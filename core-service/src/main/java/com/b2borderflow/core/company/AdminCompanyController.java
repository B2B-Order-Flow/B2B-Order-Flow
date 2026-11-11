package com.b2borderflow.core.company;

import com.b2borderflow.core.api.AdminApi;
import com.b2borderflow.core.api.model.CompanyDecisionRequest;
import com.b2borderflow.core.api.model.CompanyPage;
import com.b2borderflow.core.api.model.CompanyView;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Административная очередь подтверждения компаний. */
@RestController
public class AdminCompanyController implements AdminApi {

    private final CompanyService companyService;

    public AdminCompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @Override
    public ResponseEntity<CompanyPage> listCompaniesForConfirmation(
            com.b2borderflow.core.api.model.CompanyStatus status, Integer page, Integer size, String sort) {
        CompanyStatus domainStatus = status == null ? null : CompanyStatus.valueOf(status.name());
        return ResponseEntity.ok(companyService.listForConfirmation(domainStatus, page, size, sort));
    }

    @Override
    public ResponseEntity<CompanyView> confirmCompany(UUID companyId, CompanyDecisionRequest request) {
        return ResponseEntity.ok(companyService.confirm(companyId, request));
    }

    @Override
    public ResponseEntity<CompanyView> rejectCompany(UUID companyId, CompanyDecisionRequest request) {
        return ResponseEntity.ok(companyService.reject(companyId, request));
    }
}
