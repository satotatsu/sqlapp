package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SpannerChangeStreamTest {
	@Test
	void preservesCatalogXmlAndPlansCreateAlterAndDrop() throws Exception {
		final var dialect = com.sqlapp.data.db.dialect.spanner.DialectHolder.defaultDialect;
		final var original = new com.sqlapp.data.schemas.Catalog("test-db").setDialect(dialect);
		SpannerChangeStreams.set(original, List.of());
		final var target = original.clone().setDialect(dialect);
		final var all = new SpannerChangeStream("Audit", SpannerChangeStream.Scope.ALL, List.of(),
				new SpannerChangeStream.Options(Duration.ofDays(2), SpannerChangeStream.Capture.NEW_ROW, Map.of()));
		SpannerChangeStreams.set(target, List.of(all));
		final com.sqlapp.data.schemas.Catalog restored = com.sqlapp.data.schemas.SchemaUtils
				.readXml(new java.io.StringReader(target.asXml()));
		restored.setDialect(dialect);
		assertEquals(List.of(all), SpannerChangeStreams.get(restored).orElseThrow());
		final var registry = dialect.createSqlFactoryRegistry();
		assertTrue(registry.createSql(original.diff(restored)).getFirst().getSqlText().startsWith("CREATE CHANGE STREAM"));
		final var changed = restored.clone().setDialect(dialect);
		SpannerChangeStreams.set(changed, List.of(new SpannerChangeStream("Audit", SpannerChangeStream.Scope.NONE,
				List.of(), all.options())));
		assertEquals("ALTER CHANGE STREAM `Audit` DROP FOR ALL",
				registry.createSql(restored.diff(changed)).getFirst().getSqlText());
		assertTrue(registry.createSql(restored.diff(original)).getFirst().getSqlText().startsWith("DROP CHANGE STREAM"));
		assertTrue(registry.createSql(restored.diff(new com.sqlapp.data.schemas.Catalog("test-db").setDialect(dialect))).isEmpty());
		final var withSchema = target.clone().setDialect(dialect);
		withSchema.getSchemas().add(new com.sqlapp.data.schemas.Schema("Sales").setDialect(dialect));
		final var added = registry.createSql(original.diff(withSchema));
		assertTrue(added.getFirst().getSqlText().startsWith("CREATE SCHEMA"));
		assertTrue(added.getLast().getSqlText().startsWith("CREATE CHANGE STREAM"));
		final var removed = registry.createSql(withSchema.diff(original));
		assertTrue(removed.getFirst().getSqlText().startsWith("DROP CHANGE STREAM"));
		assertTrue(removed.getLast().getSqlText().startsWith("DROP SCHEMA"));
	}

	@Test
	void distinguishesWholeTablesKeyOnlyAndSelectedColumns() {
		final var stream = new SpannerChangeStream("Audit", SpannerChangeStream.Scope.TABLES, List.of(
				new SpannerChangeStream.Target("Sales", "Orders", List.of("Select")),
				new SpannerChangeStream.Target(null, "Items", List.of()),
				new SpannerChangeStream.Target(null, "Customers", null)), null);
		assertEquals("CREATE CHANGE STREAM IF NOT EXISTS `Audit` FOR `Sales`.`Orders`(`Select`), "
				+ "`Items`(), `Customers`", stream.createSql(true));
		assertEquals("DROP CHANGE STREAM IF EXISTS `Audit`", stream.dropSql(true));
	}

	@Test
	void rendersExplicitScopeAndOptionChanges() {
		final var options = new SpannerChangeStream.Options(Duration.ofDays(2), SpannerChangeStream.Capture.NEW_ROW,
				Map.of(SpannerChangeStream.Filter.EXCLUDE_DELETE, true));
		final var stream = new SpannerChangeStream("Audit", SpannerChangeStream.Scope.ALL, List.of(), options);
		assertEquals("ALTER CHANGE STREAM `Audit` SET FOR ALL", stream.setScopeSql());
		assertEquals("ALTER CHANGE STREAM `Audit` SET OPTIONS (retention_period = '172800s', "
				+ "value_capture_type = 'NEW_ROW', exclude_delete = true)", stream.setOptionsSql());
		final var stopped = new SpannerChangeStream("Audit", SpannerChangeStream.Scope.NONE, List.of(), null);
		assertEquals("CREATE CHANGE STREAM `Audit`", stopped.createSql(false));
		assertEquals("ALTER CHANGE STREAM `Audit` DROP FOR ALL", stopped.setScopeSql());
	}

	@Test
	void rejectsInvalidIdentifiersAndRetention() {
		assertThrows(IllegalArgumentException.class, () -> new SpannerChangeStream(
				"Audit`", SpannerChangeStream.Scope.NONE, List.of(), null));
		assertThrows(IllegalArgumentException.class, () -> new SpannerChangeStream.Options(
				Duration.ofHours(1), null, Map.of()));
		assertThrows(IllegalArgumentException.class, () -> new SpannerChangeStream(
				"Audit", SpannerChangeStream.Scope.TABLES, List.of(), null));
	}
}
