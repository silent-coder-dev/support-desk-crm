package com.support.crm.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        String portal = request.getParameter("portal");

        // Admins use /admin-login and staff use /staff-login. The wrong door gets the normal "invalid" message.
        if (("admin".equals(portal) && !isAdmin) || ("staff".equals(portal) && isAdmin)) {
            request.getSession().invalidate();
            SecurityContextHolder.clearContext();
            response.sendRedirect(("admin".equals(portal) ? "/admin-login" : "/staff-login") + "?error=true");
            return;
        }

        response.sendRedirect(isAdmin ? "/admin/dashboard" : "/tickets");
    }
}
