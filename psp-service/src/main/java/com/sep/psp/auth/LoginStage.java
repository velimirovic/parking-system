package com.sep.psp.auth;

/**
 * Koraci prijave: lozinka -> (promena lozinke pri prvoj prijavi) -> (uparivanje MFA) -> TOTP kod -> portal.
 */
public enum LoginStage {
    PASSWORD_CHANGE,
    MFA_SETUP,
    MFA_VERIFY,
    DONE
}
