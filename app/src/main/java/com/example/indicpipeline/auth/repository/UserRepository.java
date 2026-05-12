package com.example.indicpipeline.auth.repository;

import androidx.annotation.NonNull;

import com.example.indicpipeline.models.User;
import com.example.indicpipeline.utils.AuthErrorMapper;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class UserRepository {
    private final FirebaseFirestore firestore;

    public UserRepository() {
        this(FirebaseFirestore.getInstance());
    }

    public void getUserDocument(String uid, AuthRepository.AuthResultCallback<User> callback) {
        firestore.collection("users")
                .document(uid)
                .get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            DocumentSnapshot snapshot = task.getResult();
                            if (snapshot != null && snapshot.exists()) {
                                User user = snapshot.toObject(User.class);
                                if (user != null) {
                                    if (user.getUid() == null) user.setUid(uid);
                                    callback.onSuccess(user);
                                    return;
                                }
                            }
                            callback.onError("User profile not found.");
                            return;
                        }
                        Exception exception = task.getException();
                        callback.onError(AuthErrorMapper.map(exception != null ? exception : new Exception("Failed to load profile.")));
                    }
                });
    }

    public UserRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public void createUserDocument(FirebaseUser firebaseUser, String name, AuthRepository.AuthResultCallback<User> callback) {
        if (firebaseUser == null) {
            callback.onError("Authenticated user not found.");
            return;
        }

        Timestamp createdAt = Timestamp.now();
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("uid", firebaseUser.getUid());
        userMap.put("name", name);
        userMap.put("email", firebaseUser.getEmail());
        userMap.put("createdAt", createdAt);

        firestore.collection("users")
                .document(firebaseUser.getUid())
                .set(userMap)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            callback.onSuccess(new User(firebaseUser.getUid(), name, firebaseUser.getEmail(), createdAt));
                            return;
                        }
                        Exception exception = task.getException();
                        callback.onError(AuthErrorMapper.map(exception != null ? exception : new Exception("Failed to save profile.")));
                    }
                });
    }
}

