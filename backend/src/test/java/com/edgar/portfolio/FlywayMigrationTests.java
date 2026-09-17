package com.edgar.portfolio;

import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FlywayMigrationTests {
	@Autowired private Flyway flyway;
	@Test
	void allFourMigrationsAppliedAndSecondMigrationRunIsNoOp() {
		assertEquals("4", flyway.info().current().getVersion().getVersion());
		assertEquals(0, flyway.info().pending().length);
		assertEquals(4, Arrays.stream(flyway.info().applied())
				.filter(migration -> "SQL".equals(migration.getType().name())).count());
		flyway.validate();
		assertEquals(0, flyway.migrate().migrationsExecuted);
	}
}
