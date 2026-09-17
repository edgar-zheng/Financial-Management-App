package com.edgar.portfolio.exception;

/** Missing and unowned portfolios deliberately produce the same response. */
public class PortfolioNotFoundException extends RuntimeException {
    public PortfolioNotFoundException() {
        super("Portfolio not found");
    }
}
