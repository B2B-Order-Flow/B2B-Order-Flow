package com.b2borderflow.core.auth;

import com.b2borderflow.core.api.AuthApi;
import com.b2borderflow.core.api.model.LoginRequest;
import com.b2borderflow.core.api.model.LoginResponse;
import com.b2borderflow.core.api.model.RegisterUserRequest;
import com.b2borderflow.core.api.model.UserView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Вход, регистрация пользователя и текущий профиль. */
@RestController
public class AuthController implements AuthApi {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public ResponseEntity<UserView> registerUser(RegisterUserRequest registerUserRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(registerUserRequest));
    }

    @Override
    public ResponseEntity<LoginResponse> login(LoginRequest loginRequest) {
        return ResponseEntity.ok(authService.login(loginRequest));
    }

    @Override
    public ResponseEntity<UserView> getCurrentUser() {
        return ResponseEntity.ok(authService.me());
    }
}
