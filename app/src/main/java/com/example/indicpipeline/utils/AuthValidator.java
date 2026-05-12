package com.example.indicpipeline.utils;

import android.text.TextUtils;

import java.util.regex.Pattern;

public final class AuthValidator {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE
    );

    private AuthValidator() {
    }

    public static String validateName(String name) {
        if (TextUtils.isEmpty(name == null ? null : name.trim())) {
            return "Name is required.";
        }
        if (name.trim().length() < 2) {
            return "Name must be at least 2 characters.";
        }
        return null;
    }

    public static String validateEmail(String email) {
        if (TextUtils.isEmpty(email == null ? null : email.trim())) {
            return "Email is required.";
        }
        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            return "Enter a valid email address.";
        }
        return null;
    }

    public static String validatePassword(String password) {
        if (TextUtils.isEmpty(password)) {
            return "Password is required.";
        }
        if (password.length() < 6) {
            return "Password must be at least 6 characters.";
        }
        return null;
    }

    public static String validateConfirmPassword(String password, String confirmPassword) {
        if (TextUtils.isEmpty(confirmPassword)) {
            return "Please confirm your password.";
        }
        if (!password.equals(confirmPassword)) {
            return "Passwords do not match.";
        }
        return null;
    }
}

