package com.edgar.portfolio.service;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.edgar.portfolio.dto.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class AllocationDriftServiceTests {
	@Test
	void comparesUnionOfTargetsAndHoldings() {
		var targets = mock(AllocationService.class);
		var analytics = mock(PortfolioAnalyticsService.class);
		when(targets.getTargets(1L)).thenReturn(List.of(new TargetAllocationDto("AAPL", new BigDecimal("30")),
				new TargetAllocationDto("VOO", new BigDecimal("70"))));
		var asset = new AssetAllocationDto("AAPL", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
				new BigDecimal("36"), BigDecimal.ZERO, BigDecimal.ZERO, null);
		var other = new AssetAllocationDto("MSFT", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
				new BigDecimal("64"), BigDecimal.ZERO, BigDecimal.ZERO, null);
		when(analytics.getAnalytics(1L)).thenReturn(new PortfolioSummaryDto(1L, "USD", "PREVIOUS_CLOSE",
				BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(asset, other)));
		var result = new AllocationDriftService(targets, analytics).getDrift(1L);
		assertEquals(3, result.size());
		assertEquals(new BigDecimal("6"), result.get(0).driftPercentagePoints());
		assertEquals(new BigDecimal("64"), result.get(1).driftPercentagePoints());
		assertEquals(new BigDecimal("-70"), result.get(2).driftPercentagePoints());
	}
}
