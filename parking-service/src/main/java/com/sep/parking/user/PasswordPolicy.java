package com.sep.parking.user;

import com.sep.parking.common.exception.ApiException;
import java.util.regex.Pattern;

/** Najmanje 12 karaktera, veliko slovo, broj i simbol (isto pravilo proverava i frontend). */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private static final Pattern UPPER = Pattern.compile("\\p{Lu}");
    private static final Pattern LOWER = Pattern.compile("\\p{Ll}");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern SYMBOL = Pattern.compile("[^\\p{L}\\d\\s]");

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH
                || !UPPER.matcher(password).find() || !LOWER.matcher(password).find()
                || !DIGIT.matcher(password).find() || !SYMBOL.matcher(password).find()) {
            throw ApiException.badRequest("WEAK_PASSWORD",
                    "Lozinka mora imati najmanje 12 karaktera, veliko i malo slovo, broj i simbol.");
        }
    }
}
