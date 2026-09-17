package com.edgar.portfolio.dto;

import java.time.LocalDateTime;
import com.edgar.portfolio.entity.Portfolio;

public record PortfolioResponse(Long id, String name, LocalDateTime createdAt) {
    public static PortfolioResponse from(Portfolio portfolio) {
        return new PortfolioResponse(portfolio.getId(), portfolio.getName(), portfolio.getCreatedAt());
    }
}
