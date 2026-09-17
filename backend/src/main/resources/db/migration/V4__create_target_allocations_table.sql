CREATE TABLE target_allocations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    portfolio_id BIGINT NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    target_percent DECIMAL(7,4) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_targets_portfolio_symbol UNIQUE (portfolio_id, symbol),
    CONSTRAINT fk_targets_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios (id)
) ENGINE=InnoDB;
