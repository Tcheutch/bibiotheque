package com.ibizabroker.bibliotheque.service;

import com.ibizabroker.bibliotheque.dao.UsersRepository;
import com.ibizabroker.bibliotheque.entity.Users;
import com.ibizabroker.bibliotheque.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

// Point unique d'extraction de l'identité de l'appelant, déduite du
// SecurityContext (jamais du corps de la requête) — RS-04. Réutilisé par
// tout service ayant besoin de savoir qui appelle et avec quel rôle.
@Service
public class CurrentUserService {

    private static final String ROLE_ADMIN_AUTHORITY = "ROLE_Admin";

    @Autowired
    private UsersRepository usersRepository;

    public Users getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return usersRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException(
                        "Utilisateur authentifié introuvable : " + username
                ));
    }

    public boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_ADMIN_AUTHORITY::equals);
    }
}
