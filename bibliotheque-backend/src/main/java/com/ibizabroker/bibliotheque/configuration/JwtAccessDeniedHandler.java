package com.ibizabroker.bibliotheque.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

// Distinct de JwtAuthenticationEntryPoint (401 : pas de token / token
// invalide) : ce handler ne s'exécute que pour un appelant authentifié mais
// non autorisé (403). Les deux chemins ne se croisent jamais.
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    @Autowired
    private SecurityAuditLogger securityAuditLogger;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        securityAuditLogger.refus(request, HttpServletResponse.SC_FORBIDDEN, accessDeniedException.getMessage());
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
    }

}
