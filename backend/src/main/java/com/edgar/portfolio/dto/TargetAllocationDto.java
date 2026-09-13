package com.edgar.portfolio.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public record TargetAllocationDto(@NotBlank @Size(max = 32) String symbol,
		@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal targetPercent) {}
