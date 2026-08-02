package com.earthscan.common.messaging;

/**
 * Single source of truth for exchange, queue and routing-key names.
 *
 * <p>Publishers and consumers live in different services, so hard-coding these strings in both
 * places is the classic way an event silently stops being delivered. Keeping them here in common-lib
 * means a rename is a compile error rather than a runtime mystery.</p>
 *
 * <h2>Topology</h2>
 * <pre>
 *                                  earthscan.events (topic)
 *                                            |
 *    user.registered ----------+-------------+--------------------+
 *    user.role-changed --------+             |                    |
 *    user.deleted -------------+---------+---+----------+         |
 *    land.listed --------------------+    |              |        |
 *    forum.post.created ---------+   |    |              |        |
 *    forum.comment.added --------+   |    |              |        |
 *                                |   |    |              |        |
 *                     +----------+   |    |              |        |
 *                     v              v    v              v        v
 *          notification.forum   notification.land   land.user   forum.user
 *              .queue              .queue           -events     -events
 *                                                    .queue      .queue
 *                     (notification.user.queue binds user.#)
 * </pre>
 *
 * <p>Every queue is declared with a dead-letter binding onto {@link #DEAD_LETTER_EXCHANGE} so a
 * message that keeps failing is parked for inspection instead of poison-looping forever.</p>
 */
public final class RabbitTopology {

    private RabbitTopology() {
    }

    /** Topic exchange carrying all domain events. */
    public static final String EVENTS_EXCHANGE = "earthscan.events";

    /** Where messages go after retries are exhausted. */
    public static final String DEAD_LETTER_EXCHANGE = "earthscan.events.dlx";
    public static final String DEAD_LETTER_QUEUE = "earthscan.dlq";
    public static final String DEAD_LETTER_ROUTING_KEY = "dead-letter";

    // ---------------------------------------------------------------- routing keys
    public static final String ROUTING_USER_REGISTERED = "user.registered";
    public static final String ROUTING_USER_ROLE_CHANGED = "user.role-changed";
    public static final String ROUTING_USER_DELETED = "user.deleted";
    public static final String ROUTING_LAND_LISTED = "land.listed";
    public static final String ROUTING_FORUM_POST_CREATED = "forum.post.created";
    public static final String ROUTING_FORUM_COMMENT_ADDED = "forum.comment.added";

    // ---------------------------------------------------------------- binding patterns
    public static final String PATTERN_ALL_USER_EVENTS = "user.#";
    public static final String PATTERN_ALL_LAND_EVENTS = "land.#";
    public static final String PATTERN_ALL_FORUM_EVENTS = "forum.#";

    // ---------------------------------------------------------------- queues
    /** notification-service: welcome messages, role-change notices, account closure notices. */
    public static final String QUEUE_NOTIFICATION_USER = "notification.user.queue";

    /** notification-service: alerts buyers when a new listing matches their saved searches. */
    public static final String QUEUE_NOTIFICATION_LAND = "notification.land.queue";

    /** notification-service: "your question was answered" notices. */
    public static final String QUEUE_NOTIFICATION_FORUM = "notification.forum.queue";

    /** land-service: cascade-deletes listings and saved searches of a removed user. */
    public static final String QUEUE_LAND_USER_EVENTS = "land.user-events.queue";

    /** forum-service: anonymises posts and comments of a removed user. */
    public static final String QUEUE_FORUM_USER_EVENTS = "forum.user-events.queue";
}
