package com.b2borderflow.core.api.support;

import com.b2borderflow.core.api.model.CompanyStatus;
import com.b2borderflow.core.api.model.ContractType;
import com.b2borderflow.core.api.model.OfferStatus;
import com.b2borderflow.core.api.model.Role;
import com.b2borderflow.core.api.model.TariffType;

/**
 * Перевод доменных перечислений в модели, сгенерированные из OpenAPI-контракта.
 * Единственное место связи домена и контракта — проще заметить расхождение.
 */
public final class ApiEnums {

    private ApiEnums() {
    }

    public static Role role(com.b2borderflow.core.domain.Role value) {
        return value == null ? null : Role.valueOf(value.name());
    }

    public static CompanyStatus companyStatus(com.b2borderflow.core.company.CompanyStatus value) {
        return value == null ? null : CompanyStatus.valueOf(value.name());
    }

    public static com.b2borderflow.core.api.model.RequestStatus requestStatus(
            com.b2borderflow.core.request.RequestStatus value) {
        return value == null
                ? null
                : com.b2borderflow.core.api.model.RequestStatus.valueOf(value.name());
    }

    public static OfferStatus offerStatus(com.b2borderflow.core.request.OfferStatus value) {
        return value == null ? null : OfferStatus.valueOf(value.name());
    }

    public static TariffType tariffType(com.b2borderflow.core.request.TariffType value) {
        return value == null ? null : TariffType.valueOf(value.name());
    }

    public static ContractType contractType(com.b2borderflow.core.request.ContractType value) {
        return value == null ? null : ContractType.valueOf(value.name());
    }
}
