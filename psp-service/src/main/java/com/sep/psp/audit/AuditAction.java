package com.sep.psp.audit;

/**
 * Dogadjaji koji se beleze u audit log (PCI DSS 10.2.1).
 */
public final class AuditAction {

    public static final String LOGIN = "LOGIN";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String MFA_FAILED = "MFA_FAILED";
    public static final String MFA_ENROLLED = "MFA_ENROLLED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String LOGOUT = "LOGOUT";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String USER_BOOTSTRAP = "USER_BOOTSTRAP";
    public static final String MERCHANT_CREATE = "MERCHANT_CREATE";
    public static final String MERCHANT_STATUS_CHANGE = "MERCHANT_STATUS_CHANGE";
    public static final String MERCHANT_ACCESS_RESET = "MERCHANT_ACCESS_RESET";
    public static final String CONFIG_CHANGE = "CONFIG_CHANGE";
    public static final String KEY_ROTATION = "KEY_ROTATION";
    public static final String PLUGIN_REGISTER = "PLUGIN_REGISTER";
    public static final String PLUGIN_UPDATE = "PLUGIN_UPDATE";
    public static final String DATA_EXPORT = "DATA_EXPORT";
    public static final String AUDIT_VERIFY = "AUDIT_VERIFY";
    public static final String MERCHANT_API_AUTH_FAILED = "MERCHANT_API_AUTH_FAILED";
    public static final String TX_INIT = "TX_INIT";
    public static final String TX_STATUS_CHANGE = "TX_STATUS_CHANGE";

    private AuditAction() {
    }
}
