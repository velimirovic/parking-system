package com.sep.psp.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sep.psp.auth.AuthDtos.ChangePasswordRequest;
import com.sep.psp.auth.AuthDtos.FirstPasswordRequest;
import com.sep.psp.auth.AuthDtos.LoginRequest;
import com.sep.psp.auth.AuthDtos.LoginResponse;
import com.sep.psp.auth.AuthDtos.MfaCodeRequest;
import com.sep.psp.auth.AuthDtos.MfaSetupResponse;
import com.sep.psp.auth.AuthDtos.PreAuthRequest;
import com.sep.psp.auth.AuthDtos.TokenResponse;
import com.sep.psp.auth.AuthDtos.UserResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ---- javni koraci prijave (pre-auth token se salje u telu, ne kao Bearer) ----

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/password/first")
    public LoginResponse firstPassword(@Valid @RequestBody FirstPasswordRequest request) {
        return authService.changeFirstPassword(request);
    }

    @PostMapping("/mfa/setup")
    public MfaSetupResponse mfaSetup(@Valid @RequestBody PreAuthRequest request) {
        return authService.startMfaSetup(request);
    }

    @PostMapping("/mfa/activate")
    public LoginResponse mfaActivate(@Valid @RequestBody MfaCodeRequest request) {
        return authService.activateMfa(request);
    }

    @PostMapping("/mfa/verify")
    public LoginResponse mfaVerify(@Valid @RequestBody MfaCodeRequest request) {
        return authService.verifyMfa(request);
    }

    // ---- prijavljeni korisnik ----

    @GetMapping("/me")
    public UserResponse me() {
        return authService.me();
    }

    @PostMapping("/refresh")
    public TokenResponse refresh() {
        return authService.refresh();
    }

    @PostMapping("/password")
    public TokenResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        authService.logout();
    }
}
