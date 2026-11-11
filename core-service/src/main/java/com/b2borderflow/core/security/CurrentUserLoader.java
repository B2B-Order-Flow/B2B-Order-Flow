package com.b2borderflow.core.security;

import com.b2borderflow.core.company.Company;
import com.b2borderflow.core.company.CompanyMembership;
import com.b2borderflow.core.company.CompanyMembershipRepository;
import com.b2borderflow.core.company.CompanyRepository;
import com.b2borderflow.core.company.MembershipRole;
import com.b2borderflow.core.company.MembershipRoleRepository;
import com.b2borderflow.core.company.OperatorRole;
import com.b2borderflow.core.company.OperatorRoleRepository;
import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.error.ApiException;
import com.b2borderflow.core.error.ErrorCode;
import com.b2borderflow.core.user.AppUser;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Собирает {@link CurrentUser} по актуальным данным БД: операторские роли,
 * членство в компаниях и активные компании. Источник истины — БД, а не JWT.
 */
@Component
public class CurrentUserLoader {

    private final CompanyMembershipRepository membershipRepository;
    private final MembershipRoleRepository membershipRoleRepository;
    private final OperatorRoleRepository operatorRoleRepository;
    private final CompanyRepository companyRepository;

    public CurrentUserLoader(CompanyMembershipRepository membershipRepository,
                             MembershipRoleRepository membershipRoleRepository,
                             OperatorRoleRepository operatorRoleRepository,
                             CompanyRepository companyRepository) {
        this.membershipRepository = membershipRepository;
        this.membershipRoleRepository = membershipRoleRepository;
        this.operatorRoleRepository = operatorRoleRepository;
        this.companyRepository = companyRepository;
    }

    @Transactional(readOnly = true)
    public CurrentUser load(AppUser user, UUID tokenCompanyId, Role requestedRole) {
        List<CompanyMembership> memberships =
                membershipRepository.findByUserIdAndRemovedAtIsNullOrderByAddedAtAsc(user.getId());

        List<UUID> membershipIds = memberships.stream().map(CompanyMembership::getId).toList();
        Map<UUID, Set<Role>> rolesByMembership = new HashMap<>();
        if (!membershipIds.isEmpty()) {
            for (MembershipRole membershipRole : membershipRoleRepository.findByIdMembershipIdIn(membershipIds)) {
                rolesByMembership
                        .computeIfAbsent(membershipRole.getId().getMembershipId(), key -> EnumSet.noneOf(Role.class))
                        .add(membershipRole.getId().getRole());
            }
        }

        Set<UUID> companyIds = memberships.stream().map(CompanyMembership::getCompanyId).collect(Collectors.toSet());
        Map<UUID, Company> companies = companyIds.isEmpty()
                ? Map.of()
                : companyRepository.findAllById(companyIds).stream()
                        .collect(Collectors.toMap(Company::getId, company -> company));

        List<CurrentUser.Membership> userMemberships = new ArrayList<>();
        Set<Role> allRoles = new LinkedHashSet<>();
        for (CompanyMembership membership : memberships) {
            Company company = companies.get(membership.getCompanyId());
            if (company == null) {
                continue;
            }
            Set<Role> membershipRoles = rolesByMembership.getOrDefault(membership.getId(), Set.of());
            userMemberships.add(new CurrentUser.Membership(company.getId(), company.getStatus(), membershipRoles));
            allRoles.addAll(membershipRoles);
        }

        for (OperatorRole operatorRole : operatorRoleRepository.findByIdUserId(user.getId())) {
            allRoles.add(operatorRole.getId().getRole());
        }

        if (requestedRole != null && !allRoles.contains(requestedRole)) {
            throw ApiException.accessDenied("У пользователя нет роли " + requestedRole);
        }

        return new CurrentUser(
                user.getId(),
                user.getLogin(),
                user.getFullName(),
                user.getEmail(),
                allRoles,
                userMemberships,
                tokenCompanyId,
                requestedRole);
    }

    /** Проверка, что пользователь остался активным и имеет роль, не доверяя токену. */
    public void assertActive(AppUser user) {
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Учётная запись деактивирована");
        }
    }
}
