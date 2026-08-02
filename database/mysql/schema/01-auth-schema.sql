-- ---------------------------------------------------------------------------
-- earthscan_auth reference schema.
--
-- The running services create this via Hibernate ddl-auto in the dev profile.
-- This file is the authoritative definition for the prod profile, where
-- ddl-auto is 'validate' and the schema is applied deliberately.
--
-- Use it as the baseline for a Flyway V1__baseline.sql when adopting migrations.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS roles (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(30)  NOT NULL,
    display_name  VARCHAR(50)  NOT NULL,
    description   VARCHAR(255),
    CONSTRAINT uk_roles_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS users (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    email          VARCHAR(150) NOT NULL,
    -- BCrypt output is always 60 characters; 100 leaves room for an algorithm change.
    password_hash  VARCHAR(100) NOT NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_email UNIQUE (email),
    INDEX idx_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_roles (
    user_id  BIGINT NOT NULL,
    role_id  INT    NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seeded idempotently by RoleSeeder on startup; included here so the schema is complete
-- without the application having run.
INSERT IGNORE INTO roles (name, display_name, description) VALUES
    ('FARMER',             'Farmer',             'Owns or cultivates land; uses water, crop and mandi advisory tools'),
    ('LAND_BUYER',         'Land Buyer',         'Searches, compares and analyses land listings for investment'),
    ('AGRICULTURE_EXPERT', 'Agriculture Expert', 'Answers farmer queries and curates crop reference data'),
    ('ADMIN',              'Admin',              'Full platform administration including user management');
