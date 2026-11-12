/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.cockroach.resolver.CockroachDialectResolver;
import com.sqlapp.data.db.dialect.cockroach.util.CockroachSequenceOptions;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.bulk.*;

class CockroachDialectTest {
	@Test
	void restoresLegacyOwnerSpecificsAndPrefersCommonOwnerName() throws Exception {
		var schema = new Schema("reports");
		var view = new Mview("totals");
		view.setStatement("SELECT 7");
		view.getSpecifics().put("COCKROACH_OWNER", "legacy_owner");
		view.getIndexes()
				.add(new Index("legacy_idx").setDefinition("CREATE INDEX legacy_idx ON reports.totals (total)"));
		schema.getMviews().add(view);
		Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
		var legacy = restored.getMviews().get("totals");
		restored.setDialect(new CockroachDB());
		var registry = new CockroachDB().createSqlFactoryRegistry();
		var operations = registry.createSql(legacy, SqlType.CREATE);
		assertEquals(3, operations.size());
		assertTrue(operations.get(1).getSqlText().startsWith("CREATE INDEX"));
		assertTrue(operations.getLast().getSqlText().endsWith("OWNER TO \"legacy_owner\""));
		legacy.setOwnerName("common_owner");
		assertTrue(registry.createSql(legacy, SqlType.CREATE).getLast().getSqlText()
				.endsWith("OWNER TO \"common_owner\""));
		var target = (Mview) legacy.clone();
		target.setOwnerName("new_owner");
		assertTrue(registry.createSql(legacy.diff(target)).getFirst().getSqlText().endsWith("OWNER TO \"new_owner\""));
	}

	@Test
	void rejectsMaterializedViewCommentsThatCannotBeRecreated() {
		var view = new Mview("totals");
		view.setStatement("SELECT 7").setRemarks("report");
		var registry = new CockroachDB().createSqlFactoryRegistry();
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(view, SqlType.CREATE));
		view.setDefinition("CREATE MATERIALIZED VIEW totals AS SELECT 7");
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(view, SqlType.CREATE));
		view.setRemarks(null);
		view.setOwnerName("root").setSchemaName("reports");
		var changed = (Mview) view.clone();
		changed.setRemarks("changed");
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(view.diff(changed)));
		changed.setOwnerName("Reporting Owners");
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(view.diff(changed)));
		view.setTableSpaceName("pg_default");
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(view, SqlType.CREATE));
		view.setTableSpaceName(null);
		view.getColumns().add(new Column("total").setRemarks("column note"));
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(view, SqlType.CREATE));
	}

	@Test
	void rejectsMaterializedViewRenameAsSchemaDeleteAndCreate() throws Exception {
		var original = new Schema("reports");
		original.setDialect(new CockroachDB());
		var view = new Mview("old_name");
		view.setId("123").setStatement("SELECT 7");
		original.getMviews().add(view);
		Schema restored = SchemaUtils.readXml(new StringReader(original.asXml()));
		restored.setDialect(new CockroachDB());
		var target = restored.clone();
		target.getMviews().get("old_name").setName("new_name");
		var registry = new CockroachDB().createSqlFactoryRegistry();
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(restored.diff(target)));
		assertEquals(1, registry
				.createSql(restored.getMviews().get("old_name").diff(target.getMviews().get("new_name"))).size());
	}

	@Test
	void generatesMaterializedViewOwnershipChangeToALiteralRole() {
		var dialect = new CockroachDB();
		var view = new Mview("Daily Totals");
		view.setSchemaName("Reports");
		var operation = dialect.createMaterializedViewOwnerChangeSql(view, "Report \"Owners\"");
		assertEquals(SqlType.ALTER, operation.getSqlType());
		assertEquals("ALTER MATERIALIZED VIEW \"Reports\".\"Daily Totals\" OWNER TO \"Report \"\"Owners\"\"\"",
				operation.getSqlText());
		assertThrows(IllegalArgumentException.class, () -> dialect.createMaterializedViewOwnerChangeSql(view, " "));
		assertThrows(IllegalArgumentException.class,
				() -> dialect.createMaterializedViewOwnerChangeSql(new Mview("totals"), "owner"));
	}

	@Test
	void movesMaterializedViewOnlyWithinTheSameDatabase() {
		var attached = new Mview("Daily Totals");
		attached.getIndexes().add(new Index("by_total"));
		var sourceSchema = new Schema("Reports");
		sourceSchema.setDialect(new CockroachDB());
		sourceSchema.setCatalogName("db");
		sourceSchema.getMviews().add(attached);
		var original = attached.clone();
		var target = original.clone().setSchemaName("Archive");
		var registry = new CockroachDB().createSqlFactoryRegistry();
		var operations = registry.createSql(original.diff(target));
		assertEquals(1, operations.size());
		assertEquals("ALTER MATERIALIZED VIEW \"Reports\".\"Daily Totals\" SET SCHEMA \"Archive\"",
				operations.getFirst().getSqlText());
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target.clone().setCatalogName("other"))));
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target.clone().setName("renamed"))));
	}

	@Test
	void renamesMaterializedViewWithQualifiedOriginalAndUnqualifiedTarget() {
		var original = new Mview("Daily Totals").setSchemaName("Reports");
		original.getIndexes().add(
				new Index("by_total").setDefinition("CREATE INDEX by_total ON \"Reports\".\"Daily Totals\" (total)"));
		var target = original.clone().setName("Weekly Totals");
		var registry = new CockroachDB().createSqlFactoryRegistry();
		var operations = registry.createSql(original.diff(target));
		assertEquals(1, operations.size());
		assertEquals("ALTER MATERIALIZED VIEW \"Reports\".\"Daily Totals\" RENAME TO \"Weekly Totals\"",
				operations.getFirst().getSqlText());
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target.clone().setStatement("SELECT 8"))));
	}

	@Test
	void generatesExplicitMaterializedViewRefreshWithQuotedName() {
		var view = new Mview("Daily Totals");
		view.setSchemaName("Reports");
		var registry = new CockroachDB().createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		var operations = registry.createSql(view, SqlType.REFRESH);
		assertEquals(1, operations.size());
		assertEquals(SqlType.REFRESH, operations.getFirst().getSqlType());
		assertEquals("REFRESH MATERIALIZED VIEW \"Reports\".\"Daily Totals\"", operations.getFirst().getSqlText());
	}

	@Test
	void appliesMaterializedViewIndexDiffThroughSchemaRegistry() {
		var original = new Schema("reports");
		var view = new Mview("totals");
		view.setStatement("SELECT 7 AS total");
		var index = new Index("old_idx");
		index.setDefinition("CREATE INDEX old_idx ON reports.totals (total)");
		view.getIndexes().add(index);
		original.getMviews().add(view);
		var target = original.clone();
		var targetView = target.getMviews().get("totals");
		targetView.getIndexes().remove(targetView.getIndexes().get("old_idx"));
		var replacement = new Index("new_idx");
		String create = "CREATE INDEX new_idx ON reports.totals (total DESC)";
		replacement.setDefinition(create);
		targetView.getIndexes().add(replacement);
		var registry = new CockroachDB().createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		var operations = registry.createSql(original.diff(target));
		assertEquals(2, operations.size());
		assertEquals(SqlType.DROP, operations.getFirst().getSqlType());
		assertEquals("DROP INDEX reports.totals@old_idx", operations.getFirst().getSqlText());
		assertEquals(create, operations.getLast().getSqlText());
		targetView.setStatement("SELECT 8 AS total");
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(original.diff(target)));

		var modified = original.clone();
		modified.getMviews().get("totals").getIndexes().get("old_idx")
				.setDefinition("CREATE INDEX old_idx ON reports.totals (total DESC)");
		assertEquals(2, registry.createSql(original.diff(modified)).size());
	}

	@Test
	void roundTripsMaterializedViewAliasesAndGeneratesCreateAndDrop() throws Exception {
		var schema = new Schema("Reports");
		var view = new Mview("Daily Totals");
		view.setStatement("SELECT 7");
		view.getColumns().add(new Column("Total \"USD\""));
		view.getColumns().add(new Column("rowid").setHidden(true));
		var index = new Index("by_total");
		String indexDdl = "CREATE INDEX by_total ON \"Reports\".\"Daily Totals\" (\"Total \"\"USD\"\"\" DESC)";
		index.setDefinition(indexDdl);
		index.setRemarks("'index'");
		view.getIndexes().add(index);
		schema.getMviews().add(view);
		Schema restoredSchema = SchemaUtils.readXml(new StringReader(schema.asXml()));
		var restored = restoredSchema.getMviews().get("Daily Totals");
		var registry = new CockroachDB().createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);

		var operations = registry.createSql(restored, SqlType.CREATE);
		assertEquals(3, operations.size());
		assertEquals(indexDdl, operations.get(1).getSqlText());
		assertTrue(operations.get(2).getSqlText().endsWith(" IS '''index'''"));
		String create = operations.getFirst().getSqlText().replaceAll("\\s+", " ");
		assertEquals("CREATE MATERIALIZED VIEW \"Reports\".\"Daily Totals\" (\"Total \"\"USD\"\"\") AS SELECT 7",
				create);
		assertEquals("DROP MATERIALIZED VIEW \"Reports\".\"Daily Totals\"",
				registry.createSql(restored, SqlType.DROP).getFirst().getSqlText());
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(new Mview("empty"), SqlType.CREATE));
	}

	@Test
	void ordersMaterializedViewDiffsAndRejectsImplicitReplacement() {
		var empty = new Schema("reports");
		var schema = empty.clone();
		var source = new View("source_view");
		source.setDefinition("CREATE VIEW reports.source_view AS SELECT 7 AS total");
		schema.getViews().add(source);
		var view = new Mview("totals");
		view.setStatement("SELECT total FROM reports.source_view");
		schema.getMviews().add(view);
		var registry = new CockroachDB().createSqlFactoryRegistry();
		var create = registry.createSql(empty.diff(schema));
		assertEquals(2, create.size());
		assertTrue(create.get(0).getSqlText().startsWith("CREATE VIEW"));
		assertTrue(create.get(1).getSqlText().startsWith("CREATE MATERIALIZED VIEW"));
		var drop = registry.createSql(schema.diff(empty));
		assertEquals(2, drop.size());
		assertTrue(drop.get(0).getSqlText().startsWith("DROP MATERIALIZED VIEW"));
		assertTrue(drop.get(1).getSqlText().startsWith("DROP VIEW"));
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(view.diff(view.clone().setStatement("SELECT 8"))));
	}

	private DatabaseMetaData metadata(String name, String version) {
		return (DatabaseMetaData) Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(),
				new Class<?>[] { DatabaseMetaData.class }, (p, m, a) -> switch (m.getName()) {
				case "getDatabaseProductName" -> name;
				case "getDatabaseProductVersion" -> version;
				case "getConnection" -> null;
				case "getDatabaseMajorVersion" -> 13;
				case "getDatabaseMinorVersion" -> 0;
				default -> throw new UnsupportedOperationException(m.getName());
				});
	}

	@Test
	void resolvesReleaseBannerWithoutPollutingPostgresCache() {
		var resolver = DialectResolver.getInstance();
		assertEquals("PostgreSQL", resolver.getDialect("PostgreSQL", 13, 0, null).getProductName());
		assertInstanceOf(CockroachDB.class,
				resolver.getDialect(metadata("PostgreSQL", "CockroachDB CCL v25.4.0 (x86_64)")));
		assertEquals("PostgreSQL", resolver.getDialect(metadata("PostgreSQL", "13.0")).getProductName());
		assertInstanceOf(CockroachDB.class, resolver.getDialect("CockroachDB", 24, 3, null));
	}

	@Test
	void rejectsUnsupportedReleaseAndUnidentifiedVendor() {
		var resolver = new CockroachDialectResolver();
		assertThrows(IllegalArgumentException.class, () -> resolver.getDialect("CockroachDB", 24, 2, null));
		assertThrows(IllegalArgumentException.class,
				() -> resolver.resolveDatabaseMetaData(metadata("CockroachDB", "unknown")));
		assertNull(resolver.resolveDatabaseMetaData(metadata("Other", "CockroachDB CCL v25.4.0")));
	}

	@Test
	void surfacesIdentificationFailure() {
		var metadata = (DatabaseMetaData) Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(),
				new Class<?>[] { DatabaseMetaData.class }, (p, m, a) -> {
					throw new SQLException("broken");
				});
		assertInstanceOf(SQLException.class, assertThrows(IllegalStateException.class,
				() -> new CockroachDialectResolver().resolveDatabaseMetaData(metadata)).getCause());
	}

	@Test
	void registersDedicatedBulkProviders() {
		var dialect = new CockroachDB();
		assertInstanceOf(com.sqlapp.data.db.dialect.cockroach.bulk.CockroachBulkInsertExecutor.class,
				BulkInsertResolver.resolve(dialect));
		assertInstanceOf(com.sqlapp.data.db.dialect.cockroach.bulk.CockroachBulkUpsertExecutor.class,
				BulkUpsertResolver.resolve(dialect));
		assertFalse(new com.sqlapp.data.db.dialect.postgres.bulk.PostgresBulkInsertProvider().supports(dialect));
	}

	@Test
	void preservesCompleteDefinitionThroughXmlWithoutDuplicatingComponents() throws Exception {
		var table = new Table("Odd Table").setSchemaName("Odd Schema");
		table.getColumns().add("id", c -> c.setDataType(DataType.BIGINT));
		table.getIndexes().add("extra", table.getColumns().get("id"));
		table.setDefinition(
				"CREATE TABLE \"Odd Schema\".\"Odd Table\" (id INT8 PRIMARY KEY USING HASH WITH BUCKET_COUNT=8)");
		Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
		var operations = new CockroachDB().createSqlFactoryRegistry().createSql(restored, SqlType.CREATE);
		assertEquals(1, operations.size());
		assertTrue(operations.get(0).getSqlText().contains("BUCKET_COUNT=8"));
	}

	@Test
	void generatesModelDdlAndRejectsKeylessMerge() {
		var table = new Table("items");
		table.getColumns().add("id", c -> c.setDataType(DataType.BIGINT));
		var registry = new CockroachDB().createSqlFactoryRegistry();
		assertTrue(registry.createSql(table, SqlType.CREATE).get(0).getSqlText().contains("CREATE TABLE"));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.MERGE));
	}

	@Test
	void propagatesRetryWithoutReplayingRowsAndClosesIterator() throws Exception {
		var table = new Table("items");
		table.getColumns().add("id", c -> c.setDataType(DataType.BIGINT));
		table.getConstraints().addPrimaryKeyConstraint("pk", table.getColumns().get("id"));
		var row = table.newRow();
		row.put("id", 1L);
		var closed = new java.util.concurrent.atomic.AtomicBoolean();
		class Rows implements java.util.Iterator<Row>, AutoCloseable {
			private boolean pending = true;

			public boolean hasNext() {
				return pending;
			}

			public Row next() {
				if (!pending)
					throw new java.util.NoSuchElementException();
				pending = false;
				return row;
			}

			public void close() {
				closed.set(true);
			}
		}
		table.setRowIteratorHandler(rows -> new Rows());
		var attempts = new java.util.concurrent.atomic.AtomicInteger();
		var rollbacks = new java.util.concurrent.atomic.AtomicInteger();
		var autoCommit = new java.util.concurrent.atomic.AtomicBoolean(true);
		var statement = (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
				new Class<?>[] { PreparedStatement.class }, (p, m, a) -> {
					if (m.getName().equals("executeBatch")) {
						attempts.incrementAndGet();
						throw new SQLException("restart transaction", "40001");
					}
					return null;
				});
		var connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
				new Class<?>[] { Connection.class }, (p, m, a) -> switch (m.getName()) {
				case "getAutoCommit" -> autoCommit.get();
				case "setAutoCommit" -> {
					autoCommit.set((Boolean) a[0]);
					yield null;
				}
				case "rollback" -> {
					rollbacks.incrementAndGet();
					yield null;
				}
				case "prepareStatement" -> statement;
				default -> throw new UnsupportedOperationException(m.getName());
				});
		assertEquals("40001", assertThrows(SQLException.class, () -> BulkUpsertResolver.resolve(new CockroachDB())
				.execute(connection, table, BulkUpsertOption.defaults())).getSQLState());
		assertEquals(1, attempts.get());
		assertEquals(1, rollbacks.get());
		assertTrue(autoCommit.get());
		assertTrue(closed.get());
	}

	@Test
	void generatesSeparateColumnActionsAndRejectsUnmodeledChanges() {
		var registry = new CockroachDB().createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		var table = new Table("items").setSchemaName("s");
		table.getColumns().add("label", c -> c.setDataType(DataType.VARCHAR));
		var target = table.clone();
		target.getColumns().get("label").setDefaultValue("'x'").setNotNull(true);
		var sql = registry.createSql(table.diff(target)).stream().map(op -> op.getSqlText()).toList();
		assertEquals(2, sql.size());
		assertTrue(sql.get(0).contains("SET DEFAULT"));
		assertTrue(sql.get(1).contains("COLUMN label SET NOT NULL"));
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(table
				.diff(table.clone().setDefinition("CREATE TABLE s.items (label STRING) LOCALITY REGIONAL BY ROW"))));
		var generated = table.clone();
		generated.getColumns().get("label").setFormula("upper(label)");
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(table.diff(generated)));
	}

	@Test
	void enumChangesPreserveOrderAndRejectRemoval() {
		var registry = new CockroachDB().createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		var before = new com.sqlapp.data.schemas.Type("mood").setSchemaName("s")
				.setDefinition("CREATE TYPE s.mood AS ENUM ('ok','done')");
		var after = before.clone().setDefinition("CREATE TYPE s.mood AS ENUM ('ok','a''b','done','日本語')");
		var sql = registry.createSql(before.diff(after)).stream().map(op -> op.getSqlText()).toList();
		assertEquals(2, sql.size());
		assertTrue(sql.get(0).contains("'a''b' BEFORE 'done'"));
		assertThrows(UnsupportedOperationException.class, () -> registry
				.createSql(before.diff(before.clone().setDefinition("CREATE TYPE s.mood AS ENUM ('ok')"))));
		assertThrows(UnsupportedOperationException.class, () -> registry
				.createSql(before.diff(before.clone().setDefinition("CREATE TYPE s.mood AS ENUM ('done','ok')"))));
	}

	@Test
	void sequenceOwnershipDiffEmitsOnlyAnExplicitQualifiedChange() throws Exception {
		var registry = new CockroachDB().createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		String definition = "CREATE SEQUENCE \"App Schema\".\"Owned Seq\" START 10 /* OWNED BY other.t.c */";
		var original = new Sequence("Owned Seq").setSchemaName("App Schema").setDefinition(definition);
		var target = original.clone().setDefinition(definition + " OWNED BY \"App Schema\".Items.\"Key\"\"Column\";");
		Sequence restored = SchemaUtils.readXml(new StringReader(target.asXml()));
		assertEquals("ALTER SEQUENCE \"App Schema\".\"Owned Seq\" OWNED BY \"App Schema\".\"items\".\"Key\"\"Column\"",
				registry.createSql(original.diff(restored)).getFirst().getSqlText());
		assertEquals("ALTER SEQUENCE \"App Schema\".\"Owned Seq\" OWNED BY NONE",
				registry.createSql(target.diff(target.clone().setDefinition(definition + " OWNED BY NONE"))).getFirst()
						.getSqlText());
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(target.diff(original)));
		assertThrows(UnsupportedOperationException.class, () -> registry
				.createSql(original.diff(original.clone().setDefinition(definition + " OWNED BY items.id"))));
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(
				original.diff(original.clone().setDefinition(definition + " OWNED BY items.id").setCacheSize(8))));
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(original.diff(
				target.clone().setDefinition(definition.replace("START 10", "START 20") + " OWNED BY s.items.id"))));
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target.clone().setCacheSize(8))));
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(
				original.diff(original.clone().setDefinition(definition + " OWNED BY s.items.id; DROP TABLE items"))));
	}

	@Test
	void sequenceCachePreservesExplicitScopeAndRejectsImplicitReset() throws Exception {
		var registry = new CockroachDB().createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		String definition = "CREATE SEQUENCE \"CACHE 900\".\"seq\" /* /* ignored */ CACHE 99 */ PER SESSION CACHE 8";
		var cache = CockroachSequenceOptions.cache(definition);
		assertEquals(8, cache.size());
		assertEquals(1, CockroachSequenceOptions
				.cache("CREATE SEQUENCE \"CACHE 900\"; COMMENT ON SEQUENCE seq IS 'CACHE 99'").size());
		assertNull(CockroachSequenceOptions.cache("CREATE SEQUENCE seq CACHE 2147483648").size());
		var original = new Sequence("seq").setSchemaName("CACHE 900").setDefinition(definition)
				.setCacheSize(cache.size());
		Sequence restored = SchemaUtils.readXml(new StringReader(original.asXml()));
		assertEquals(8, restored.getCacheSize());
		assertEquals(original.getDefinition(), restored.getDefinition());
		var modeled = new Sequence("seq").setCacheSize(8);
		assertTrue(registry.createSql(modeled, SqlType.CREATE).getFirst().getSqlText().endsWith(" CACHE 8"));
		assertTrue(registry.createSql(modeled.diff(modeled.clone().setCacheSize(32))).getFirst().getSqlText()
				.endsWith(" CACHE 32"));
		var target = original.clone().setCacheSize(32);
		assertEquals("ALTER SEQUENCE \"CACHE 900\".seq PER SESSION CACHE 32",
				registry.createSql(original.diff(target)).getFirst().getSqlText());
		assertTrue(registry.createSql(original.diff(original.clone().setCacheSize(1))).getFirst().getSqlText()
				.endsWith(" PER SESSION CACHE 1"));
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(original.diff(original.clone().setCacheSize(null))));
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(original.diff(original.clone().setCacheSize(0))));
		assertThrows(UnsupportedOperationException.class, () -> registry
				.createSql(original.diff(target.clone().setDefinition(definition.replace("PER SESSION", "PER NODE")))));
	}

	@Test
	void sequenceRoleOwnershipQuotesTheNamedRoleAsAnIdentifier() {
		var dialect = new CockroachDB();
		var sequence = new Sequence("Order Seq").setSchemaName("App Schema");
		var operation = dialect.createSequenceOwnerChangeSql(sequence, "Report \"Owners\"");
		assertEquals(SqlType.ALTER, operation.getSqlType());
		assertEquals("ALTER SEQUENCE \"App Schema\".\"Order Seq\" OWNER TO \"Report \"\"Owners\"\"\"",
				operation.getSqlText());
		assertTrue(dialect.createSequenceOwnerChangeSql(sequence, "CURRENT_USER").getSqlText()
				.endsWith(" OWNER TO \"CURRENT_USER\""));
		assertThrows(IllegalArgumentException.class, () -> dialect.createSequenceOwnerChangeSql(sequence, " "));
		assertThrows(IllegalArgumentException.class, () -> dialect.createSequenceOwnerChangeSql(sequence, "bad\0role"));
		assertThrows(IllegalArgumentException.class,
				() -> dialect.createSequenceOwnerChangeSql(sequence.clone().setSchemaName(null), "report"));
	}

	@Test
	void sequenceRestartRequiresAnExplicitNextValueWithinModeledBounds() {
		var dialect = new CockroachDB();
		var sequence = new Sequence("Mixed Seq").setSchemaName("App Schema")
				.setMinValue(java.math.BigInteger.valueOf(-100)).setMaxValue(java.math.BigInteger.valueOf(100))
				.setLastValue(java.math.BigInteger.TEN);
		assertEquals("ALTER SEQUENCE \"App Schema\".\"Mixed Seq\" RESTART WITH -100",
				dialect.createSequenceRestartSql(sequence, -100).getSqlText());
		assertEquals("ALTER SEQUENCE \"App Schema\".\"Mixed Seq\" RESTART WITH 100",
				dialect.createSequenceRestartSql(sequence, 100).getSqlText());
		assertThrows(IllegalArgumentException.class, () -> dialect.createSequenceRestartSql(sequence, -101));
		assertThrows(IllegalArgumentException.class, () -> dialect.createSequenceRestartSql(sequence, 101));
		assertThrows(IllegalArgumentException.class,
				() -> dialect.createSequenceRestartSql(sequence.clone().setSchemaName(null), 10));
		assertEquals(java.math.BigInteger.TEN, sequence.getLastValue());
	}

	@Test
	void sequenceMoveAlwaysQualifiesSourceAndRejectsCombinedChanges() {
		var registry = new CockroachDB().createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(false);
		var original = new Sequence("Order Seq").setCatalogName("db").setSchemaName("Old Schema")
				.setDefinition("CREATE SEQUENCE \"Old Schema\".\"Order Seq\" START 10");
		var target = original.clone().setSchemaName("New \"Schema\"");
		var operations = registry.createSql(original.diff(target));
		assertEquals(1, operations.size());
		assertEquals("ALTER SEQUENCE \"Old Schema\".\"Order Seq\" SET SCHEMA \"New \"\"Schema\"\"\"",
				operations.getFirst().getSqlText());
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target.clone().setCatalogName("other"))));
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target.clone().setName("renamed"))));
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target.clone().setCacheSize(4))));
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(original.diff(original.clone().setSchemaName(null))));
	}

	@Test
	void sequenceRenameUsesQualifiedOriginalAndUnqualifiedNewName() {
		var registry = new CockroachDB().createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		var original = new Sequence("Old Seq").setSchemaName("Mixed Schema")
				.setDefinition("CREATE SEQUENCE \"Mixed Schema\".\"Old Seq\" START 10");
		var target = original.clone().setName("New Seq");
		var operations = registry.createSql(original.diff(target));
		assertEquals(1, operations.size());
		assertEquals("ALTER SEQUENCE \"Mixed Schema\".\"Old Seq\" RENAME TO \"New Seq\"",
				operations.getFirst().getSqlText());
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target.clone().setSchemaName("other"))));
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target.clone().setIncrementBy(java.math.BigInteger.TWO))));
	}

	@Test
	void indexDropRequiresTableAndSequenceAlterNeverRestarts() {
		var registry = new CockroachDB().createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		var table = new Table("Mixed Table").setSchemaName("s");
		table.getColumns().add("id", c -> c.setDataType(DataType.BIGINT));
		table.getIndexes().add("by_id", table.getColumns().get("id"));
		assertTrue(registry.createSql(table.getIndexes().get("by_id"), SqlType.DROP).get(0).getSqlText()
				.contains("s.\"Mixed Table\"@by_id"));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(new Index("x"), SqlType.DROP));
		var sequence = new Sequence("seq").setSchemaName("s").setIncrementBy(java.math.BigInteger.ONE);
		var target = sequence.clone().setIncrementBy(java.math.BigInteger.valueOf(3));
		assertTrue(registry.createSql(sequence.diff(target)).get(0).getSqlText().contains("INCREMENT BY 3"));
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(sequence.diff(sequence.clone().setLastValue(java.math.BigInteger.TEN))));
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(sequence.diff(sequence.clone().setCycle(true))));
	}

	@Test
	void functionReplacementRetainsIdentityWithoutDrop() {
		var registry = new CockroachDB().createSqlFactoryRegistry();
		var function = new Function("inc").setSpecificName("inc(v INT8)").setSchemaName("s")
				.setDefinition("CREATE FUNCTION s.inc(v INT8) RETURNS INT8 LANGUAGE SQL AS $$SELECT v+1$$");
		var target = function.clone()
				.setDefinition("CREATE FUNCTION s.inc(v INT8) RETURNS INT8 LANGUAGE SQL AS $$SELECT v+2$$");
		assertTrue(
				registry.createSql(function.diff(target)).get(0).getSqlText().startsWith("CREATE OR REPLACE FUNCTION"));
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(function.diff(function.clone().setStatement("SELECT 42"))));
	}

	@Test
	void rejectsStaleIndexDefinitionAndMalformedEnumRatherThanIgnoringChanges() {
		var registry = new CockroachDB().createSqlFactoryRegistry();
		var table = new Table("items");
		table.getColumns().add("id", c -> c.setDataType(DataType.BIGINT));
		var index = table.getIndexes().add("by_id", table.getColumns().get("id"))
				.setDefinition("CREATE INDEX by_id ON items(id)");
		var target = index.clone().setWhere("id>0");
		table.getIndexes().remove("by_id");
		table.getIndexes().add(target);
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(index.diff(target)));
		var type = new com.sqlapp.data.schemas.Type("mood").setDefinition("CREATE TYPE mood AS ENUM ('ok')");
		assertThrows(UnsupportedOperationException.class, () -> registry
				.createSql(type.diff(type.clone().setDefinition("CREATE TYPE mood AS ENUM ('ok','new',)"))));
	}

	@Test
	void rejectsSchemaRenameThatWouldDropTheOriginalTable() {
		var registry = new CockroachDB().createSqlFactoryRegistry();
		var schema = new Schema("s");
		schema.setDialect(new CockroachDB());
		schema.getTables().add(new Table("old_name").setId("123"));
		schema.getTables().get("old_name").getColumns().add("id", c -> c.setDataType(DataType.BIGINT));
		var target = schema.clone();
		target.getTables().get("old_name").setName("new_name");
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(schema.diff(target)));
		assertTrue(registry.createSql(schema.getTables().get("old_name").diff(target.getTables().get("new_name")))
				.get(0).getSqlText().contains("RENAME TO"));
	}

	@Test
	void localitySpecificsOverrideOnlyTheOuterClause() {
		var registry = new CockroachDB().createSqlFactoryRegistry();
		var table = new Table("items").setDefinition(
				"CREATE TABLE items (id INT8, note STRING DEFAULT 'LOCALITY GLOBAL', \"LOCALITY\" STRING) LOCALITY GLOBAL; COMMENT ON TABLE items IS 'LOCALITY REGIONAL BY ROW'");
		table.getSpecifics().put("COCKROACH_LOCALITY", "REGIONAL BY TABLE IN \"us-east\"");
		String sql = registry.createSql(table, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("DEFAULT 'LOCALITY GLOBAL'"));
		assertTrue(sql.contains("LOCALITY REGIONAL BY TABLE IN \"us-east\"; COMMENT"));
		assertTrue(sql.endsWith("'LOCALITY REGIONAL BY ROW'"));
		assertNull(com.sqlapp.data.db.dialect.cockroach.util.CockroachPlacement
				.readLocality("CREATE TABLE locality (id INT8 DEFAULT 1)"));
		assertEquals("GLOBAL", com.sqlapp.data.db.dialect.cockroach.util.CockroachPlacement.readLocality(
				"CREATE TABLE t (note STRING DEFAULT $$LOCALITY REGIONAL BY ROW$$) LOCALITY GLOBAL; COMMENT ON TABLE t IS 'x'"));
		table.getSpecifics().put("COCKROACH_LOCALITY", "GLOBAL; DROP TABLE items");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
	}

	@Test
	void generatesModeledLocalityAndQuotesPlacementNames() {
		var registry = new CockroachDB().createSqlFactoryRegistry();
		var table = new Table("items");
		table.getColumns().add("id", c -> c.setDataType(DataType.BIGINT));
		table.getSpecifics().put("COCKROACH_LOCALITY", "REGIONAL BY ROW AS \"Region Column\"");
		assertTrue(registry.createSql(table, SqlType.CREATE).get(0).getSqlText()
				.contains("LOCALITY REGIONAL BY ROW AS \"Region Column\""));
		var catalog = new Catalog("Target\"DB");
		catalog.getSpecifics().put("COCKROACH_PRIMARY_REGION", "east\"1");
		catalog.getSpecifics().put("COCKROACH_REGIONS", "[\"east\\\"1\",\"west-2\"]");
		catalog.getSpecifics().put("COCKROACH_SECONDARY_REGION", "west-2");
		catalog.getSpecifics().put("COCKROACH_SURVIVAL_GOAL", "REGION");
		var sql = registry.createSql(catalog, SqlType.CREATE).stream().map(op -> op.getSqlText()).toList();
		assertEquals(4, sql.size());
		assertEquals("ALTER DATABASE \"Target\"\"DB\" PRIMARY REGION \"east\"\"1\"", sql.get(0));
		assertTrue(sql.get(1).contains("ADD REGION IF NOT EXISTS \"west-2\""));
		assertTrue(sql.get(2).contains("SECONDARY REGION"));
		assertTrue(sql.get(3).endsWith("SURVIVE REGION FAILURE"));
	}

	@Test
	void rejectsIncompleteAndInvalidDatabasePlacement() {
		var registry = new CockroachDB().createSqlFactoryRegistry();
		var catalog = new Catalog("db");
		catalog.getSpecifics().put("COCKROACH_REGIONS", "[\"east\"]");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(catalog, SqlType.CREATE));
		catalog.getSpecifics().put("COCKROACH_PRIMARY_REGION", "east");
		for (String regions : java.util.List.of("[]", "[1]", "[\"west\"]", "[\"east\",\"east\"]")) {
			catalog.getSpecifics().put("COCKROACH_REGIONS", regions);
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(catalog, SqlType.CREATE));
		}
		catalog.getSpecifics().put("COCKROACH_REGIONS", "[\"east\"]");
		catalog.getSpecifics().put("COCKROACH_SURVIVAL_GOAL", "NONE");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(catalog, SqlType.CREATE));
	}
}
