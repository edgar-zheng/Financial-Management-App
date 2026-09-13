package com.edgar.portfolio.controller;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import com.edgar.portfolio.dto.AllocationDriftDto;
import com.edgar.portfolio.dto.TargetAllocationDto;
import com.edgar.portfolio.service.AllocationService;
import com.edgar.portfolio.service.AllocationDriftService;

@RestController
@RequestMapping("/api/portfolios/{id}/allocations")
public class AllocationController {
	private final AllocationService allocations;
	private final AllocationDriftService drift;
	public AllocationController(AllocationService allocations, AllocationDriftService drift) {
		this.allocations = allocations; this.drift = drift;
	}
	public record TargetRequest(@NotNull @Size(max = 100) List<@NotNull @Valid TargetAllocationDto> targets) {}
	@GetMapping
	public List<TargetAllocationDto> getTargets(@PathVariable("id") Long id) { return allocations.getTargets(id); }
	@PutMapping
	public List<TargetAllocationDto> saveTargets(@PathVariable("id") Long id, @Valid @RequestBody TargetRequest request) {
		return allocations.saveTargets(id, request.targets());
	}
	@GetMapping("/drift")
	public List<AllocationDriftDto> getDrift(@PathVariable("id") Long id) {
		return drift.getDrift(id);
	}
}
