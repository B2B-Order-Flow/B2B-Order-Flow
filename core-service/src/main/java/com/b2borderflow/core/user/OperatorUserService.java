package com.b2borderflow.core.user;

import com.b2borderflow.core.api.model.OperatorUserPage;
import com.b2borderflow.core.api.model.OperatorUserView;
import com.b2borderflow.core.api.model.PageInfo;
import com.b2borderflow.core.api.support.ApiEnums;
import com.b2borderflow.core.company.OperatorRole;
import com.b2borderflow.core.company.OperatorRoleRepository;
import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.error.ApiException;
import com.b2borderflow.core.error.Violation;
import com.b2borderflow.core.security.CurrentUserProvider;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Список сотрудников оператора для назначения менеджера и исполнителя
 * (docs/system/api-contract.md, 6.1). Доступен только координатору; возвращаются
 * активные сотрудники с запрошенной ролью.
 */
@Service
public class OperatorUserService {

    private static final Set<String> SORT_FIELDS = Set.of("login", "fullName", "createdAt");

    private final AppUserRepository userRepository;
    private final OperatorRoleRepository operatorRoleRepository;
    private final CurrentUserProvider currentUserProvider;

    public OperatorUserService(AppUserRepository userRepository,
                               OperatorRoleRepository operatorRoleRepository,
                               CurrentUserProvider currentUserProvider) {
        this.userRepository = userRepository;
        this.operatorRoleRepository = operatorRoleRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public OperatorUserPage list(com.b2borderflow.core.api.model.Role role, int page, int size, String sort) {
        currentUserProvider.current().requireAnyRole(Role.COORDINATOR);
        Role domainRole = Role.valueOf(role.name());
        Pageable pageable = pageable(page, size, sort);
        Page<AppUser> users = userRepository.findActiveByOperatorRole(domainRole, pageable);

        List<UUID> userIds = users.getContent().stream().map(AppUser::getId).toList();
        Map<UUID, List<OperatorRole>> rolesByUser = userIds.isEmpty()
                ? Map.of()
                : operatorRoleRepository.findByIdUserIdIn(userIds).stream()
                        .collect(Collectors.groupingBy(roleEntity -> roleEntity.getId().getUserId()));

        List<OperatorUserView> content = users.getContent().stream()
                .map(user -> toView(user, rolesByUser.getOrDefault(user.getId(), List.of())))
                .toList();

        PageInfo pageInfo = new PageInfo()
                .number(users.getNumber())
                .size(users.getSize())
                .totalElements(users.getTotalElements())
                .totalPages(users.getTotalPages());

        return new OperatorUserPage().content(content).page(pageInfo);
    }

    private OperatorUserView toView(AppUser user, List<OperatorRole> roles) {
        List<com.b2borderflow.core.api.model.Role> operatorRoles = roles.stream()
                .map(role -> ApiEnums.role(role.getId().getRole()))
                .toList();
        return new OperatorUserView()
                .id(user.getId())
                .login(user.getLogin())
                .fullName(user.getFullName())
                .operatorRoles(operatorRoles)
                .active(user.isActive());
    }

    private static Pageable pageable(int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw ApiException.validation("Некорректная пагинация",
                    List.of(new Violation("page", "INVALID", "page >= 0, size от 1 до 100")));
        }
        String property = "createdAt";
        Sort.Direction direction = Sort.Direction.DESC;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", 2);
            property = parts[0].trim();
            if (!SORT_FIELDS.contains(property)) {
                throw ApiException.validation("Недопустимое поле сортировки",
                        List.of(new Violation("sort", "INVALID", "Допустимые поля: " + SORT_FIELDS)));
            }
            if (parts.length == 2) {
                try {
                    direction = Sort.Direction.fromString(parts[1].trim().toLowerCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    throw ApiException.validation("Недопустимое направление сортировки",
                            List.of(new Violation("sort", "INVALID", "Допустимы: asc, desc")));
                }
            }
        }
        return PageRequest.of(page, size, Sort.by(direction, property));
    }
}
