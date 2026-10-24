package com.sqlapp.data.db.command.generator.config;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.util.eval.mvel.CachedMvelEvaluator;

class GeneratorSourceCountTest {
	@Test
	void builtInRangeReportsHugeExactSizeWithoutWalkingIt() {
		var config = new TableGeneratorConfig();
		config.setDataSourceExpression("iterator(1000000000000L)");
		config.setColumnMappingExpression("new java.util.HashMap()");
		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertEquals(1000000000000L, config.iterateCount()));
		config.setDataSourceExpression("iterator(0)");
		assertEquals(0, config.iterateCount());
	}

	@Test
	void genericSourceIsCountedAndItsIteratorIsClosed() {
		var reads = new AtomicInteger();
		var closes = new AtomicInteger();
		class SourceIterator implements Iterator<Map<String, Object>>, AutoCloseable {
			public boolean hasNext() {
				return reads.get() < 3;
			}

			public Map<String, Object> next() {
				reads.incrementAndGet();
				return Map.of();
			}

			public void close() {
				closes.incrementAndGet();
			}
		}
		var config = config(() -> new SourceIterator());
		assertEquals(3, config.iterateCount());
		assertEquals(3, reads.get());
		assertEquals(1, closes.get());
	}

	@Test
	void collectionsUseSizeAndInvalidMappingStillFails() {
		var config = config(List.of(Map.of("ID", 1), Map.of("ID", 2)));
		assertEquals(2, config.iterateCount());
		config.setColumnMappingExpression("42");
		assertThrows(RuntimeException.class, config::iterateCount);
	}

	private TableGeneratorConfig config(Iterable<Map<String, Object>> source) {
		var config = new TableGeneratorConfig();
		config.setDataSourceExpression("testSource");
		config.setEvaluator(new CachedMvelEvaluator() {
			@Override
			@SuppressWarnings("unchecked")
			public <T> T eval(String expression, Map<?, ?> context) {
				return expression.equals("testSource") ? (T) source : super.eval(expression, context);
			}
		});
		return config;
	}
}
