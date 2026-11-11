package com.b2borderflow.core.security;

import com.b2borderflow.core.company.CompanyStatus;
import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.error.ApiException;
import com.b2borderflow.core.error.ErrorCode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Пользователь, его роли и активный контекст запроса. Решение о правах принимается
 * по актуальным данным БД: роль могли отозвать после выдачи токена (EC-17).
 */
public final class CurrentUser {

    public static final String REQUEST_ATTRIBUTE = CurrentUser.class.getName();

    private final UUID id;
    private final String login;
    private final String fullName;
    private final String email;
    private final Set<Role> roles;
    private final List<Membership> memberships;
    private final UUID tokenCompanyId;
    private final Role activeRole;

    public CurrentUser(UUID id, String login, String fullName, String email,
                       Set<Role> roles, List<Membership> memberships,
                       UUID tokenCompanyId, Role activeRole) {
        this.id = id;
        this.login = login;
        this.fullName = fullName;
        this.email = email;
        this.roles = Set.copyOf(roles);
        this.memberships = memberships.stream()
                .sorted(Comparator.comparing(Membership::companyId))
                .toList();
        this.tokenCompanyId = tokenCompanyId;
        this.activeRole = activeRole;
    }

    public UUID id() {
        return id;
    }

    public String login() {
        return login;
    }

    public String fullName() {
        return fullName;
    }

    public String email() {
        return email;
    }

    public Set<Role> roles() {
        return roles;
    }

    public List<Membership> memberships() {
        return memberships;
    }

    public Role activeRole() {
        return activeRole;
    }

    /** Роль, от имени которой выполняется запрос. */
    public Role effectiveActiveRole() {
        if (activeRole != null) {
            return activeRole;
        }
        if (roles.size() == 1) {
            return roles.iterator().next();
        }
        throw new ApiException(ErrorCode.ACTIVE_ROLE_REQUIRED,
                "У пользователя несколько ролей: передайте X-Active-Role");
    }

    /**
     * Проверяет, что активная роль входит в допустимый набор, и возвращает её.
     *
     * @throws ApiException 403, если активная роль не разрешает действие
     */
    public Role requireAnyRole(Role... required) {
        Role effective = effectiveActiveRole();
        boolean allowed = Arrays.stream(required).anyMatch(role -> role == effective);
        if (!allowed) {
            throw ApiException.accessDenied(
                    "Активная роль " + effective + " не позволяет выполнить действие");
        }
        return effective;
    }

    public boolean hasActiveRole(Role role) {
        return activeRole == role;
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean hasAnyRole(Role... candidates) {
        return Arrays.stream(candidates).anyMatch(roles::contains);
    }

    /**
     * Компания активной клиентской роли. У пользователя MVP одна активная компания;
     * если их несколько, берём компанию из токена либо явно просим уточнить.
     */
    public UUID activeCompanyId() {
        Role effective = effectiveActiveRole();
        if (effective.isOperatorRole()) {
            return memberships.stream().map(Membership::companyId).findFirst().orElse(null);
        }

        List<UUID> companies = memberships.stream()
                .filter(membership -> membership.roles().contains(effective))
                .map(Membership::companyId)
                .toList();

        if (companies.size() == 1) {
            return companies.get(0);
        }
        if (tokenCompanyId != null && companies.contains(tokenCompanyId)) {
            return tokenCompanyId;
        }
        if (companies.isEmpty()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "У пользователя нет компании с активной ролью " + effective);
        }
        throw new ApiException(ErrorCode.ACTIVE_COMPANY_REQUIRED,
                "Пользователь состоит в нескольких компаниях: укажите компанию в токене");
    }

    public UUID requireActiveCompanyId() {
        UUID companyId = activeCompanyId();
        if (companyId == null) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Пользователь не привязан к компании");
        }
        return companyId;
    }

    public CompanyStatus activeCompanyStatus() {
        UUID companyId = requireActiveCompanyId();
        return memberships.stream()
                .filter(membership -> membership.companyId().equals(companyId))
                .map(Membership::status)
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_DENIED, "Компания пользователя не найдена"));
    }

    public boolean belongsToCompany(UUID companyId) {
        return memberships.stream().anyMatch(membership -> membership.companyId().equals(companyId));
    }

    public Optional<Membership> membership(UUID companyId) {
        return memberships.stream().filter(membership -> membership.companyId().equals(companyId)).findFirst();
    }

    /** Связь пользователя с компанией в объёме, нужном для политики доступа. */
    public record Membership(UUID companyId, CompanyStatus status, Set<Role> roles) {
        public Membership {
            roles = roles == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(roles));
        }
    }
}
