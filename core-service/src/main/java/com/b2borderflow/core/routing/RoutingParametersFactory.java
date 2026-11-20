package com.b2borderflow.core.routing;

import com.b2borderflow.core.request.Request;
import com.b2borderflow.core.request.ServiceOffer;
import com.b2borderflow.core.request.TechResult;
import org.springframework.stereotype.Component;

/**
 * Собирает параметры маршрутизации из заявки и выбранного предложения
 * (docs/system/routing-rules.md, раздел 3). Отсутствующее значение остаётся
 * {@code null} — правило, которому оно нужно, просто не сработает.
 */
@Component
public class RoutingParametersFactory {

    public RoutingParameters from(Request request, ServiceOffer offer) {
        RoutingParameters.Builder builder = RoutingParameters.builder()
                .govCustomer(request.getGovCustomer())
                .debtAmount(request.getDebtAmount())
                .segment(request.getSegment())
                .capex(request.getCapex())
                .techResult(techResult(request.getTechResult()));
        if (offer != null) {
            builder.contractType(offer.getContractType())
                    .tariffType(offer.getTariffType())
                    .discountPercent(offer.getDiscountPercent())
                    .speedMbps(offer.getSpeedMbps());
        }
        return builder.build();
    }

    private static TechResult techResult(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TechResult.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
