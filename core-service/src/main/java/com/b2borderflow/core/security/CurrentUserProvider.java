package com.b2borderflow.core.security;

import com.b2borderflow.core.error.ApiException;
import com.b2borderflow.core.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Доступ к {@link CurrentUser} текущего запроса для сервисов и контроллеров. */
@Component
public class CurrentUserProvider {

    public CurrentUser current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Нет контекста запроса");
        }
        HttpServletRequest request = servletAttributes.getRequest();
        Object value = request.getAttribute(CurrentUser.REQUEST_ATTRIBUTE);
        if (value instanceof CurrentUser currentUser) {
            return currentUser;
        }
        throw new ApiException(ErrorCode.UNAUTHORIZED, "Требуется аутентификация");
    }
}
