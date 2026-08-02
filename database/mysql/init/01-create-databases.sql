-- ---------------------------------------------------------------------------
-- Creates one schema per relational service.
--
-- Each service owns exactly one schema and never queries the other's tables.
-- There is deliberately no foreign key between them: a cross-schema constraint
-- would make a land row's existence depend on the user schema, which is the
-- coupling the service split exists to remove. Referential integrity across
-- that boundary is maintained by the `user.deleted` event instead.
-- ---------------------------------------------------------------------------

CREATE DATABASE IF NOT EXISTS earthscan_auth
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS earthscan_land
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- utf8mb4 rather than utf8: the platform serves Marathi and Hindi content, and
-- MySQL's legacy `utf8` is a 3-byte encoding that cannot store the full
-- Devanagari range or any emoji a user types into a forum post.

-- Least-privilege service accounts. The compose file connects as root for
-- convenience during development; production should use these instead.
CREATE USER IF NOT EXISTS 'earthscan_auth_user'@'%' IDENTIFIED BY 'change_me_auth';
GRANT SELECT, INSERT, UPDATE, DELETE ON earthscan_auth.* TO 'earthscan_auth_user'@'%';

CREATE USER IF NOT EXISTS 'earthscan_land_user'@'%' IDENTIFIED BY 'change_me_land';
GRANT SELECT, INSERT, UPDATE, DELETE ON earthscan_land.* TO 'earthscan_land_user'@'%';

-- Note the grants are DML only. Schema changes are applied deliberately, by a
-- migration run with elevated credentials, not by the running application.

FLUSH PRIVILEGES;
