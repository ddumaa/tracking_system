package com.project.tracking_system.utils;

import com.project.tracking_system.service.user.LoginAttemptService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;

/**
 * @author Dmitriy Anisimov
 * @date 03.02.2025
 */
@Component
public class IPBlockingFilter extends GenericFilterBean {

    private final LoginAttemptService loginAttemptService;

    @Autowired
    public IPBlockingFilter(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String ip = request.getRemoteAddr();

        if (loginAttemptService.isIPBlocked(ip)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Ваш IP временно заблокирован. Попробуйте позже.");
            return;
        }
        chain.doFilter(req, res);
    }
}
