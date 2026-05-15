package com.example.indicpipeline.contacts.model;

import com.example.indicpipeline.models.User;

public class IncomingFriendRequestItem {
    private final FriendRequest request;
    private final User sender;

    public IncomingFriendRequestItem(FriendRequest request, User sender) {
        this.request = request;
        this.sender = sender;
    }

    public FriendRequest getRequest() {
        return request;
    }

    public User getSender() {
        return sender;
    }
}

