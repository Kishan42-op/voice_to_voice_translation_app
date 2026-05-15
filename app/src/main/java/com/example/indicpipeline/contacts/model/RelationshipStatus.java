package com.example.indicpipeline.contacts.model;

public enum RelationshipStatus {
    SELF,
    NOT_CONNECTED,
    REQUEST_SENT,
    REQUEST_RECEIVED,
    FRIENDS;

    public boolean isActionable() {
        return this == NOT_CONNECTED || this == REQUEST_RECEIVED;
    }

    public static RelationshipStatus fromValue(String value) {
        if (value == null) {
            return NOT_CONNECTED;
        }
        for (RelationshipStatus status : values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        return NOT_CONNECTED;
    }
}

