package com.sep.parking.user;

import com.sep.parking.common.security.CurrentUser;
import com.sep.parking.user.AuthDtos.AuthResponse;
import com.sep.parking.user.AuthDtos.LoginRequest;
import com.sep.parking.user.AuthDtos.PreferencesRequest;
import com.sep.parking.user.AuthDtos.RegisterRequest;
import com.sep.parking.user.AuthDtos.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return auth.me(CurrentUser.id(jwt));
    }

    /** Vreme obavestenja pre isteka parkinga (koristi modul obavestenja). */
    @PutMapping("/me/preferences")
    public UserResponse preferences(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PreferencesRequest request) {
        return auth.updatePreferences(CurrentUser.id(jwt), request.notifyBeforeMinutes());
    }
}
