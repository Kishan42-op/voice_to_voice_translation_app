package com.example.indicpipeline.contacts.model;

import com.example.indicpipeline.models.User;

public class UserConnectionItem {
    private final User user;
    private final RelationshipStatus relationshipStatus;
    private final String requestId;

    public UserConnectionItem(User user, RelationshipStatus relationshipStatus, String requestId) {
        this.user = user;
        this.relationshipStatus = relationshipStatus;
        this.requestId = requestId;
    }

    public User getUser() {
        return user;
    }

    public RelationshipStatus getRelationshipStatus() {
        return relationshipStatus;
    }

    public String getRequestId() {
        return requestId;
    }
}

