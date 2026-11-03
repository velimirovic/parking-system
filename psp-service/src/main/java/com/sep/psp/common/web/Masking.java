package com.sep.psp.common.web;

/**
 * Maskiranje tajni i konfiguracionih vrednosti za prikaz (PCI DSS 3.4).
 */
public final class Masking {

    private Masking() {
    }

    public static String maskSecret(String prefix, String last4) {
        return prefix + "••••••••••••" + last4;
    }

    public static String maskValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() <= 4) {
            return "••••";
        }
        return "••••" + value.substring(value.length() - 4);
    }
}
