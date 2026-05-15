package com.example.indicpipeline.contacts.model;

import androidx.annotation.Keep;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.IgnoreExtraProperties;

@Keep
@IgnoreExtraProperties
public class FriendContact {
    private String friendUid;
    private Timestamp createdAt;

    public FriendContact() {
    }

    public FriendContact(String friendUid, Timestamp createdAt) {
        this.friendUid = friendUid;
        this.createdAt = createdAt;
    }

    public String getFriendUid() {
        return friendUid;
    }

    public void setFriendUid(String friendUid) {
        this.friendUid = friendUid;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}

