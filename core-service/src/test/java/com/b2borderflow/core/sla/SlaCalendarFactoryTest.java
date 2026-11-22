package com.b2borderflow.core.sla;

import static org.assertj.core.api.Assertions.assertThat;

import com.b2borderflow.core.config.SlaProperties;
import com.b2borderflow.core.request.RequestStageType;
import java.lang.reflect.Proxy;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Календарь выбирается по нормативу, а не только по профилю: в PROD нормативы
 * с {@code calendar_kind = CALENDAR} (ТВ, стройка) считаются календарными минутами
 * (docs/system/sla.md, 3.1; замечание тимлида по неделе 2).
 */
class SlaCalendarFactoryTest {

    private final WorkCalendarRepository workCalendarRepository = emptyWorkCalendarRepository();

    @Test
    void prodCalendarKindCalendarUsesWallClockNotWorkCalendar() {
        SlaCalendarFactory factory = factory(SlaProfile.PROD);

        SlaCalendar calendar = factory.forNorm(norm(SlaProfile.PROD, SlaCalendarKind.CALENDAR));

        assertThat(calendar).isSameAs(WallClockSlaCalendar.INSTANCE);
    }

    @Test
    void prodCalendarKindWorkUsesProdCalendar() {
        SlaCalendarFactory factory = factory(SlaProfile.PROD);

        SlaCalendar calendar = factory.forNorm(norm(SlaProfile.PROD, SlaCalendarKind.WORK));

        assertThat(calendar).isInstanceOf(ProdSlaCalendar.class);
        assertThat(calendar.zone()).isEqualTo(ZoneId.of("Europe/Moscow"));
    }

    @Test
    void demoProfileAlwaysUsesWallClockCalendar() {
        SlaCalendarFactory factory = factory(SlaProfile.DEMO);

        assertThat(factory.forNorm(norm(SlaProfile.DEMO, SlaCalendarKind.WORK)))
                .isSameAs(DemoSlaCalendar.INSTANCE);
        assertThat(factory.forNorm(norm(SlaProfile.DEMO, SlaCalendarKind.CALENDAR)))
                .isSameAs(DemoSlaCalendar.INSTANCE);
    }

    @Test
    void businessDateUsesMoscowZoneInProd() {
        assertThat(factory(SlaProfile.PROD).businessZone()).isEqualTo(ZoneId.of("Europe/Moscow"));
    }

    private SlaCalendarFactory factory(SlaProfile profile) {
        SlaProperties properties = new SlaProperties();
        properties.setProfile(profile);
        return new SlaCalendarFactory(properties, workCalendarRepository);
    }

    private static SlaNorm norm(SlaProfile profile, SlaCalendarKind calendarKind) {
        SlaNorm norm = new SlaNorm();
        norm.setProfile(profile);
        norm.setKind(SlaTimerKind.TECH_RESULT);
        norm.setStageType(RequestStageType.TECH_CHECK);
        norm.setNormMinutes(100);
        norm.setWarnBeforeMinutes(10);
        norm.setCalendarKind(calendarKind);
        return norm;
    }

    /**
     * Тесту нужен только {@code findAll()}; полноценно реализовывать интерфейс
     * Spring Data ради одной заглушки не стоит, а Mockito в песочнице не может
     * подключить свой агент.
     */
    private static WorkCalendarRepository emptyWorkCalendarRepository() {
        return (WorkCalendarRepository) Proxy.newProxyInstance(
                WorkCalendarRepository.class.getClassLoader(),
                new Class<?>[]{WorkCalendarRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> List.<WorkCalendar>of();
                    case "toString" -> "emptyWorkCalendarRepository";
                    case "hashCode" -> 0;
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
