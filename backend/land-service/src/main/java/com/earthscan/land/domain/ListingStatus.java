package com.earthscan.land.domain;

/** Lifecycle of a listing. Stored as a string so the database stays readable. */
public enum ListingStatus {

    /** Visible in search results. */
    ACTIVE,

    /** Retained for history but hidden from search. */
    SOLD,

    /** Saved by the owner but not yet published. */
    DRAFT,

    /** Hidden by an administrator, e.g. a suspected fraudulent listing. */
    WITHDRAWN
}
