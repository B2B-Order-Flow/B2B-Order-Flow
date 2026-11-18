package com.b2borderflow.core.sla;

import static org.assertj.core.api.Assertions.assertThat;

import com.b2borderflow.core.request.RequestStageType;
import com.b2borderflow.core.request.Segment;
import com.b2borderflow.core.routing.RoutingConfig;
import com.b2borderflow.core.routing.RoutingConfigRepository;
import com.b2borderflow.core.routing.RoutingThresholds;
import com.b2borderflow.core.support.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Сущности недели 2 читают справочники из миграции Дениса
 * (docs/system/sla.md, раздел 4; docs/system/routing-rules.md, раздел 4).
 * Тест страхует расхождение сущностей и схемы, которое иначе проявилось бы
 * только на стенде: {@code ddl-auto=validate} проверяет типы, а этот тест — данные.
 */
class SlaReferenceDataIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SlaNormRepository slaNormRepository;

    @Autowired
    private PauseReasonRepository pauseReasonRepository;

    @Autowired
    private WorkCalendarRepository workCalendarRepository;

    @Autowired
    private RoutingConfigRepository routingConfigRepository;

    @Test
    void slaNormsAreReadForBothProfiles() {
        assertThat(slaNormRepository.findByProfileAndKindAndStageType(
                SlaProfile.PROD, SlaTimerKind.STAGE_SLA, RequestStageType.TECH_CHECK))
                .hasValueSatisfying(norm -> {
                    assertThat(norm.getNormMinutes()).isEqualTo(1440);
                    assertThat(norm.getWarnBeforeMinutes()).isEqualTo(360);
                    assertThat(norm.getCalendarKind()).isEqualTo(SlaCalendarKind.WORK);
                });

        assertThat(slaNormRepository.findByProfileAndKindAndStageTypeIsNull(
                SlaProfile.DEMO, SlaTimerKind.TECH_RESULT))
                .hasValueSatisfying(norm -> {
                    assertThat(norm.getNormMinutes()).isEqualTo(12);
                    assertThat(norm.getCalendarKind()).isEqualTo(SlaCalendarKind.CALENDAR);
                });
    }

    @Test
    void pauseReasonsAreReadWithRoles() {
        assertThat(pauseReasonRepository.findByActiveTrueOrderByCodeAsc())
                .extracting(PauseReason::getCode)
                .containsExactlyInAnyOrder(PauseReasonCode.values());

        assertThat(pauseReasonRepository.findById(PauseReasonCode.NO_ACCESS))
                .hasValueSatisfying(reason -> {
                    assertThat(reason.isAutomatic()).isFalse();
                    assertThat(reason.getSetterRoles()).containsExactly("COORDINATOR");
                    assertThat(reason.getRemoverRoles()).containsExactly("COORDINATOR");
                });
    }

    @Test
    void workCalendarContainsHolidayAndWeekend() {
        assertThat(workCalendarRepository.findByCalendarDate(LocalDate.of(2026, 11, 4)))
                .hasValueSatisfying(day -> {
                    assertThat(day.isWorking()).isFalse();
                    assertThat(day.getTimezone()).isEqualTo("Europe/Moscow");
                });
        assertThat(workCalendarRepository.findByCalendarDate(LocalDate.of(2026, 11, 7)))
                .hasValueSatisfying(day -> assertThat(day.isWorking()).isFalse());
        assertThat(workCalendarRepository.findByCalendarDate(LocalDate.of(2026, 11, 9)))
                .hasValueSatisfying(day -> assertThat(day.isWorking()).isTrue());
    }

    @Test
    void routingThresholdsAreReadFromRoutingConfigTable() {
        Map<String, Object> values = routingConfigRepository.findAll().stream()
                .collect(Collectors.toMap(RoutingConfig::getConfigKey, RoutingConfig::getConfigValue));

        RoutingThresholds thresholds = RoutingThresholds.fromValues(values);

        assertThat(thresholds.standardSpeedMaxMbps()).isEqualTo(100);
        assertThat(thresholds.debtThresholdRub()).isEqualByComparingTo("0");
        assertThat(thresholds.capexMgmtThresholdRub()).isEqualByComparingTo("500000");
        assertThat(thresholds.strategicSegments()).containsExactly(Segment.STRATEGIC);
    }
}
