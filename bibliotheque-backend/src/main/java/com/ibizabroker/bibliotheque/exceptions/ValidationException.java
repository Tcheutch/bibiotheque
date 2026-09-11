package com.ibizabroker.bibliotheque.exceptions;

import java.util.Map;

// Validation dépendant du rôle de l'appelant (ex. adherentId obligatoire
// seulement pour un Bibliothécaire), donc non exprimable par @NotNull sur
// le DTO. Même forme de réponse (400 + détail par champ) que la validation
// Bean Validation existante, via ReservationExceptionHandler.
public class ValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Map<String, String> errors;

    public ValidationException(String message, Map<String, String> errors) {
        super(message);
        this.errors = errors;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
