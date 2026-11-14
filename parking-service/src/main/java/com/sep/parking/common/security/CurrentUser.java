package com.sep.parking.common.security;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** ID prijavljenog korisnika je "sub" claim JWT-a. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
