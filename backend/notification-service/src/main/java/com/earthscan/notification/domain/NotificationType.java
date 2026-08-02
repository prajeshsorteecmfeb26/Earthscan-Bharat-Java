package com.earthscan.notification.domain;

/** Notification categories, one per event the service reacts to. */
public enum NotificationType {

    WELCOME,
    ROLE_CHANGED,
    ACCOUNT_CLOSED,
    NEW_LISTING_MATCH,
    LISTING_PUBLISHED,
    QUESTION_ANSWERED,
    NEW_QUESTION_IN_CATEGORY
}
