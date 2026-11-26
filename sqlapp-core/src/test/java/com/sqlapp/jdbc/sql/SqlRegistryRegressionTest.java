package com.sqlapp.jdbc.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.jdbc.sql.node.SqlNode;

class SqlRegistryRegressionTest {

	private final Dialect dialect = DialectResolver.getInstance().getDefaultDialect();

	@Test
	void singleSqlOverloadsRegisterInTheCorrectNamespace() {
		MapSqlRegistry registry = new MapSqlRegistry();
		registry.put("default", "SELECT 1");
		registry.put("explicitDefault", (Dialect) null, "SELECT 2");
		registry.put("vendor", dialect, "SELECT 3");
		assertEquals("SELECT 1", registry.get("default").toString());
		assertEquals("SELECT 2", registry.get("explicitDefault").toString());
		assertEquals("SELECT 3", registry.get("vendor", dialect).toString());
		assertNull(registry.get("vendor"));
	}

	@Test
	void cacheLoadsOnceAndPreservesMissingResults() {
		AtomicInteger loads = new AtomicInteger();
		MapSqlRegistry source = new MapSqlRegistry() {
			@Override
			public List<SqlNode> getAll(String id, Dialect requestedDialect) {
				loads.incrementAndGet();
				return super.getAll(id, requestedDialect);
			}
		};
		source.put("query", "SELECT 1");
		CachedSqlRegistry cache = new CachedSqlRegistry(source);
		assertTrue(cache.contains("query"));
		for (Dialect requested : new Dialect[] { null, dialect }) {
			List<SqlNode> first = cache.getAll("query", requested);
			assertEquals(1, first.size());
			assertSame(first, cache.getAll("query", requested));
			assertTrue(cache.getAll("missing", requested).isEmpty());
			assertNull(cache.get("missing", requested));
		}
		assertEquals(4, loads.get());
	}

	@Test
	void mutationsInvalidateProductFallbacksAndReplacedRegistries() {
		CachedSqlRegistry cache = new CachedSqlRegistry(new MapSqlRegistry());
		assertNull(cache.get("query", dialect));
		cache.put("query", "SELECT 1");
		assertEquals("SELECT 1", cache.get("query", dialect).toString());
		cache.put("query", "SELECT 2");
		assertEquals("SELECT 2", cache.get("query", dialect).toString());
		cache.put("query", dialect, "SELECT 3");
		assertEquals("SELECT 3", cache.get("query", dialect).toString());
		cache.remove("query", dialect);
		assertEquals("SELECT 2", cache.get("query", dialect).toString());
		cache.remove("query");
		assertNull(cache.get("query", dialect));
		cache.setSqlRegistry(new MapSqlRegistry());
		assertFalse(cache.contains("query"));
		cache.put("query", "SELECT 4");
		assertEquals("SELECT 4", cache.get("query").toString());
		MapSqlRegistry replacement = new MapSqlRegistry();
		replacement.put("query", "SELECT 5");
		cache.setSqlRegistry(replacement);
		assertEquals("SELECT 5", cache.get("query").toString());
	}

	@Test
	void parsedIncludesResolveAndBindOnEveryEvaluation() {
		CachedSqlRegistry registry = new CachedSqlRegistry(new MapSqlRegistry());
		registry.put("part", "id=/*id*/0");
		registry.put("query", "SELECT * FROM t WHERE /*include 'part'*/");
		SqlNode query = registry.get("query", dialect);
		for (int value : new int[] { 1, 2 }) {
			SqlParameterCollection result = query.eval(Map.of("id", value));
			assertEquals("SELECT * FROM t WHERE id = ?", result.getSql().trim());
			assertEquals(value, result.getBindParameters().get(0).getBindParameter().getValue());
		}
		registry.put("part", "other_id=/*id*/0");
		assertTrue(query.eval(Map.of("id", 3)).getSql().contains("other_id"));
	}

	@Test
	void missingIncludesFailClearly() {
		MapSqlRegistry registry = new MapSqlRegistry();
		registry.put("query", "SELECT /*include 'missing'*/");
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> registry.get("query").eval(Map.of()));
		assertTrue(error.getMessage().contains("missing"));
	}
}
