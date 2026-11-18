package com.b2borderflow.core.routing;

import com.b2borderflow.core.request.ContractType;
import com.b2borderflow.core.request.Segment;
import com.b2borderflow.core.request.TariffType;
import com.b2borderflow.core.request.TechResult;
import java.math.BigDecimal;

/**
 * Параметры, из которых строится маршрут (docs/system/routing-rules.md, раздел 3).
 * Значения берутся из заявки и выбранного предложения услуги; отсутствующее значение
 * означает «ещё не известно» (например, результат ТВ до этапа проверки).
 *
 * <p>Скорость {@code speedMbps} в правилах не участвует: она описывает крупность
 * заявки, но состав согласований определяют тариф, скидка, тип договора, задолженность,
 * сегмент и результат ТВ (routing-rules.md, раздел 3).</p>
 */
public record RoutingParameters(
        ContractType contractType,
        TariffType tariffType,
        BigDecimal discountPercent,
        Integer speedMbps,
        Boolean govCustomer,
        BigDecimal debtAmount,
        Segment segment,
        TechResult techResult,
        BigDecimal capex) {

    public static Builder builder() {
        return new Builder();
    }

    /** Построитель с безопасными значениями по умолчанию, чтобы тесты читались как сценарии. */
    public static final class Builder {

        private ContractType contractType = ContractType.OFFER;
        private TariffType tariffType = TariffType.STANDARD;
        private BigDecimal discountPercent = BigDecimal.ZERO;
        private Integer speedMbps;
        private Boolean govCustomer = Boolean.FALSE;
        private BigDecimal debtAmount = BigDecimal.ZERO;
        private Segment segment;
        private TechResult techResult;
        private BigDecimal capex = BigDecimal.ZERO;

        private Builder() {
        }

        public Builder contractType(ContractType value) {
            this.contractType = value;
            return this;
        }

        public Builder tariffType(TariffType value) {
            this.tariffType = value;
            return this;
        }

        public Builder discountPercent(BigDecimal value) {
            this.discountPercent = value;
            return this;
        }

        public Builder speedMbps(Integer value) {
            this.speedMbps = value;
            return this;
        }

        public Builder govCustomer(Boolean value) {
            this.govCustomer = value;
            return this;
        }

        public Builder debtAmount(BigDecimal value) {
            this.debtAmount = value;
            return this;
        }

        public Builder segment(Segment value) {
            this.segment = value;
            return this;
        }

        public Builder techResult(TechResult value) {
            this.techResult = value;
            return this;
        }

        public Builder capex(BigDecimal value) {
            this.capex = value;
            return this;
        }

        public RoutingParameters build() {
            return new RoutingParameters(
                    contractType,
                    tariffType,
                    discountPercent,
                    speedMbps,
                    govCustomer,
                    debtAmount,
                    segment,
                    techResult,
                    capex);
        }
    }
}
