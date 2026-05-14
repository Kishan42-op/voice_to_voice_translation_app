package com.example.indicpipeline.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class AvatarUtils {
    private AvatarUtils() {
    }

    @NonNull
    public static String initials(@Nullable String name, @Nullable String username) {
        String source = firstNonEmpty(name, username, "U");
        String[] parts = source.trim().split("\\s+");

        if (parts.length == 0) {
            return "U";
        }

        if (parts.length == 1) {
            String token = parts[0];
            if (token.isEmpty()) {
                return "U";
            }
            return token.substring(0, Math.min(2, token.length())).toUpperCase();
        }

        String first = parts[0].isEmpty() ? "U" : parts[0].substring(0, 1);
        String second = parts[1].isEmpty() ? "" : parts[1].substring(0, 1);
        return (first + second).toUpperCase();
    }

    @NonNull
    private static String firstNonEmpty(@Nullable String... values) {
        if (values == null) {
            return "U";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "U";
    }
}

