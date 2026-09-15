package com.ibizabroker.bibliotheque.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

// Journal des accès refusés : une ligne WARN par 401/403, au format clé=valeur
// ("grep ACCES_REFUSE"). Appelé par les trois points qui produisent un refus :
// JwtAuthenticationEntryPoint (401), JwtAccessDeniedHandler (403 hors module
// Réservation) et ReservationExceptionHandler (403 du module Réservation).
// Ne journalise jamais le token.
@Component
public class SecurityAuditLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityAuditLogger.class);

    public void refus(HttpServletRequest request, int status, String motif) {
        LOGGER.warn("ACCES_REFUSE status={} methode={} chemin={} ip={} utilisateur={} motif=\"{}\"",
                status,
                request.getMethod(),
                nettoyer(request.getRequestURI()),
                request.getRemoteAddr(),
                nettoyer(utilisateurCourant()),
                nettoyer(motif));
    }

    private String utilisateurCourant() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return "anonyme";
        }
        return authentication.getName();
    }

    // Retours à la ligne neutralisés : un chemin ou un message forgé ne doit pas
    // pouvoir injecter de fausses lignes dans le journal.
    private static String nettoyer(String valeur) {
        return valeur == null ? "" : valeur.replaceAll("[\\r\\n\\t]", "_");
    }
}
