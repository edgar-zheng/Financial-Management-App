package com.edgar.portfolio.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edgar.portfolio.dto.HoldingDto;
import com.edgar.portfolio.service.HoldingService;

@RestController
@RequestMapping("/api/portfolios/{id}/holdings")
public class HoldingController {

	private final HoldingService holdingService;

	public HoldingController(HoldingService holdingService) {
		this.holdingService = holdingService;
	}

	@GetMapping
	public List<HoldingDto> getHoldings(@PathVariable("id") Long id) {
		return holdingService.getHoldings(id);
	}
}
