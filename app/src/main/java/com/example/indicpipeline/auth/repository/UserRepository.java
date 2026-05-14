package com.example.indicpipeline.auth.repository;

import androidx.annotation.NonNull;

import com.example.indicpipeline.models.User;
import com.example.indicpipeline.utils.AuthErrorMapper;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserRepository {
    public interface UserSearchCallback {
        void onSuccess(List<User> users);

        void onError(String message);
    }

    private final FirebaseFirestore firestore;
    private final AuthRepository authRepository;

    public UserRepository() {
        this(FirebaseFirestore.getInstance(), new AuthRepository());
    }

    public UserRepository(FirebaseFirestore firestore) {
        this(firestore, new AuthRepository());
    }

    public UserRepository(FirebaseFirestore firestore, AuthRepository authRepository) {
        this.firestore = firestore;
        this.authRepository = authRepository;
    }

    public FirebaseUser getCurrentUser() {
        return authRepository.getCurrentUser();
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

    public void saveUserProfile(FirebaseUser firebaseUser, String name, String username, AuthRepository.AuthResultCallback<User> callback) {
        if (firebaseUser == null) {
            callback.onError("Authenticated user not found.");
            return;
        }

        String normalizedUsername = normalizeUsername(username);
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedUsername == null) {
            callback.onError("Use 3-20 lowercase letters, numbers, or underscores.");
            return;
        }

        DocumentReference userRef = firestore.collection("users").document(firebaseUser.getUid());
        DocumentReference usernameRef = firestore.collection("usernames").document(normalizedUsername);

        firestore.runTransaction((Transaction transaction) -> {
            DocumentSnapshot usernameSnapshot = transaction.get(usernameRef);
            if (usernameSnapshot.exists()) {
                String existingUid = usernameSnapshot.getString("uid");
                if (existingUid != null && !firebaseUser.getUid().equals(existingUid)) {
                    throw new IllegalStateException("Username is already taken.");
                }
            }

            DocumentSnapshot userSnapshot = transaction.get(userRef);
            String previousUsername = null;
            Timestamp createdAt = Timestamp.now();
            if (userSnapshot.exists()) {
                previousUsername = userSnapshot.getString("username");
                Timestamp existingCreatedAt = userSnapshot.getTimestamp("createdAt");
                if (existingCreatedAt != null) {
                    createdAt = existingCreatedAt;
                }
            }

            Map<String, Object> userMap = new HashMap<>();
            userMap.put("uid", firebaseUser.getUid());
            userMap.put("name", normalizedName);
            userMap.put("email", firebaseUser.getEmail());
            userMap.put("username", normalizedUsername);
            userMap.put("createdAt", createdAt);
            transaction.set(userRef, userMap);

            Map<String, Object> usernameMap = new HashMap<>();
            usernameMap.put("uid", firebaseUser.getUid());
            usernameMap.put("username", normalizedUsername);
            usernameMap.put("createdAt", createdAt);
            transaction.set(usernameRef, usernameMap);

            if (previousUsername != null) {
                String previousNormalized = normalizeUsername(previousUsername);
                if (previousNormalized != null && !previousNormalized.equals(normalizedUsername)) {
                    transaction.delete(firestore.collection("usernames").document(previousNormalized));
                }
            }

            return new User(firebaseUser.getUid(), normalizedName, firebaseUser.getEmail(), normalizedUsername, createdAt);
        }).addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(exception -> callback.onError(AuthErrorMapper.map(exception)));
    }

    public void checkUsernameAvailability(String username, AuthRepository.AuthResultCallback<Boolean> callback) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null) {
            callback.onError("Use 3-20 lowercase letters, numbers, or underscores.");
            return;
        }

        FirebaseUser currentUser = getCurrentUser();
        String currentUid = currentUser != null ? currentUser.getUid() : null;

        firestore.collection("usernames")
                .document(normalizedUsername)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Exception exception = task.getException();
                        callback.onError(AuthErrorMapper.map(exception != null ? exception : new Exception("Failed to check username.")));
                        return;
                    }

                    DocumentSnapshot snapshot = task.getResult();
                    if (snapshot == null || !snapshot.exists()) {
                        callback.onSuccess(Boolean.TRUE);
                        return;
                    }

                    String uid = snapshot.getString("uid");
                    callback.onSuccess(currentUid != null && currentUid.equals(uid));
                });
    }

    public ListenerRegistration searchUsers(String query, String currentUserId, UserSearchCallback callback) {
        String normalizedQuery = normalizeSearchQuery(query);

        Query firestoreQuery = firestore.collection("users")
                .orderBy("username")
                .limit(50);

        if (!normalizedQuery.isEmpty()) {
            firestoreQuery = firestore.collection("users")
                    .orderBy("username")
                    .startAt(normalizedQuery)
                    .endAt(normalizedQuery + "\uf8ff")
                    .limit(50);
        }

        return firestoreQuery.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                callback.onError(AuthErrorMapper.map(error));
                return;
            }

            List<User> users = new ArrayList<>();
            if (snapshot != null) {
                for (QueryDocumentSnapshot document : snapshot) {
                    User user = document.toObject(User.class);
                    if (user.getUid() == null) {
                        user.setUid(document.getId());
                    }
                    if (currentUserId != null && currentUserId.equals(user.getUid())) {
                        continue;
                    }
                    users.add(user);
                }
            }
            callback.onSuccess(users);
        });
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }

        String normalized = username.trim().toLowerCase();
        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1);
        }
        if (normalized.length() < 3 || normalized.length() > 20) {
            return null;
        }
        if (!normalized.matches("^[a-z0-9_]{3,20}$")) {
            return null;
        }
        return normalized;
    }

    private String normalizeSearchQuery(String query) {
        if (query == null) {
            return "";
        }

        String normalized = query.trim().toLowerCase();
        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}

