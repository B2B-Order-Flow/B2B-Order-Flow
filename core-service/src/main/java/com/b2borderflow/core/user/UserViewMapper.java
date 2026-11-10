package com.b2borderflow.core.user;

import com.b2borderflow.core.api.model.UserView;
import com.b2borderflow.core.api.support.ApiEnums;
import com.b2borderflow.core.company.Company;
import com.b2borderflow.core.company.CompanyMapper;
import com.b2borderflow.core.company.CompanyRepository;
import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.security.CurrentUser;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Представление пользователя для входа и {@code GET /me}. */
@Component
public class UserViewMapper {

    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;

    public UserViewMapper(CompanyRepository companyRepository, CompanyMapper companyMapper) {
        this.companyRepository = companyRepository;
        this.companyMapper = companyMapper;
    }

    public UserView toView(AppUser user, CurrentUser currentUser) {
        Set<Role> companyRoles = EnumSet.noneOf(Role.class);
        Set<Role> operatorRoles = EnumSet.noneOf(Role.class);
        for (Role role : currentUser.roles()) {
            if (role.isClientRole()) {
                companyRoles.add(role);
            } else {
                operatorRoles.add(role);
            }
        }

        List<UUID> companyIds = currentUser.memberships().stream()
                .map(CurrentUser.Membership::companyId)
                .toList();
        Map<UUID, Company> companies = companyIds.isEmpty()
                ? Map.of()
                : companyRepository.findAllById(companyIds).stream()
                        .collect(Collectors.toMap(Company::getId, company -> company));

        List<com.b2borderflow.core.api.model.CompanyBrief> companyBriefs = new ArrayList<>();
        for (CurrentUser.Membership membership : currentUser.memberships()) {
            Company company = companies.get(membership.companyId());
            if (company != null) {
                companyBriefs.add(companyMapper.toBrief(company));
            }
        }

        return new UserView()
                .id(user.getId())
                .login(user.getLogin())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .companyRoles(companyRoles.stream().map(ApiEnums::role).toList())
                .operatorRoles(operatorRoles.stream().map(ApiEnums::role).toList())
                .companies(companyBriefs);
    }
}
