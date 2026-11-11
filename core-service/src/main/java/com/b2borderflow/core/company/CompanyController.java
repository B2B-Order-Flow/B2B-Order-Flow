package com.b2borderflow.core.company;

import com.b2borderflow.core.api.CompaniesApi;
import com.b2borderflow.core.api.model.CompanyView;
import com.b2borderflow.core.api.model.RegisterCompanyRequest;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Регистрация и карточка компании. */
@RestController
public class CompanyController implements CompaniesApi {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @Override
    public ResponseEntity<CompanyView> registerCompany(RegisterCompanyRequest registerCompanyRequest,
                                                       UUID idempotencyKey) {
        CompanyView view = companyService.register(registerCompanyRequest, idempotencyKey);
        return ResponseEntity
                .created(URI.create("/internal/v1/companies/" + view.getId()))
                .body(view);
    }

    @Override
    public ResponseEntity<CompanyView> getCompany(UUID companyId) {
        return ResponseEntity.ok(companyService.getCompany(companyId));
    }
}
