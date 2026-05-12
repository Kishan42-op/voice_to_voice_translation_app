package com.example.indicpipeline.auth.repository;

import androidx.annotation.NonNull;

import com.example.indicpipeline.utils.AuthErrorMapper;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AuthRepository {
    public interface AuthResultCallback<T> {
        void onSuccess(T result);

        void onError(String message);
    }

    private final FirebaseAuth firebaseAuth;

    public AuthRepository() {
        this(FirebaseAuth.getInstance());
    }

    public AuthRepository(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    public FirebaseUser getCurrentUser() {
        return firebaseAuth.getCurrentUser();
    }

    public boolean isLoggedIn() {
        return firebaseAuth.getCurrentUser() != null;
    }

    public void login(String email, String password, AuthResultCallback<FirebaseUser> callback) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            FirebaseUser user = firebaseAuth.getCurrentUser();
                            if (user != null) {
                                callback.onSuccess(user);
                            } else {
                                callback.onError("Login failed. Please try again.");
                            }
                            return;
                        }
                        Exception exception = task.getException();
                        callback.onError(AuthErrorMapper.map(exception != null ? exception : new Exception("Login failed.")));
                    }
                });
    }

    public void signUp(String email, String password, AuthResultCallback<FirebaseUser> callback) {
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            FirebaseUser user = firebaseAuth.getCurrentUser();
                            if (user != null) {
                                callback.onSuccess(user);
                            } else {
                                callback.onError("Signup failed. Please try again.");
                            }
                            return;
                        }
                        Exception exception = task.getException();
                        callback.onError(AuthErrorMapper.map(exception != null ? exception : new Exception("Signup failed.")));
                    }
                });
    }

    public void logout() {
        firebaseAuth.signOut();
    }
}

