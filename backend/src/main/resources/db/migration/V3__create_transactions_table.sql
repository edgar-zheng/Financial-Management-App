CREATE TABLE transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    portfolio_id BIGINT NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    type ENUM('BUY', 'SELL') NOT NULL,
    quantity DECIMAL(19,8) NOT NULL,
    price DECIMAL(19,8) NOT NULL,
    timestamp DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_transactions_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios (id)
) ENGINE=InnoDB;
