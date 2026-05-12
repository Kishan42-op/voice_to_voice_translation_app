package com.example.indicpipeline.utils;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.firestore.FirebaseFirestoreException;

public final class AuthErrorMapper {
    private AuthErrorMapper() {
    }

    public static String map(@NonNull Exception exception) {
        if (exception instanceof FirebaseAuthWeakPasswordException) {
            return "Password must be at least 6 characters.";
        }
        if (exception instanceof FirebaseAuthUserCollisionException) {
            return "An account already exists for this email.";
        }
        if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            return "Invalid email or password.";
        }
        if (exception instanceof FirebaseAuthInvalidUserException) {
            return "No account found for this email.";
        }
        if (exception instanceof FirebaseFirestoreException) {
            return "Firestore error: " + exception.getMessage();
        }
        if (exception instanceof FirebaseAuthException) {
            return exception.getMessage() != null ? exception.getMessage() : "Authentication failed.";
        }
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty() ? "Something went wrong. Please try again." : message;
    }
}

