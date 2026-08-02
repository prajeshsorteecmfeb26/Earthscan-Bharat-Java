-- ---------------------------------------------------------------------------
-- earthscan_land reference schema.
--
-- Note the deliberate absence of a foreign key on owner_id: the referenced user
-- lives in another service's schema, and a cross-schema constraint would re-couple
-- the two databases at exactly the layer the service split exists to separate.
-- Integrity across that boundary is maintained by the user.deleted RabbitMQ event.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS soil_types (
    id                     INT AUTO_INCREMENT PRIMARY KEY,
    name                   VARCHAR(60)  NOT NULL,
    fertility_index        INT          NOT NULL,
    water_retention_index  INT          NOT NULL,
    description            VARCHAR(255),
    CONSTRAINT uk_soil_types_name UNIQUE (name),
    CONSTRAINT ck_soil_fertility  CHECK (fertility_index BETWEEN 0 AND 100),
    CONSTRAINT ck_soil_retention  CHECK (water_retention_index BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lands (
    id                            BIGINT AUTO_INCREMENT PRIMARY KEY,
    title                         VARCHAR(150)   NOT NULL,
    description                   VARCHAR(2000),
    location                      VARCHAR(200)   NOT NULL,
    district                      VARCHAR(80),
    state                         VARCHAR(80)    DEFAULT 'Maharashtra',
    latitude                      DOUBLE,
    longitude                     DOUBLE,
    -- DECIMAL, never DOUBLE. Land prices run to crores and binary floating point cannot
    -- represent them exactly; the error compounds through the price/acre division that the
    -- investment analysis is built on.
    price                         DECIMAL(18,2)  NOT NULL,
    size_in_acres                 DOUBLE         NOT NULL,
    soil_type_id                  INT            NOT NULL,
    groundwater_level_depth       DOUBLE         NOT NULL,
    annual_rainfall_mm            INT,
    irrigation_available          BOOLEAN        NOT NULL DEFAULT FALSE,
    road_access                   BOOLEAN        NOT NULL DEFAULT FALSE,
    -- Derived by the scoring engine. Stored rather than computed on read because search
    -- filters and sorts on them, which would otherwise be unindexable.
    land_intelligence_score       DOUBLE         NOT NULL DEFAULT 0,
    borewell_success_probability  DOUBLE         NOT NULL DEFAULT 0,
    scored_at                     TIMESTAMP      NULL,
    owner_id                      BIGINT         NOT NULL,
    status                        VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    verified                      BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at                    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_lands_soil_type FOREIGN KEY (soil_type_id) REFERENCES soil_types(id),
    CONSTRAINT ck_lands_size      CHECK (size_in_acres > 0),
    CONSTRAINT ck_lands_score     CHECK (land_intelligence_score BETWEEN 0 AND 100),
    CONSTRAINT ck_lands_borewell  CHECK (borewell_success_probability BETWEEN 0 AND 100),
    INDEX idx_lands_owner (owner_id),
    INDEX idx_lands_district (district),
    INDEX idx_lands_status (status),
    -- Composite: the search screen filters on price and sorts by score together.
    INDEX idx_lands_price_score (price, land_intelligence_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS saved_searches (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    label            VARCHAR(150) NOT NULL,
    location_query   VARCHAR(200),
    soil_type_name   VARCHAR(60),
    min_price        DECIMAL(18,2),
    max_price        DECIMAL(18,2),
    min_score        DOUBLE,
    land_id          BIGINT,
    notify_on_match  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_saved_searches_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
