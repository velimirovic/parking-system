package com.sep.psp.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.sep.psp.common.config.SecurityProperties;
import com.sep.psp.common.exception.ApiException;

/**
 * Izdavanje JWT tokena za portal. Postoje dve vrste:
 * <ul>
 *   <li>access token - pristup API-ju, istice posle 15 min (stateless, radi na svim instancama PSP-a)</li>
 *   <li>pre-auth token - vazi samo za sledeci korak prijave (MFA ili promena lozinke), potpisan drugim kljucem,
 *       pa ga resource server ne prihvata kao access token</li>
 * </ul>
 */
@Service
public class TokenService {

    public static final String ISSUER = "sep-psp-portal";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_MERCHANT = "merchant";
    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_STAGE = "stage";

    private final SecurityProperties properties;
    private final JwtEncoder accessEncoder;
    private final JwtEncoder preAuthEncoder;
    private final JwtDecoder preAuthDecoder;

    public TokenService(SecurityProperties properties) {
        this.properties = properties;
        this.accessEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(accessKey(properties)));
        SecretKey preAuthKey = derive(properties.jwtSecret(), "pre-auth");
        this.preAuthEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(preAuthKey));
        this.preAuthDecoder = NimbusJwtDecoder.withSecretKey(preAuthKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    public static SecretKey accessKey(SecurityProperties properties) {
        byte[] secret = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("app.security.jwt-secret mora imati najmanje 32 bajta");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    public IssuedToken issueAccessToken(PspUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.accessTokenMinutes(), ChronoUnit.MINUTES);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(CLAIM_TYPE, "access")
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim(CLAIM_ROLE, user.getRole().name());
        if (user.getMerchantId() != null) {
            claims.claim(CLAIM_MERCHANT, user.getMerchantId().toString());
        }
        return new IssuedToken(encode(accessEncoder, claims.build()), expiresAt);
    }

    public String issuePreAuthToken(PspUser user, LoginStage stage) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.preAuthMinutes(), ChronoUnit.MINUTES))
                .claim(CLAIM_TYPE, "pre_auth")
                .claim(CLAIM_STAGE, stage.name())
                .build();
        return encode(preAuthEncoder, claims);
    }

    /** Vraca ID korisnika ako je pre-auth token ispravan i namenjen trazenom koraku prijave. */
    public UUID verifyPreAuthToken(String token, LoginStage expectedStage) {
        try {
            Jwt jwt = preAuthDecoder.decode(token);
            if (!"pre_auth".equals(jwt.getClaimAsString(CLAIM_TYPE))
                    || !expectedStage.name().equals(jwt.getClaimAsString(CLAIM_STAGE))) {
                throw invalidPreAuth();
            }
            return UUID.fromString(jwt.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw invalidPreAuth();
        }
    }

    private static ApiException invalidPreAuth() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_SESSION_EXPIRED",
                "Sesija prijave je istekla. Prijavite se ponovo.");
    }

    private static String encode(JwtEncoder encoder, JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static SecretKey derive(String secret, String purpose) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return new SecretKeySpec(mac.doFinal(purpose.getBytes(StandardCharsets.UTF_8)), "HmacSHA256");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
