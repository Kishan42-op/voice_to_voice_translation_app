package com.example.indicpipeline.contacts.model;

import androidx.annotation.Keep;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.IgnoreExtraProperties;

@Keep
@IgnoreExtraProperties
public class FriendRequest {
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_REJECTED = "rejected";

    private String requestId;
    private String senderUid;
    private String receiverUid;
    private String status;
    private Timestamp createdAt;

    public FriendRequest() {
    }

    public FriendRequest(String requestId, String senderUid, String receiverUid, String status, Timestamp createdAt) {
        this.requestId = requestId;
        this.senderUid = senderUid;
        this.receiverUid = receiverUid;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getSenderUid() {
        return senderUid;
    }

    public void setSenderUid(String senderUid) {
        this.senderUid = senderUid;
    }

    public String getReceiverUid() {
        return receiverUid;
    }

    public void setReceiverUid(String receiverUid) {
        this.receiverUid = receiverUid;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}

