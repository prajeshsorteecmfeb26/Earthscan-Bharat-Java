// ---------------------------------------------------------------------------
// MongoDB initialisation, run once by the container entrypoint on an empty
// data directory.
//
// Spring Data creates these indexes itself via auto-index-creation, so this
// script is not strictly required. It exists for two reasons: the collections
// and indexes are then documented as DDL rather than only as Java annotations,
// and auto-index-creation should be turned OFF in production (building an index
// on application startup can block a deploy for minutes on a large collection)
// — at which point this becomes the authoritative definition.
// ---------------------------------------------------------------------------

// ------------------------------------------------------------ forum database
db = db.getSiblingDB('earthscan_forum');

db.createCollection('forum_posts');

db.forum_posts.createIndex({ category: 1, createdAt: -1 }, { name: 'idx_category_created' });
db.forum_posts.createIndex({ authorId: 1 }, { name: 'idx_author' });
db.forum_posts.createIndex({ createdAt: -1 }, { name: 'idx_created_desc' });
// Supports the expert work queue: unanswered, unresolved threads.
db.forum_posts.createIndex({ commentCount: 1, resolved: 1 }, { name: 'idx_unanswered' });
// Matches a reply author inside the embedded array — the query the account-deletion
// anonymisation depends on.
db.forum_posts.createIndex({ 'comments.authorId': 1 }, { name: 'idx_comment_author' });

// ---------------------------------------------------- notifications database
db = db.getSiblingDB('earthscan_notifications');

db.createCollection('notifications');

db.notifications.createIndex({ userId: 1, createdAt: -1 }, { name: 'idx_user_created' });
db.notifications.createIndex({ userId: 1, read: 1 }, { name: 'idx_user_read' });

// UNIQUE, and load-bearing. RabbitMQ guarantees at-least-once delivery, so a consumer
// that crashes after processing but before acknowledging will see the same message again
// on restart. This constraint is what turns that redelivery into a caught duplicate-key
// error instead of a second copy in the user's inbox.
db.notifications.createIndex(
    { sourceEventId: 1, userId: 1 },
    { name: 'idx_event_user_unique', unique: true }
);

print('EarthScan MongoDB collections and indexes initialised.');
