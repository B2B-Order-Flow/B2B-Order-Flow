package com.b2borderflow.core.auth;

import com.b2borderflow.core.api.model.LoginRequest;
import com.b2borderflow.core.api.model.LoginResponse;
import com.b2borderflow.core.api.model.RegisterUserRequest;
import com.b2borderflow.core.api.model.UserView;
import com.b2borderflow.core.audit.AuditAction;
import com.b2borderflow.core.audit.AuditService;
import com.b2borderflow.core.error.ApiException;
import com.b2borderflow.core.error.ErrorCode;
import com.b2borderflow.core.error.Violation;
import com.b2borderflow.core.security.CurrentUser;
import com.b2borderflow.core.security.CurrentUserLoader;
import com.b2borderflow.core.security.CurrentUserProvider;
import com.b2borderflow.core.security.JwtService;
import com.b2borderflow.core.user.AppUser;
import com.b2borderflow.core.user.AppUserRepository;
import com.b2borderflow.core.user.UserViewMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Регистрация пользователя, вход и выдача JWT. */
@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserLoader currentUserLoader;
    private final CurrentUserProvider currentUserProvider;
    private final UserViewMapper userViewMapper;
    private final AuditService auditService;

    public AuthService(AppUserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       CurrentUserLoader currentUserLoader,
                       CurrentUserProvider currentUserProvider,
                       UserViewMapper userViewMapper,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.currentUserLoader = currentUserLoader;
        this.currentUserProvider = currentUserProvider;
        this.userViewMapper = userViewMapper;
        this.auditService = auditService;
    }

    @Transactional
    public UserView register(RegisterUserRequest request) {
        String login = request.getLogin().trim();
        if (userRepository.existsByLoginIgnoreCase(login)) {
            throw ApiException.validation(
                    "Логин уже занят",
                    List.of(new Violation("login", "ALREADY_EXISTS", "Пользователь с таким логином уже существует")));
        }

        AppUser user = new AppUser();
        user.setLogin(login);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName().trim());
        user.setEmail(request.getEmail().trim());
        user.setActive(true);
        AppUser saved = userRepository.save(user);

        auditService.recordForUser(saved, null, null, AuditAction.USER_REGISTERED,
                "USER", saved.getId(), null, null, null, null);

        CurrentUser currentUser = currentUserLoader.load(saved, null, null);
        return userViewMapper.toView(saved, currentUser);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        AppUser user = userRepository.findByLoginIgnoreCase(request.getLogin().trim())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS, "Неверный логин или пароль"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Неверный логин или пароль");
        }

        CurrentUser currentUser = currentUserLoader.load(user, null, null);
        UUID tokenCompanyId = currentUser.memberships().size() == 1
                ? currentUser.memberships().get(0).companyId()
                : null;
        JwtService.IssuedToken token = jwtService.issue(user, currentUser.roles(), tokenCompanyId);

        return new LoginResponse()
                .accessToken(token.accessToken())
                .expiresIn(token.expiresIn())
                .user(userViewMapper.toView(user, currentUser));
    }

    @Transactional(readOnly = true)
    public UserView me() {
        CurrentUser currentUser = currentUserProvider.current();
        AppUser user = userRepository.findById(currentUser.id())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Пользователь не найден"));
        return userViewMapper.toView(user, currentUser);
    }
}
