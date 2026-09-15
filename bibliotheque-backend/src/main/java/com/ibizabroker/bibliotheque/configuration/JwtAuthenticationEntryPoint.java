package com.ibizabroker.bibliotheque.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizabroker.bibliotheque.dto.ApiError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;

// 401 : appelant non authentifié. JwtRequestFilter pose le motif (attribut
// ATTRIBUT_MOTIF) quand un token est présent mais inutilisable ; sans motif,
// c'est qu'aucun token Bearer n'a été envoyé. Le message part au format
// ApiError pour que le frontend l'affiche tel quel sur l'écran de connexion.
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    public static final String ATTRIBUT_MOTIF = JwtAuthenticationEntryPoint.class.getName() + ".MOTIF";

    public enum MotifNonAuthentifie {
        TOKEN_ABSENT("Authentification requise. Veuillez vous connecter."),
        TOKEN_EXPIRE("Votre session a expiré. Veuillez vous reconnecter."),
        TOKEN_INVALIDE("Token invalide. Veuillez vous reconnecter.");

        private final String message;

        MotifNonAuthentifie(String message) {
            this.message = message;
        }
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SecurityAuditLogger securityAuditLogger;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        MotifNonAuthentifie motif = (MotifNonAuthentifie) request.getAttribute(ATTRIBUT_MOTIF);
        if (motif == null) {
            motif = MotifNonAuthentifie.TOKEN_ABSENT;
        }

        securityAuditLogger.refus(request, HttpServletResponse.SC_UNAUTHORIZED, motif.name());

        ApiError corps = new ApiError(
                Instant.now(),
                HttpServletResponse.SC_UNAUTHORIZED,
                motif.message,
                request.getRequestURI(),
                new LinkedHashMap<>()
        );
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), corps);
    }

}
