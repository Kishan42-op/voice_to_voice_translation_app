package com.example.indicpipeline.contacts.repository;

import com.example.indicpipeline.contacts.model.FriendContact;
import com.example.indicpipeline.contacts.model.FriendRequest;
import com.example.indicpipeline.contacts.model.RelationshipStatus;
import com.example.indicpipeline.utils.AuthErrorMapper;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContactsRepository {
    public interface FriendRequestsCallback {
        void onSuccess(List<FriendRequest> requests);

        void onError(String message);
    }

    public interface FriendsCallback {
        void onSuccess(List<FriendContact> friends);

        void onError(String message);
    }

    public interface RequestCallback {
        void onSuccess(FriendRequest request);

        void onError(String message);
    }

    public interface VoidCallback {
        void onSuccess();

        void onError(String message);
    }

    public interface BooleanCallback {
        void onSuccess(boolean value);

        void onError(String message);
    }

    public interface StatusCallback {
        void onSuccess(RelationshipStatus status);

        void onError(String message);
    }

    private static final String REQUESTS_COLLECTION = "friend_requests";
    private static final String FRIENDS_COLLECTION = "friends";
    private static final String USER_FRIENDS_SUBCOLLECTION = "user_friends";

    private final FirebaseFirestore firestore;

    public ContactsRepository() {
        this(FirebaseFirestore.getInstance());
    }

    public ContactsRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public ListenerRegistration getFriendRequests(String receiverUid, FriendRequestsCallback callback) {
        if (receiverUid == null || receiverUid.trim().isEmpty()) {
            callback.onError("User session not found.");
            return null;
        }

        return firestore.collection(REQUESTS_COLLECTION)
                .whereEqualTo("receiverUid", receiverUid)
                .whereEqualTo("status", FriendRequest.STATUS_PENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(AuthErrorMapper.map(error));
                        return;
                    }
                    callback.onSuccess(mapRequests(snapshot));
                });
    }

    public ListenerRegistration getOutgoingFriendRequests(String senderUid, FriendRequestsCallback callback) {
        if (senderUid == null || senderUid.trim().isEmpty()) {
            callback.onError("User session not found.");
            return null;
        }

        return firestore.collection(REQUESTS_COLLECTION)
                .whereEqualTo("senderUid", senderUid)
                .whereEqualTo("status", FriendRequest.STATUS_PENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(AuthErrorMapper.map(error));
                        return;
                    }
                    callback.onSuccess(mapRequests(snapshot));
                });
    }

    public ListenerRegistration getFriends(String uid, FriendsCallback callback) {
        if (uid == null || uid.trim().isEmpty()) {
            callback.onError("User session not found.");
            return null;
        }

        return firestore.collection(FRIENDS_COLLECTION)
                .document(uid)
                .collection(USER_FRIENDS_SUBCOLLECTION)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(AuthErrorMapper.map(error));
                        return;
                    }
                    callback.onSuccess(mapFriends(snapshot));
                });
    }

    public void sendFriendRequest(String senderUid, String receiverUid, RequestCallback callback) {
        if (!isValidParticipantPair(senderUid, receiverUid)) {
            callback.onError("You cannot send a friend request to yourself.");
            return;
        }

        String requestId = buildRequestId(senderUid, receiverUid);
        DocumentReference requestRef = firestore.collection(REQUESTS_COLLECTION).document(requestId);
        Timestamp now = Timestamp.now();

        Map<String, Object> payload = new HashMap<>();
        payload.put("requestId", requestId);
        payload.put("senderUid", senderUid);
        payload.put("receiverUid", receiverUid);
        payload.put("status", FriendRequest.STATUS_PENDING);
        payload.put("createdAt", now);

        requestRef.set(payload)
                .addOnSuccessListener(unused -> callback.onSuccess(new FriendRequest(requestId, senderUid, receiverUid, FriendRequest.STATUS_PENDING, now)))
                .addOnFailureListener(exception -> callback.onError(resolveFriendlyMessage(exception)));
    }

    public void acceptFriendRequest(String requestId, String currentUserUid, VoidCallback callback) {
        if (requestId == null || requestId.trim().isEmpty()) {
            callback.onError("Request not found.");
            return;
        }
        if (currentUserUid == null || currentUserUid.trim().isEmpty()) {
            callback.onError("User session not found.");
            return;
        }

        DocumentReference requestRef = firestore.collection(REQUESTS_COLLECTION).document(requestId);

        firestore.runTransaction((Transaction transaction) -> {
            DocumentSnapshot requestSnapshot = transaction.get(requestRef);
            if (!requestSnapshot.exists()) {
                throw new IllegalStateException("Friend request not found.");
            }

            String receiverUid = requestSnapshot.getString("receiverUid");
            if (receiverUid == null || !receiverUid.equals(currentUserUid)) {
                throw new IllegalStateException("You cannot accept this request.");
            }

            String status = requestSnapshot.getString("status");
            if (!FriendRequest.STATUS_PENDING.equals(status)) {
                throw new IllegalStateException("This request is no longer pending.");
            }

            String senderUid = requestSnapshot.getString("senderUid");
            if (senderUid == null || senderUid.trim().isEmpty()) {
                throw new IllegalStateException("Invalid request data.");
            }

            Timestamp now = Timestamp.now();
            DocumentReference senderFriendRef = firestore.collection(FRIENDS_COLLECTION).document(senderUid).collection(USER_FRIENDS_SUBCOLLECTION).document(receiverUid);
            DocumentReference receiverFriendRef = firestore.collection(FRIENDS_COLLECTION).document(receiverUid).collection(USER_FRIENDS_SUBCOLLECTION).document(senderUid);

            Map<String, Object> senderFriendPayload = new HashMap<>();
            senderFriendPayload.put("friendUid", receiverUid);
            senderFriendPayload.put("createdAt", now);
            transaction.set(senderFriendRef, senderFriendPayload);

            Map<String, Object> receiverFriendPayload = new HashMap<>();
            receiverFriendPayload.put("friendUid", senderUid);
            receiverFriendPayload.put("createdAt", now);
            transaction.set(receiverFriendRef, receiverFriendPayload);

            transaction.update(requestRef, "status", FriendRequest.STATUS_ACCEPTED);
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(exception -> callback.onError(resolveFriendlyMessage(exception)));
    }

    public void rejectFriendRequest(String requestId, String currentUserUid, VoidCallback callback) {
        if (requestId == null || requestId.trim().isEmpty()) {
            callback.onError("Request not found.");
            return;
        }
        if (currentUserUid == null || currentUserUid.trim().isEmpty()) {
            callback.onError("User session not found.");
            return;
        }

        DocumentReference requestRef = firestore.collection(REQUESTS_COLLECTION).document(requestId);

        firestore.runTransaction((Transaction transaction) -> {
            DocumentSnapshot requestSnapshot = transaction.get(requestRef);
            if (!requestSnapshot.exists()) {
                throw new IllegalStateException("Friend request not found.");
            }

            String receiverUid = requestSnapshot.getString("receiverUid");
            if (receiverUid == null || !receiverUid.equals(currentUserUid)) {
                throw new IllegalStateException("You cannot reject this request.");
            }

            String status = requestSnapshot.getString("status");
            if (!FriendRequest.STATUS_PENDING.equals(status)) {
                throw new IllegalStateException("This request is no longer pending.");
            }

            transaction.update(requestRef, "status", FriendRequest.STATUS_REJECTED);
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(exception -> callback.onError(resolveFriendlyMessage(exception)));
    }

    public void checkFriendshipStatus(String currentUserUid, String otherUserUid, StatusCallback callback) {
        if (!isValidParticipantPair(currentUserUid, otherUserUid)) {
            callback.onSuccess(RelationshipStatus.NOT_CONNECTED);
            return;
        }

        DocumentReference currentFriendRef = firestore.collection(FRIENDS_COLLECTION).document(currentUserUid).collection(USER_FRIENDS_SUBCOLLECTION).document(otherUserUid);
        DocumentReference otherFriendRef = firestore.collection(FRIENDS_COLLECTION).document(otherUserUid).collection(USER_FRIENDS_SUBCOLLECTION).document(currentUserUid);
        DocumentReference outgoingRequestRef = firestore.collection(REQUESTS_COLLECTION).document(buildRequestId(currentUserUid, otherUserUid));
        DocumentReference incomingRequestRef = firestore.collection(REQUESTS_COLLECTION).document(buildRequestId(otherUserUid, currentUserUid));

        currentFriendRef.get()
                .addOnSuccessListener(friendSnapshot -> {
                    if (friendSnapshot.exists()) {
                        callback.onSuccess(RelationshipStatus.FRIENDS);
                        return;
                    }

                    otherFriendRef.get()
                            .addOnSuccessListener(reverseFriendSnapshot -> {
                                if (reverseFriendSnapshot.exists()) {
                                    callback.onSuccess(RelationshipStatus.FRIENDS);
                                    return;
                                }

                                outgoingRequestRef.get()
                                        .addOnSuccessListener(outgoingSnapshot -> {
                                            if (outgoingSnapshot.exists() && FriendRequest.STATUS_PENDING.equals(outgoingSnapshot.getString("status"))) {
                                                callback.onSuccess(RelationshipStatus.REQUEST_SENT);
                                                return;
                                            }

                                            incomingRequestRef.get()
                                                    .addOnSuccessListener(incomingSnapshot -> {
                                                        if (incomingSnapshot.exists() && FriendRequest.STATUS_PENDING.equals(incomingSnapshot.getString("status"))) {
                                                            callback.onSuccess(RelationshipStatus.REQUEST_RECEIVED);
                                                            return;
                                                        }
                                                        callback.onSuccess(RelationshipStatus.NOT_CONNECTED);
                                                    })
                                                    .addOnFailureListener(exception -> callback.onError(resolveFriendlyMessage(exception)));
                                        })
                                        .addOnFailureListener(exception -> callback.onError(resolveFriendlyMessage(exception)));
                            })
                            .addOnFailureListener(exception -> callback.onError(resolveFriendlyMessage(exception)));
                })
                .addOnFailureListener(exception -> callback.onError(resolveFriendlyMessage(exception)));
    }

    public void isAlreadyFriend(String currentUserUid, String otherUserUid, BooleanCallback callback) {
        if (!isValidParticipantPair(currentUserUid, otherUserUid)) {
            callback.onSuccess(false);
            return;
        }

        DocumentReference currentFriendRef = firestore.collection(FRIENDS_COLLECTION).document(currentUserUid).collection(USER_FRIENDS_SUBCOLLECTION).document(otherUserUid);
        DocumentReference otherFriendRef = firestore.collection(FRIENDS_COLLECTION).document(otherUserUid).collection(USER_FRIENDS_SUBCOLLECTION).document(currentUserUid);

        currentFriendRef.get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        callback.onSuccess(true);
                        return;
                    }
                    otherFriendRef.get()
                            .addOnSuccessListener(reverseSnapshot -> callback.onSuccess(reverseSnapshot.exists()))
                            .addOnFailureListener(exception -> callback.onError(resolveFriendlyMessage(exception)));
                })
                .addOnFailureListener(exception -> callback.onError(resolveFriendlyMessage(exception)));
    }

    public void isRequestPending(String currentUserUid, String otherUserUid, BooleanCallback callback) {
        if (!isValidParticipantPair(currentUserUid, otherUserUid)) {
            callback.onSuccess(false);
            return;
        }

        DocumentReference outgoingRequestRef = firestore.collection(REQUESTS_COLLECTION).document(buildRequestId(currentUserUid, otherUserUid));
        DocumentReference incomingRequestRef = firestore.collection(REQUESTS_COLLECTION).document(buildRequestId(otherUserUid, currentUserUid));

        outgoingRequestRef.get()
                .addOnSuccessListener(outgoingSnapshot -> {
                    if (outgoingSnapshot.exists() && FriendRequest.STATUS_PENDING.equals(outgoingSnapshot.getString("status"))) {
                        callback.onSuccess(true);
                        return;
                    }
                    incomingRequestRef.get()
                            .addOnSuccessListener(incomingSnapshot -> callback.onSuccess(incomingSnapshot.exists() && FriendRequest.STATUS_PENDING.equals(incomingSnapshot.getString("status"))))
                            .addOnFailureListener(exception -> callback.onError(resolveFriendlyMessage(exception)));
                })
                .addOnFailureListener(exception -> callback.onError(resolveFriendlyMessage(exception)));
    }

    public void hasFriendOrPendingRelationship(String currentUserUid, String otherUserUid, StatusCallback callback) {
        checkFriendshipStatus(currentUserUid, otherUserUid, callback);
    }

    public String buildRequestId(String senderUid, String receiverUid) {
        if (senderUid == null || receiverUid == null) {
            return "";
        }
        return senderUid + "_" + receiverUid;
    }

    private boolean isValidParticipantPair(String senderUid, String receiverUid) {
        return senderUid != null && !senderUid.trim().isEmpty()
                && receiverUid != null && !receiverUid.trim().isEmpty()
                && !senderUid.equals(receiverUid);
    }

    private List<FriendRequest> mapRequests(QuerySnapshot snapshot) {
        List<FriendRequest> requests = new ArrayList<>();
        if (snapshot == null) {
            return requests;
        }

        for (QueryDocumentSnapshot document : snapshot) {
            FriendRequest request = document.toObject(FriendRequest.class);
            if (request == null) {
                continue;
            }
            if (request.getRequestId() == null) {
                request.setRequestId(document.getId());
            }
            requests.add(request);
        }

        requests.sort((first, second) -> compareTimestamps(second != null ? second.getCreatedAt() : null, first != null ? first.getCreatedAt() : null));
        return requests;
    }

    private List<FriendContact> mapFriends(QuerySnapshot snapshot) {
        List<FriendContact> friends = new ArrayList<>();
        if (snapshot == null) {
            return friends;
        }

        for (QueryDocumentSnapshot document : snapshot) {
            FriendContact friend = document.toObject(FriendContact.class);
            if (friend.getFriendUid() == null) {
                friend.setFriendUid(document.getId());
            }
            friends.add(friend);
        }

        friends.sort((first, second) -> compareTimestamps(second != null ? second.getCreatedAt() : null, first != null ? first.getCreatedAt() : null));
        return friends;
    }

    private int compareTimestamps(Timestamp first, Timestamp second) {
        if (first == null && second == null) {
            return 0;
        }
        if (first == null) {
            return 1;
        }
        if (second == null) {
            return -1;
        }
        return first.compareTo(second);
    }

    private String resolveFriendlyMessage(Exception exception) {
        if (exception == null) {
            return "Something went wrong. Please try again.";
        }
        String message = AuthErrorMapper.map(exception);
        if (message == null || message.trim().isEmpty()) {
            return exception.getMessage() == null ? "Something went wrong. Please try again." : exception.getMessage();
        }
        return message;
    }
}




