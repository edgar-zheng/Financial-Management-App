CREATE TABLE portfolios (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    -- Nullable to preserve legacy ownerless portfolios; API creation always sets an owner.
    owner_id BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_portfolios_owner FOREIGN KEY (owner_id) REFERENCES users (id)
) ENGINE=InnoDB;
