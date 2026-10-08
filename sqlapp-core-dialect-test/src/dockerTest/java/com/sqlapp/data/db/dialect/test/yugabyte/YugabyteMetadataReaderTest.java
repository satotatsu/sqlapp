/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.yugabyte;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.db.dialect.test.BulkMigrationTransactionAssertions;
import com.sqlapp.data.db.dialect.test.BulkMigrationLoadAssertions;
import com.sqlapp.data.db.dialect.test.BulkMigrationKeysetAssertions;
import com.sqlapp.data.db.dialect.test.BulkMigrationJobAssertions;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertDuplicateKeyStrategy;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationKeysetSource;
import com.sqlapp.jdbc.bulk.BulkMigrationVerifier;
import com.sqlapp.jdbc.bulk.BulkMigrationRepairExecutor;
import com.sqlapp.jdbc.bulk.BulkMigrationRepairOption;

/** Disposable local YSQL integration test, enabled only by the dockerTest task. */
class YugabyteMetadataReaderTest {
	private static final GenericContainer<?> YSQL = ReusableTestcontainers.configure(
			new GenericContainer<>(DockerImageName.parse(System.getProperty("sqlapp.test.yugabyte.image",
					"yugabytedb/yugabyte:2026.1.2.0-b137")))
					.withExposedPorts(5433)
					.withCommand("bin/yugabyted", "start", "--background=false")
					.waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3))));

	@BeforeAll
	static void startContainer() throws Exception {
		ReusableTestcontainers.start(YSQL);
		SQLException last = null;
		for (int attempt = 0; attempt < 60; attempt++) {
			try (var connection = connect(); var statement = connection.createStatement()) {
				statement.execute("SELECT 1");
				String version = connection.getMetaData().getDatabaseProductVersion();
				System.out.println("YSQL JDBC engine version: " + version);
				String expected = System.getProperty("sqlapp.test.yugabyte.expectedEngineMajor");
				if (expected != null) assertTrue(version.startsWith(expected + "."), version);
				return;
			} catch (SQLException e) {
				last = e;
				Thread.sleep(1000);
			}
		}
		throw new IllegalStateException("YSQL did not become ready", last);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(YSQL);
	}

	private static Connection connect() throws SQLException {
		return DriverManager.getConnection("jdbc:postgresql://" + YSQL.getHost() + ":"
				+ YSQL.getMappedPort(5433) + "/yugabyte?connectTimeout=5&socketTimeout=30", "yugabyte", "yugabyte");
	}

	@Test
	void readsAndRecreatesTableFromSchemaModel() throws Exception {
		String schemaName = "sqlapp_ysql_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			var dialect = DialectResolver.getInstance().getDialect(connection);
			assertEquals("YugabyteDB", dialect.getProductName());
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE TABLE " + schemaName
						+ ".source_table (id integer PRIMARY KEY, label varchar(40) NOT NULL)");
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName(schemaName);
				reader.setObjectName("source_table");
				Table table = reader.getAllFull(connection).stream()
						.filter(t -> "source_table".equals(t.getName())).findFirst().orElseThrow();
				assertNotNull(table.getColumns().get("id"));
				assertEquals(DataType.INT, table.getColumns().get("id").getDataType());
				assertEquals(2, table.getColumns().size());
				assertTrue(table.getColumns().get("label").isNotNull());
				assertNotNull(table.getConstraints().getPrimaryKeyConstraint());
				statement.execute("DROP TABLE " + schemaName + ".source_table");
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				SqlFactory<Table> factory = registry.getSqlFactory(table, SqlType.CREATE);
				for (var operation : factory.createSql(table)) {
					System.out.println(operation.getSqlText());
					statement.execute(operation.getSqlText());
				}
				statement.execute("INSERT INTO " + schemaName + ".source_table VALUES (1, 'roundtrip')");
				try (var rows = statement.executeQuery("SELECT label FROM " + schemaName + ".source_table WHERE id=1")) {
					assertTrue(rows.next());
					assertEquals("roundtrip", rows.getString(1));
				}
			} finally {
				statement.execute("DROP SCHEMA " + schemaName + " CASCADE");
			}
		}
	}
	@Test
	void recreatesRelationshipsViewAndSequence() throws Exception {
		String schemaName = "sqlapp_ysql_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			var dialect = DialectResolver.getInstance().getDialect(connection);
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE TABLE " + schemaName + ".parent (id integer PRIMARY KEY)");
				statement.execute("CREATE TABLE " + schemaName + ".child (id integer PRIMARY KEY, parent_id integer NOT NULL, label varchar(40), CONSTRAINT child_parent_fk FOREIGN KEY (parent_id) REFERENCES " + schemaName + ".parent(id))");
				statement.execute("CREATE UNIQUE INDEX child_label_idx ON " + schemaName + ".child(label)");
				statement.execute("CREATE VIEW " + schemaName + ".child_view AS SELECT id, label FROM " + schemaName + ".child");
				statement.execute("CREATE SEQUENCE " + schemaName + ".sample_seq START WITH 10 INCREMENT BY 3 MINVALUE 10 MAXVALUE 1000 CACHE 100");
				var schemaReader = dialect.getCatalogReader().getSchemaReader();
				var tableReader = schemaReader.getTableReader();
				tableReader.setSchemaName(schemaName);
				tableReader.setObjectName("child");
				Table child = tableReader.getAllFull(connection).stream().filter(t -> "child".equals(t.getName())).findFirst().orElseThrow();
				assertTrue(child.getConstraints().get("child_parent_fk") instanceof ForeignKeyConstraint);
				assertNotNull(child.getIndexes().get("child_label_idx"));
				var viewReader = schemaReader.getViewReader();
				viewReader.setSchemaName(schemaName);
				viewReader.setObjectName("child_view");
				Table view = viewReader.getAllFull(connection).stream().filter(t -> "child_view".equals(t.getName())).findFirst().orElseThrow();
				assertEquals(2, view.getColumns().size());
				assertTrue(String.join("\n", view.getStatement()).contains("child"));
				var sequenceReader = schemaReader.getSequenceReader();
				sequenceReader.setSchemaName(schemaName);
				sequenceReader.setObjectName("sample_seq");
				Sequence sequence = sequenceReader.getAllFull(connection).stream().filter(t -> "sample_seq".equals(t.getName())).findFirst().orElseThrow();
				assertEquals(3, sequence.getIncrementBy().intValueExact());
				assertEquals(10, sequence.getStartValue().intValueExact());
				assertEquals(10, sequence.getMinValue().intValueExact());
				assertEquals(1000, sequence.getMaxValue().intValueExact());
				assertFalse(sequence.isCycle());
				statement.execute("DROP VIEW " + schemaName + ".child_view");
				statement.execute("DROP TABLE " + schemaName + ".child");
				statement.execute("DROP SEQUENCE " + schemaName + ".sample_seq");
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				SqlFactory<Table> tableFactory = registry.getSqlFactory(child, SqlType.CREATE);
				for (var operation : tableFactory.createSql(child)) {
					statement.execute(operation.getSqlText());
				}
				SqlFactory<Table> viewFactory = registry.getSqlFactory(view, SqlType.CREATE);
				for (var operation : viewFactory.createSql(view)) {
					statement.execute(operation.getSqlText());
				}
				SqlFactory<Sequence> sequenceFactory = registry.getSqlFactory(sequence, SqlType.CREATE);
				for (var operation : sequenceFactory.createSql(sequence)) {
					statement.execute(operation.getSqlText());
				}
				statement.execute("INSERT INTO " + schemaName + ".parent VALUES (1)");
				statement.execute("INSERT INTO " + schemaName + ".child VALUES (1, 1, 'linked')");
				assertThrows(SQLException.class, () -> statement.execute("INSERT INTO " + schemaName + ".child VALUES (2, 999, 'orphan')"));
				assertThrows(SQLException.class, () -> statement.execute("INSERT INTO " + schemaName + ".child VALUES (3, 1, 'linked')"));
				try (var rows = statement.executeQuery("SELECT label FROM " + schemaName + ".child_view")) {
					assertTrue(rows.next());
					assertEquals("linked", rows.getString(1));
				}
				try (var rows = statement.executeQuery("SELECT nextval('" + schemaName + ".sample_seq'), nextval('" + schemaName + ".sample_seq')")) {
					assertTrue(rows.next());
					assertEquals(10, rows.getLong(1));
					assertEquals(13, rows.getLong(2));
				}
			} finally {
				statement.execute("DROP SCHEMA " + schemaName + " CASCADE");
			}
		}
	}

	@Test
	void migratesWithAtomicDatabaseCheckpointsAndResume() throws Exception {
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE public.ysql_atomic (code varchar(20) PRIMARY KEY, label text)");
			Table table = dataTable("ysql_atomic");
			BulkMigrationTransactionAssertions.assertDatabaseCheckpointAtomic(connection, table, "code", "label",
					"SELECT count(*) FROM public.ysql_atomic");
			statement.execute("TRUNCATE public.ysql_atomic");
			BulkMigrationTransactionAssertions.assertDatabaseCheckpointInsertAtomic(connection, table, "code", "label",
					"SELECT count(*) FROM public.ysql_atomic");
		}
	}

	@Test
	void sustainsChunkedUnicodeBinaryAndDuplicateLoad() throws Exception {
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE public.ysql_load (code varchar(20) PRIMARY KEY, content text, payload bytea)");
			BulkMigrationLoadAssertions.assertChunkedLobAndDuplicateLoad(connection,
					BulkMigrationLoadAssertions.table("public", "ysql_load", "code", "content", "payload",
							DataType.LONGVARCHAR, DataType.VARBINARY),
					"code", "content", "payload", "SELECT count(*) FROM public.ysql_load",
					"SELECT content, payload FROM public.ysql_load WHERE code = ?");
		}
	}

	@Test
	void honorsUpsertActionsDuplicatePoliciesAndCallerRollback() throws Exception {
		try (var connection = connect(); var observer = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE public.ysql_actions (code varchar(20) PRIMARY KEY, label text)");
			Table table = dataTable("ysql_actions");
			table.getRows().add(r -> { r.put("code", "A"); r.put("label", "initial"); });
			assertEquals(1, BulkInsertResolver.execute(connection, table, BulkOption.defaults()));
			table.getRows().clear();
			table.getRows().add(r -> { r.put("code", "A"); r.put("label", "changed"); });
			table.getRows().add(r -> { r.put("code", "B"); r.put("label", "new"); });
			assertEquals(1, BulkUpsertResolver.execute(connection, table,
					BulkUpsertOption.builder().insertWhenNotMatched(false).build()));
			assertEquals(1, BulkUpsertResolver.execute(connection, table,
					BulkUpsertOption.builder().updateWhenMatched(false).build()));
			connection.setAutoCommit(false);
			table.getRows().clear();
			table.getRows().add(r -> { r.put("code", "C"); r.put("label", "pending"); });
			BulkUpsertResolver.execute(connection, table, BulkUpsertOption.defaults());
			assertEquals(2, scalar(observer, "SELECT count(*) FROM public.ysql_actions"));
			connection.rollback();
			connection.setAutoCommit(true);
			assertEquals(2, scalar(connection, "SELECT count(*) FROM public.ysql_actions"));
			table.getRows().add(r -> { r.put("code", "C"); r.put("label", "last"); });
			assertThrows(IllegalArgumentException.class,
					() -> BulkUpsertResolver.execute(connection, table, BulkUpsertOption.defaults()));
			assertTrue(connection.getAutoCommit());
			assertEquals(2, scalar(connection, "SELECT count(*) FROM public.ysql_actions"));
			assertEquals(1, BulkUpsertResolver.execute(connection, table, BulkUpsertOption.builder()
					.duplicateKeyStrategy(BulkUpsertDuplicateKeyStrategy.KEEP_LAST).build()));
			try (var rows = statement.executeQuery("SELECT label FROM public.ysql_actions WHERE code='C'")) {
				assertTrue(rows.next());
				assertEquals("last", rows.getString(1));
			}
		}
	}

	@Test
	void repairsJdbcKeysetMismatchAndFencesLeaseOwners() throws Exception {
		try (var connection = connect(); var second = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE public.ysql_expected (code varchar(20) PRIMARY KEY, label text)");
			statement.execute("CREATE TABLE public.ysql_actual (code varchar(20) PRIMARY KEY, label text)");
			statement.execute("INSERT INTO public.ysql_expected VALUES ('A','one'),('B','two'),('C','three')");
			statement.execute("INSERT INTO public.ysql_actual VALUES ('A','one'),('B','wrong'),('C','three')");
			var expected = new JdbcBulkMigrationKeysetSource(connection, dataTable("ysql_expected"));
			var actual = new JdbcBulkMigrationKeysetSource(connection, dataTable("ysql_actual"));
			var verification = BulkMigrationVerifier.verify(expected, actual, List.of("code", "label"), 1);
			assertEquals(1, verification.getMismatches().size());
			assertEquals(1, BulkMigrationRepairExecutor.execute(connection, expected, dataTable("ysql_actual"),
					verification, BulkMigrationRepairOption.defaults()).getReplayedRows());
			assertTrue(BulkMigrationVerifier.verify(expected, actual, List.of("code", "label"), 1).isMatch());
			BulkMigrationJobAssertions.assertJdbcLeaseOwnerFencing(connection, second);
		}
	}

	@Test
	void recreatesQuotedIdentifiersAndCommonTypes() throws Exception {
		String schema = "Ysql_" + UUID.randomUUID().toString().replace("-", "");
		String qualified = "\"" + schema + "\".\"Order\"";
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA \"" + schema + "\"");
			try {
				statement.execute("CREATE TABLE " + qualified + " (\"Id\" bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, "
						+ "\"Amount\" numeric(18,4) NOT NULL CHECK (\"Amount\" >= 0), active boolean DEFAULT true, "
						+ "day date, moment timestamp, zoned timestamptz, uid uuid, doc jsonb, bytes bytea, tags text[], note text)");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName(schema);
				reader.setObjectName("Order");
				Table table = reader.getAllFull(connection).stream().filter(t -> "Order".equals(t.getName())).findFirst().orElseThrow();
				assertTrue(table.getColumns().get("Id").isIdentity());
				assertEquals(11, table.getColumns().size());
				assertNotNull(table.getColumns().get("active").getDefaultValue());
				statement.execute("DROP TABLE " + qualified);
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				registry.getOptions().setQuateObjectName(true);
				registry.getOptions().setQuateColumnName(true);
				SqlFactory<Table> factory = registry.getSqlFactory(table, SqlType.CREATE);
				for (var operation : factory.createSql(table)) {
					System.out.println(operation.getSqlText());
					statement.execute(operation.getSqlText());
				}
				statement.execute("INSERT INTO " + qualified + " (\"Amount\", day, moment, zoned, uid, doc, bytes, tags, note) "
						+ "VALUES (1234567890.1234, DATE '2026-10-08', TIMESTAMP '2026-10-08 12:34:56', "
						+ "TIMESTAMPTZ '2026-10-08 12:34:56+09', '123e4567-e89b-12d3-a456-426614174000', "
						+ "'{\"name\":\"日本語\"}', decode('00ff','hex'), ARRAY['one','two,three'], '日本語')");
				try (var rows = statement.executeQuery("SELECT * FROM " + qualified)) {
					assertTrue(rows.next());
					assertTrue(rows.getLong("Id") > 0);
					assertEquals(new BigDecimal("1234567890.1234"), rows.getBigDecimal("Amount"));
					assertTrue(rows.getBoolean("active"));
					assertEquals(java.time.LocalDate.of(2026, 10, 8), rows.getObject("day", java.time.LocalDate.class));
					assertEquals(java.time.LocalDateTime.of(2026, 10, 8, 12, 34, 56), rows.getObject("moment", java.time.LocalDateTime.class));
					assertEquals(java.time.Instant.parse("2026-10-08T03:34:56Z"), rows.getObject("zoned", java.time.OffsetDateTime.class).toInstant());
					assertEquals("123e4567-e89b-12d3-a456-426614174000", rows.getObject("uid").toString());
					assertTrue(rows.getString("doc").contains("日本語"));
					assertArrayEquals(new byte[] {0, (byte)255}, rows.getBytes("bytes"));
					assertArrayEquals(new String[] {"one", "two,three"}, (String[]) rows.getArray("tags").getArray());
					assertEquals("日本語", rows.getString("note"));
				}
				assertThrows(SQLException.class, () -> statement.execute("INSERT INTO " + qualified + " (\"Amount\") VALUES (-1)"));
			} finally {
				statement.execute("DROP SCHEMA \"" + schema + "\" CASCADE");
			}
		}
	}

	@Test
	void preservesDmlSavepointsAndFailureSqlState() throws Exception {
		try (var connection = connect(); var observer = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE public.ysql_savepoint (id integer PRIMARY KEY)");
			connection.setAutoCommit(false);
			statement.execute("INSERT INTO public.ysql_savepoint VALUES (1)");
			var savepoint = connection.setSavepoint();
			SQLException error = assertThrows(SQLException.class,
					() -> statement.execute("INSERT INTO public.ysql_savepoint VALUES (1)"));
			assertEquals("23505", error.getSQLState());
			connection.rollback(savepoint);
			statement.execute("INSERT INTO public.ysql_savepoint VALUES (2)");
			assertEquals(0, scalar(observer, "SELECT count(*) FROM public.ysql_savepoint"));
			connection.commit();
			assertEquals(2, scalar(observer, "SELECT count(*) FROM public.ysql_savepoint"));
			statement.execute("INSERT INTO public.ysql_savepoint VALUES (3)");
			connection.rollback();
			assertEquals(2, scalar(observer, "SELECT count(*) FROM public.ysql_savepoint"));
		}
	}

	private static Table dataTable(String name) {
		Table table = new Table(name).setSchemaName("public");
		table.getColumns().add("code", c -> c.setDataType(DataType.VARCHAR).setLength(20).setNotNull(true));
		table.getColumns().add("label", c -> c.setDataType(DataType.LONGVARCHAR));
		table.setPrimaryKey(name + "_pkey", table.getColumns().get("code"));
		return table;
	}

	private static int scalar(Connection connection, String sql) throws SQLException {
		try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
			assertTrue(rows.next());
			return rows.getInt(1);
		}
	}

	@Test
	void verifiesPostgresCopyAcrossYsqlTransactionBatchBoundary() throws Exception {
		try (var connection = connect(); var observer = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE public.ysql_native_copy (code varchar(20) PRIMARY KEY, label text)");
			Table table = dataTable("ysql_native_copy");
			for (int i = 0; i < 20_005; i++) {
				final int id = i;
				table.getRows().add(r -> { r.put("code", "C" + id); r.put("label", "日本語,\"quoted\"\nline"); });
			}
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var copy = new com.sqlapp.data.db.dialect.postgres.bulk.PostgresBulkInsertExecutor(dialect);
			connection.setAutoCommit(false);
			assertEquals(20_005, copy.execute(connection, table, BulkOption.defaults()));
			assertEquals(0, scalar(observer, "SELECT count(*) FROM public.ysql_native_copy"));
			connection.rollback();
			assertEquals(0, scalar(observer, "SELECT count(*) FROM public.ysql_native_copy"));
			table.getRows().add(r -> { r.put("code", "C0"); r.put("label", "duplicate-after-20000"); });
			assertThrows(SQLException.class, () -> copy.execute(connection, table, BulkOption.defaults()));
			connection.rollback();
			assertEquals(0, scalar(observer, "SELECT count(*) FROM public.ysql_native_copy"));
			assertFalse(connection.getAutoCommit());
			connection.setAutoCommit(true);
			assertThrows(SQLException.class, () -> BulkInsertResolver.execute(connection, table, BulkOption.defaults()));
			assertTrue(connection.getAutoCommit());
			assertEquals(0, scalar(observer, "SELECT count(*) FROM public.ysql_native_copy"));
		}
	}

	@Test
	void verifiesPostgresStagingUpsertCallerTransactionAndCleanup() throws Exception {
		try (var connection = connect(); var observer = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE public.ysql_native_upsert (code varchar(20) PRIMARY KEY, label text)");
			Table table = dataTable("ysql_native_upsert");
			table.getRows().add(r -> { r.put("code", "A"); r.put("label", "native"); });
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var upsert = new com.sqlapp.data.db.dialect.postgres.bulk.PostgresBulkUpsertExecutor(dialect);
			assertEquals(1, upsert.execute(connection, table, BulkUpsertOption.defaults()));
			assertTrue(connection.getAutoCommit());
			connection.setAutoCommit(false);
			table.getRows().clear();
			table.getRows().add(r -> { r.put("code", "B"); r.put("label", "rollback"); });
			assertEquals(1, upsert.execute(connection, table, BulkUpsertOption.defaults()));
			assertEquals(1, scalar(observer, "SELECT count(*) FROM public.ysql_native_upsert"));
			connection.rollback();
			assertEquals(1, scalar(observer, "SELECT count(*) FROM public.ysql_native_upsert"));
			assertEquals(0, scalar(connection,
					"SELECT count(*) FROM pg_class WHERE relname LIKE 'sqlapp_upsert_%' AND relnamespace=pg_my_temp_schema()"));
		}
	}

	@Test
	void readsWholeSchemaAndRecreatesFunctionAndTrigger() throws Exception {
		String schemaName = "ysql_objects_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE TYPE " + schemaName + ".status AS ENUM ('new', 'done')");
				statement.execute("CREATE DOMAIN " + schemaName + ".positive AS integer CHECK (VALUE > 0)");
				statement.execute("CREATE TABLE " + schemaName + ".items (id integer PRIMARY KEY, label text)");
				statement.execute("CREATE FUNCTION " + schemaName + ".double_value(p_value integer) RETURNS integer LANGUAGE sql IMMUTABLE AS $$ SELECT p_value * 2 $$");
				statement.execute("CREATE FUNCTION " + schemaName + ".mark_label() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN NEW.label := upper(NEW.label); RETURN NEW; END $$");
				statement.execute("CREATE TRIGGER mark_label BEFORE INSERT ON " + schemaName + ".items FOR EACH ROW EXECUTE PROCEDURE " + schemaName + ".mark_label()");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader();
				reader.setSchemaName(schemaName);
				var schema = reader.getAllFull(connection).stream().filter(v -> schemaName.equals(v.getName())).findFirst().orElseThrow();
				assertNotNull(schema.getTables().get("items"));
				assertNotNull(schema.getDomains().get("positive"));
				assertNotNull(schema.getDomains().get("status"));
				assertEquals(List.of("new", "done"), List.copyOf(schema.getDomains().get("status").getValues()));
				var function = schema.getFunctions().get("double_value");
				assertNotNull(function);
				assertEquals(1, function.getArguments().size());
				var trigger = schema.getTriggers().get("mark_label");
				assertNotNull(trigger);
				statement.execute("DROP TRIGGER mark_label ON " + schemaName + ".items");
				statement.execute("DROP FUNCTION " + schemaName + ".double_value(integer)");
				statement.execute("DROP TYPE " + schemaName + ".status");
				statement.execute("DROP DOMAIN " + schemaName + ".positive");
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				SqlFactory<com.sqlapp.data.schemas.Function> functionFactory = registry.getSqlFactory(function, SqlType.CREATE);
				for (var op : functionFactory.createSql(function)) statement.execute(op.getSqlText());
				SqlFactory<com.sqlapp.data.schemas.Trigger> triggerFactory = registry.getSqlFactory(trigger, SqlType.CREATE);
				for (var op : triggerFactory.createSql(trigger)) statement.execute(op.getSqlText());
				for (String name : List.of("status", "positive")) {
					var domain = schema.getDomains().get(name);
					SqlFactory<com.sqlapp.data.schemas.Domain> domainFactory = registry.getSqlFactory(domain, SqlType.CREATE);
					var operations = domainFactory.createSql(domain);
					assertFalse(operations.isEmpty(), "Missing CREATE factory for " + name);
					for (var op : operations) statement.execute(op.getSqlText());
				}
				assertEquals(1, scalar(connection, "SELECT 1 WHERE 'done'::" + schemaName + ".status = 'done'"));
				assertThrows(SQLException.class, () -> statement.execute("SELECT (-1)::" + schemaName + ".positive"));
				assertEquals(14, scalar(connection, "SELECT " + schemaName + ".double_value(7)"));
				statement.execute("INSERT INTO " + schemaName + ".items VALUES (1,'marked')");
				try (var rows = statement.executeQuery("SELECT label FROM " + schemaName + ".items")) {
					assertTrue(rows.next());
					assertEquals("MARKED", rows.getString(1));
				}
			} finally {
				statement.execute("DROP SCHEMA " + schemaName + " CASCADE");
			}
		}
	}

	@Test
	void copiesAndUpsertsIdentityUnicodeNullEmptyDecimalBinaryAndArray() throws Exception {
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE public.ysql_copy_types (id bigint GENERATED BY DEFAULT AS IDENTITY UNIQUE, "
					+ "code text PRIMARY KEY, label text, amount numeric(18,4), payload bytea, tags text[])");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("public");
			reader.setObjectName("ysql_copy_types");
			Table table = reader.getAllFull(connection).stream().filter(t -> "ysql_copy_types".equals(t.getName())).findFirst().orElseThrow();
			table.getRows().add(r -> {
				r.put("code", "A"); r.put("label", "日本語,\"quoted\"\nline");
				r.put("amount", new BigDecimal("1234567890.1234"));
				r.put("payload", new byte[] {0, (byte)255}); r.put("tags", new String[] {"one", "two,three"});
			});
			table.getRows().add(r -> { r.put("code", "B"); r.put("label", null); });
			table.getRows().add(r -> { r.put("code", "C"); r.put("label", ""); });
			assertEquals(3, BulkInsertResolver.execute(connection, table, BulkOption.defaults()));
			long identity;
			try (var rows = statement.executeQuery("SELECT * FROM public.ysql_copy_types ORDER BY code")) {
				assertTrue(rows.next()); identity = rows.getLong("id"); assertTrue(identity > 0);
				assertEquals("日本語,\"quoted\"\nline", rows.getString("label"));
				assertEquals(new BigDecimal("1234567890.1234"), rows.getBigDecimal("amount"));
				assertArrayEquals(new byte[] {0, (byte)255}, rows.getBytes("payload"));
				assertArrayEquals(new String[] {"one", "two,three"}, (String[]) rows.getArray("tags").getArray());
				assertTrue(rows.next()); assertNull(rows.getString("label"));
				assertTrue(rows.next()); assertEquals("", rows.getString("label"));
			}
			table.getRows().clear();
			table.getRows().add(r -> { r.put("code", "A"); r.put("label", "updated"); });
			assertEquals(1, BulkUpsertResolver.execute(connection, table, BulkUpsertOption.defaults()));
			try (var rows = statement.executeQuery("SELECT id, label FROM public.ysql_copy_types WHERE code='A'")) {
				assertTrue(rows.next()); assertEquals(identity, rows.getLong(1)); assertEquals("updated", rows.getString(2));
			}
			table.getRows().clear();
			table.getRows().add(r -> { r.put("id", 1000000L); r.put("code", "D"); r.put("label", "explicit"); });
			assertEquals(1, BulkInsertResolver.execute(connection, table, BulkOption.builder().keepIdentity(true).build()));
			assertEquals(1, scalar(connection, "SELECT count(*) FROM public.ysql_copy_types WHERE id=1000000"));
		}
	}

	@Test
	void propagatesSnapshotWriteConflictWithoutReplayingCallerTransaction() throws Exception {
		try (var first = connect(); var second = connect(); var statement = first.createStatement()) {
			statement.execute("CREATE TABLE public.ysql_conflict (id integer PRIMARY KEY, value integer)");
			statement.execute("INSERT INTO public.ysql_conflict VALUES (1,0)");
			first.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
			second.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
			first.setAutoCommit(false);
			second.setAutoCommit(false);
			assertEquals(0, scalar(first, "SELECT value FROM public.ysql_conflict WHERE id=1"));
			assertEquals(0, scalar(second, "SELECT value FROM public.ysql_conflict WHERE id=1"));
			statement.execute("UPDATE public.ysql_conflict SET value=1 WHERE id=1");
			first.commit();
			Table table = new Table("ysql_conflict").setSchemaName("public");
			table.getColumns().add("id", c -> c.setDataType(DataType.INT));
			table.getColumns().add("value", c -> c.setDataType(DataType.INT));
			table.setPrimaryKey("ysql_conflict_pkey", table.getColumns().get("id"));
			table.getRows().add(r -> { r.put("id", 1); r.put("value", 2); });
			SQLException conflict = assertThrows(SQLException.class,
					() -> BulkUpsertResolver.execute(second, table, BulkUpsertOption.defaults()));
			assertEquals("40001", conflict.getSQLState());
			assertFalse(second.getAutoCommit());
			second.rollback();
			assertEquals(1, scalar(second, "SELECT value FROM public.ysql_conflict WHERE id=1"));
			second.rollback();
		}
	}

}
