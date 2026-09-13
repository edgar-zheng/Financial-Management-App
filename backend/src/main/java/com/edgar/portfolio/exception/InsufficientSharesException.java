package com.edgar.portfolio.exception;

public class InsufficientSharesException extends RuntimeException {
	public InsufficientSharesException(String symbol) {
		super("Sell quantity exceeds shares owned for " + symbol);
	}
}
