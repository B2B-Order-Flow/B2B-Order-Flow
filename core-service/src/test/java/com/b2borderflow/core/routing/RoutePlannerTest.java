package com.b2borderflow.core.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b2borderflow.core.request.ContractType;
import com.b2borderflow.core.request.Segment;
import com.b2borderflow.core.request.TariffType;
import com.b2borderflow.core.request.TechResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Реестр правил R-01…R-08 и проверка его на сценариях
 * (docs/system/routing-rules.md, разделы 5, 8 и 9).
 */
class RoutePlannerTest {

    private static final RoutingThresholds DEFAULTS = RoutingThresholds.defaults();

    // --- R-01…R-08: по одному положительному и отрицательному кейсу ---

    @Test
    void r01RequiresLegalApprovalForClientContractForm() {
        RoutePlan plan = finalPlan(request().contractType(ContractType.CLIENT_FORM).build());

        assertThat(plan.rulesFor(ApprovalType.LEGAL)).containsExactly(RoutingRule.R01);
        assertThat(plan.requiredByRules()).containsExactly("R-01");
    }

    @Test
    void r01DoesNotRequireLegalApprovalForOfferOrStandardContract() {
        for (ContractType contractType : List.of(ContractType.OFFER, ContractType.STANDARD)) {
            assertThat(finalPlan(request().contractType(contractType).build()).requires(ApprovalType.LEGAL))
                    .as("тип договора %s", contractType)
                    .isFalse();
        }
    }

    @Test
    void r02RequiresLegalApprovalForGovCustomer() {
        RoutePlan plan = finalPlan(request().govCustomer(true).build());

        assertThat(plan.rulesFor(ApprovalType.LEGAL)).containsExactly(RoutingRule.R02);
    }

    @Test
    void r02DoesNotRequireLegalApprovalForCommercialCustomer() {
        assertThat(finalPlan(request().govCustomer(false).build()).requires(ApprovalType.LEGAL)).isFalse();
    }

    @Test
    void r03RequiresFinanceApprovalForIndividualTariffOrDiscount() {
        assertThat(finalPlan(request().tariffType(TariffType.INDIVIDUAL).build()).requiredByRules())
                .containsExactly("R-03");
        assertThat(finalPlan(request().discountPercent(new BigDecimal("15.00")).build()).requiredByRules())
                .containsExactly("R-03");
    }

    @Test
    void r03DoesNotRequireFinanceApprovalForStandardTariffWithoutDiscount() {
        assertThat(finalPlan(request()
                .tariffType(TariffType.STANDARD)
                .discountPercent(BigDecimal.ZERO)
                .build())
                .requires(ApprovalType.FINANCE)).isFalse();
    }

    @Test
    void r04RequiresFinanceApprovalWhenDebtExceedsThreshold() {
        RoutePlan plan = finalPlan(request().debtAmount(new BigDecimal("45000.00")).build());

        assertThat(plan.rulesFor(ApprovalType.FINANCE)).containsExactly(RoutingRule.R04);
    }

    @Test
    void r04DoesNotRequireFinanceApprovalWhenDebtIsZero() {
        assertThat(finalPlan(request().debtAmount(BigDecimal.ZERO).build()).requires(ApprovalType.FINANCE))
                .isFalse();
    }

    @Test
    void r05RequiresFinanceApprovalForConstructionTechResult() {
        RoutePlan plan = finalPlan(request().techResult(TechResult.WITH_CONSTRUCTION).build());

        assertThat(plan.rulesFor(ApprovalType.FINANCE)).containsExactly(RoutingRule.R05);
    }

    @Test
    void r05DoesNotRequireFinanceApprovalWhenConstructionIsNotNeeded() {
        assertThat(finalPlan(request().techResult(TechResult.AVAILABLE).build()).requires(ApprovalType.FINANCE))
                .isFalse();
    }

    @Test
    void r06RequiresProcurementApprovalForConstructionTechResult() {
        RoutePlan plan = finalPlan(request().techResult(TechResult.WITH_CONSTRUCTION).build());

        assertThat(plan.rulesFor(ApprovalType.PROCUREMENT)).containsExactly(RoutingRule.R06);
        assertThat(plan.construction()).isTrue();
    }

    @Test
    void r06DoesNotRequireProcurementApprovalWhenTechResultIsNone() {
        assertThat(finalPlan(request().techResult(TechResult.NONE).build()).requires(ApprovalType.PROCUREMENT))
                .isFalse();
    }

    @Test
    void r07RequiresManagementApprovalForStrategicSegment() {
        RoutePlan plan = finalPlan(request().segment(Segment.STRATEGIC).build());

        assertThat(plan.rulesFor(ApprovalType.MANAGEMENT)).containsExactly(RoutingRule.R07);
    }

    @Test
    void r07DoesNotRequireManagementApprovalForSmbSegment() {
        assertThat(finalPlan(request().segment(Segment.SMB).build()).requires(ApprovalType.MANAGEMENT))
                .isFalse();
    }

    @Test
    void r08RequiresManagementApprovalAboveCapexThreshold() {
        RoutePlan plan = finalPlan(request().capex(new BigDecimal("800000.00")).build());

        assertThat(plan.rulesFor(ApprovalType.MANAGEMENT)).containsExactly(RoutingRule.R08);
    }

    @Test
    void r08DoesNotRequireManagementApprovalAtCapexThreshold() {
        assertThat(finalPlan(request().capex(new BigDecimal("500000.00")).build())
                .requires(ApprovalType.MANAGEMENT))
                .as("порог строгий: CAPEX ровно 500 000 не добавляет управленческое согласование")
                .isFalse();
    }

    // --- фазы и сценарии ---

    @Test
    void preliminaryRouteSkipsFinalRules() {
        RoutePlan plan = RoutePlanner.buildRoute(
                sc03().build(), DEFAULTS, RoutingPhase.PRELIMINARY);

        assertThat(plan.requiredByRules()).containsExactly("R-01", "R-03", "R-07");
    }

    @Test
    void sc01HasNoApprovalsAndIsStandard() {
        RoutePlan plan = finalPlan(sc01().build());

        assertThat(plan.requiresApprovals()).isFalse();
        assertThat(plan.requestClass()).isEqualTo(RequestClass.STANDARD);
        assertThat(plan.construction()).isFalse();
    }

    @Test
    void sc02AddsLegalAndFinanceWave() {
        RoutePlan plan = finalPlan(sc02().build());

        assertThat(plan.requiredByRules()).containsExactly("R-01", "R-03");
        assertThat(plan.wave1()).containsExactly(ApprovalType.LEGAL, ApprovalType.FINANCE);
        assertThat(plan.wave2()).isEmpty();
        assertThat(plan.requestClass()).isEqualTo(RequestClass.EXTENDED);
    }

    @Test
    void sc02AfterReworkDropsFinanceWhenDiscountIsRemoved() {
        RoutePlan plan = finalPlan(sc02()
                .discountPercent(BigDecimal.ZERO)
                .build());

        assertThat(plan.requiredByRules()).containsExactly("R-01");
        assertThat(plan.requires(ApprovalType.FINANCE)).isFalse();
    }

    @Test
    void sc03HasAllFourApprovalsAndConstruction() {
        RoutePlan plan = finalPlan(sc03().build());

        assertThat(plan.requiredByRules())
                .containsExactly("R-01", "R-03", "R-05", "R-06", "R-07", "R-08");
        assertThat(plan.wave1())
                .containsExactly(ApprovalType.LEGAL, ApprovalType.FINANCE, ApprovalType.PROCUREMENT);
        assertThat(plan.wave2()).containsExactly(ApprovalType.MANAGEMENT);
        assertThat(plan.construction()).isTrue();
        assertThat(plan.requestClass()).isEqualTo(RequestClass.LARGE);
    }

    @Test
    void ec03DebtAddsFinanceApproval() {
        RoutePlan plan = finalPlan(sc01().debtAmount(new BigDecimal("45000.00")).build());

        assertThat(plan.requiredByRules()).containsExactly("R-04");
        assertThat(plan.requestClass()).isEqualTo(RequestClass.EXTENDED);
    }

    @Test
    void ec04ConstructionAddsFinanceAndProcurementWithoutManagement() {
        RoutePlan plan = finalPlan(sc01()
                .techResult(TechResult.WITH_CONSTRUCTION)
                .capex(new BigDecimal("300000.00"))
                .build());

        assertThat(plan.requiredByRules()).containsExactly("R-05", "R-06");
        assertThat(plan.requires(ApprovalType.MANAGEMENT)).isFalse();
        assertThat(plan.requestClass()).isEqualTo(RequestClass.LARGE);
    }

    @Test
    void ec04LargeCapexAddsManagementApproval() {
        RoutePlan plan = finalPlan(sc01()
                .techResult(TechResult.WITH_CONSTRUCTION)
                .capex(new BigDecimal("800000.00"))
                .build());

        assertThat(plan.requiredByRules()).containsExactly("R-05", "R-06", "R-08");
        assertThat(plan.requires(ApprovalType.MANAGEMENT)).isTrue();
    }

    // --- пороги из routing_config ---

    @Test
    void thresholdsComeFromRoutingConfigValues() {
        Map<String, Object> values = Map.of(
                RoutingThresholds.KEY_DEBT_THRESHOLD_RUB, 100000,
                RoutingThresholds.KEY_CAPEX_MGMT_THRESHOLD_RUB, 1000000,
                RoutingThresholds.KEY_STRATEGIC_SEGMENTS, List.of("LARGE"),
                RoutingThresholds.KEY_STANDARD_SPEED_MAX_MBPS, 100);

        RoutingThresholds thresholds = RoutingThresholds.fromValues(values);

        assertThat(thresholds.debtThresholdRub()).isEqualByComparingTo("100000");
        assertThat(thresholds.capexMgmtThresholdRub()).isEqualByComparingTo("1000000");
        assertThat(thresholds.strategicSegments()).containsExactly(Segment.LARGE);
        assertThat(finalPlan(sc01().debtAmount(new BigDecimal("50000.00")).build(), thresholds)
                .requires(ApprovalType.FINANCE)).isFalse();
        assertThat(finalPlan(sc01().segment(Segment.LARGE).build(), thresholds)
                .requires(ApprovalType.MANAGEMENT)).isTrue();
    }

    @Test
    void missingThresholdsUseDocumentedDefaults() {
        RoutingThresholds thresholds = RoutingThresholds.fromValues(Map.of());

        assertThat(thresholds.standardSpeedMaxMbps())
                .isEqualTo(RoutingThresholds.DEFAULT_STANDARD_SPEED_MAX_MBPS);
        assertThat(thresholds.debtThresholdRub())
                .isEqualByComparingTo(RoutingThresholds.DEFAULT_DEBT_THRESHOLD_RUB);
        assertThat(thresholds.capexMgmtThresholdRub())
                .isEqualByComparingTo(RoutingThresholds.DEFAULT_CAPEX_MGMT_THRESHOLD_RUB);
        assertThat(thresholds.strategicSegments())
                .containsExactly(RoutingThresholds.DEFAULT_STRATEGIC_SEGMENTS.toArray(Segment[]::new));
    }

    @Test
    void brokenStrategicSegmentsValueIsNotSilentlyReplaced() {
        Map<String, Object> values = Map.of(
                RoutingThresholds.KEY_STRATEGIC_SEGMENTS, "STRATEGIC");

        assertThatThrownBy(() -> RoutingThresholds.fromValues(values))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strategic_segments");
    }

    // --- helpers ---

    private static RoutingParameters.Builder request() {
        return RoutingParameters.builder();
    }

    private static RoutingParameters.Builder sc01() {
        return request()
                .contractType(ContractType.OFFER)
                .tariffType(TariffType.STANDARD)
                .discountPercent(BigDecimal.ZERO)
                .speedMbps(100)
                .segment(Segment.SMB)
                .techResult(TechResult.AVAILABLE)
                .debtAmount(BigDecimal.ZERO)
                .capex(BigDecimal.ZERO);
    }

    private static RoutingParameters.Builder sc02() {
        return sc01()
                .speedMbps(500)
                .tariffType(TariffType.STANDARD)
                .discountPercent(new BigDecimal("15.00"))
                .contractType(ContractType.CLIENT_FORM)
                .segment(Segment.LARGE);
    }

    private static RoutingParameters.Builder sc03() {
        return sc01()
                .speedMbps(1000)
                .tariffType(TariffType.INDIVIDUAL)
                .contractType(ContractType.CLIENT_FORM)
                .segment(Segment.STRATEGIC)
                .techResult(TechResult.WITH_CONSTRUCTION)
                .capex(new BigDecimal("1200000.00"));
    }

    private static RoutePlan finalPlan(RoutingParameters parameters) {
        return finalPlan(parameters, DEFAULTS);
    }

    private static RoutePlan finalPlan(RoutingParameters parameters, RoutingThresholds thresholds) {
        return RoutePlanner.buildRoute(parameters, thresholds, RoutingPhase.FINAL);
    }
}
