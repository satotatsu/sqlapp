package com.sqlapp.data.db.command.generator;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class GeneratorProgressTest {
	@Test
	void unknownTotalShowsCountElapsedAndRateWithoutPercentageAndThrottles() {
		var time = new AtomicLong();
		var progress = new GeneratorProgress(-1, time::get);
		time.set(2_000_000_000L);
		String message = progress.update(400);
		assertTrue(message.contains("Rows processed=[400]"));
		assertTrue(message.contains("total=[unknown]"));
		assertTrue(message.contains("elapsedMs=[2000]"));
		assertTrue(message.contains("rowsPerSecond=[200.0]"));
		assertFalse(message.contains("%"));
		assertNull(progress.update(500));
		time.set(3_000_000_000L);
		assertNotNull(progress.update(600));
	}

	@Test
	void exactModeDoesNotClaimCompletionOnFirstSmallBatch() {
		var progress = new GeneratorProgress(5, () -> 0);
		assertTrue(progress.update(2).contains("40% update completed.[2/5]"));
		assertNull(progress.update(2));
		assertTrue(progress.update(5).contains("100% update completed.[5/5]"));
	}

	@Test
	void emptyAndVeryLargeTotalsDoNotDivideByZeroOrOverflow() {
		assertNotNull(new GeneratorProgress(0, () -> 0).update(0));
		var progress = new GeneratorProgress(Long.MAX_VALUE, () -> 0);
		assertTrue(progress.update(Long.MAX_VALUE - 1).startsWith("99%"));
		assertTrue(progress.update(Long.MAX_VALUE).startsWith("100%"));
	}
}
