package com.b2borderflow.core.company;

import com.b2borderflow.core.api.model.CompanyBrief;
import com.b2borderflow.core.api.model.CompanyView;
import com.b2borderflow.core.api.support.ApiEnums;
import org.springframework.stereotype.Component;

/** Представление компании в ответах API. */
@Component
public class CompanyMapper {

    public CompanyView toView(Company company) {
        return new CompanyView()
                .id(company.getId())
                .name(company.getName())
                .inn(company.getInn())
                .kpp(company.getKpp())
                .ogrn(company.getOgrn())
                .legalAddress(company.getLegalAddress())
                .status(ApiEnums.companyStatus(company.getStatus()))
                .rejectReason(company.getRejectReason())
                .confirmedAt(company.getConfirmedAt())
                .createdAt(company.getCreatedAt());
    }

    public CompanyBrief toBrief(Company company) {
        return new CompanyBrief()
                .id(company.getId())
                .name(company.getName())
                .status(ApiEnums.companyStatus(company.getStatus()));
    }
}
