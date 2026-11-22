package com.b2borderflow.core.user;

import com.b2borderflow.core.api.UsersApi;
import com.b2borderflow.core.api.model.OperatorUserPage;
import com.b2borderflow.core.api.model.Role;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** HTTP-слой списка сотрудников оператора для назначения (api-contract.md, 6.1). */
@RestController
public class UsersController implements UsersApi {

    private final OperatorUserService userService;

    public UsersController(OperatorUserService userService) {
        this.userService = userService;
    }

    @Override
    public ResponseEntity<OperatorUserPage> listOperatorUsers(Role role, Integer page, Integer size, String sort) {
        return ResponseEntity.ok(userService.list(role, page, size, sort));
    }
}
