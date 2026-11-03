package com.sep.psp.common.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.sep.psp.common.exception.ApiException;

/**
 * Pristup podacima tekuceg zahteva: IP adresa i prijavljeni korisnik iz JWT-a.
 */
public final class RequestContext {

    private RequestContext() {
    }

    public static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRemoteAddr();
        }
        return null;
    }

    public static Jwt jwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Niste prijavljeni");
    }

    public static UUID userId() {
        return UUID.fromString(jwt().getSubject());
    }

    public static String userEmail() {
        return jwt().getClaimAsString("email");
    }

    /** Merchant prijavljenog MERCHANT_ADMIN korisnika - uvek iz tokena, nikad iz zahteva (PCI DSS 7). */
    public static UUID merchantId() {
        String value = jwt().getClaimAsString("merchant");
        if (value == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Korisnik nije vezan za klijenta");
        }
        return UUID.fromString(value);
    }
}
