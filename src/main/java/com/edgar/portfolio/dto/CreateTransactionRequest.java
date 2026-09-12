package com.edgar.portfolio.dto;

import java.math.BigDecimal;
import com.edgar.portfolio.entity.TransactionType;
import jakarta.validation.constraints.*;

public record CreateTransactionRequest(
		@NotBlank @Size(max = 32) String symbol,
		@NotNull TransactionType type,
		@NotNull @Positive @Digits(integer = 11, fraction = 8) BigDecimal quantity,
		@NotNull @Positive @Digits(integer = 11, fraction = 8) BigDecimal price) {
}
