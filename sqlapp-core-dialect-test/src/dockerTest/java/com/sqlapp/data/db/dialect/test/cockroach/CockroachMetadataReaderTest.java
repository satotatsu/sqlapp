/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.cockroach;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.io.StringReader;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import com.sqlapp.data.db.dialect.*;
import com.sqlapp.data.db.dialect.cockroach.CockroachDB;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.bulk.*;

/**
 * Owned disposable container only; no external credentials or host data
 * volumes.
 */
class CockroachMetadataReaderTest {
	private static final GenericContainer<?> DB = new GenericContainer<>(
			DockerImageName.parse(System.getProperty("sqlapp.test.cockroach.image", "cockroachdb/cockroach:v25.4.0")))
			.withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("/cockroach/cockroach")).withExposedPorts(26257)
			.withCommand("start-single-node", "--insecure", "--listen-addr=0.0.0.0:26257",
					"--locality=region=sqlapp-region,zone=sqlapp-zone", "--store=type=mem,size=0.25", "--cache=64MiB",
					"--max-sql-memory=512MiB")
			.waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));

	@BeforeAll
	static void start() throws Exception {
		DB.start();
		try (var c = connect(); var sql = c.createStatement(); var rows = sql.executeQuery("SELECT version()")) {
			assertTrue(rows.next());
			System.out.println(rows.getString(1));
			String expected = System.getProperty("sqlapp.test.cockroach.expectedVersion");
			if (expected != null)
				assertTrue(rows.getString(1).contains(expected));
		}
	}

	@AfterAll
	static void stop() {
		DB.stop();
	}

	private static Connection connect() throws SQLException {
		return DriverManager.getConnection("jdbc:postgresql://" + DB.getHost() + ":" + DB.getMappedPort(26257)
				+ "/defaultdb?sslmode=disable&connectTimeout=5&socketTimeout=120", "root", "");
	}

	private String schema() {
		return "sqlapp_crdb_" + UUID.randomUUID().toString().replace("-", "");
	}

	private Schema read(Connection c, String name) {
		var reader = DialectResolver.getInstance().getDialect(c).getCatalogReader().getSchemaReader();
		reader.setSchemaName(name);
		return reader.getAllFull(c).stream().filter(s -> name.equals(s.getName())).findFirst().orElseThrow();
	}

	private void create(Connection c, Object object) throws Exception {
		var registry = new CockroachDB().createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		try (var sql = c.createStatement()) {
			for (var op : registry.createSql(object, SqlType.CREATE)) {
				sql.execute(op.getSqlText());
			}
		}
	}

	@Test
	void identifiesCockroachThroughPostgresDriver() throws Exception {
		try (var c = connect()) {
			assertInstanceOf(CockroachDB.class, DialectResolver.getInstance().getDialect(c));
		}
	}

	@Test
	void roundTripsSchemaConstraintsIndexesViewAndSequence() throws Exception {
		String name = schema();
		try (var c = connect(); var sql = c.createStatement()) {
			sql.execute("CREATE SCHEMA " + name);
			try {
				sql.execute("CREATE SEQUENCE " + name + ".seq START 10 INCREMENT 3");
				sql.execute("CREATE TABLE " + name
						+ ".parent (id INT8 PRIMARY KEY, label STRING NOT NULL UNIQUE, balance DECIMAL(20,4) DEFAULT 0, CONSTRAINT positive CHECK (balance>=0))");
				sql.execute("CREATE TABLE " + name + ".child (id INT8 PRIMARY KEY DEFAULT nextval('" + name
						+ ".seq'), parent_id INT8 REFERENCES " + name
						+ ".parent(id), payload BYTES, tags STRING[], meta JSONB)");
				sql.execute("CREATE INDEX child_parent ON " + name
						+ ".child (parent_id DESC) STORING (payload) WHERE parent_id IS NOT NULL");
				sql.execute("COMMENT ON TABLE " + name + ".child IS 'child note'");
				sql.execute("CREATE VIEW " + name + ".labels AS SELECT id,label FROM " + name + ".parent");
				var original = read(c, name);
				assertEquals(2, original.getTables().size());
				assertNotNull(original.getTables().get("parent").getPrimaryKeyConstraint());
				assertEquals(1, original.getTables().get("child").getConstraints().getForeignKeyConstraints().size());
				Schema restored = SchemaUtils.readXml(new StringReader(original.asXml()));
				sql.execute("DROP SCHEMA " + name + " CASCADE");
				create(c, restored);
				sql.execute("INSERT INTO " + name + ".parent(id,label) VALUES (1,'ok')");
				sql.execute("INSERT INTO " + name
						+ ".child(parent_id,payload,tags,meta) VALUES (1,decode('00ff','hex'),ARRAY['a','日本語'],'{\"ok\":true}')");
				try (var rows = sql.executeQuery("SELECT id FROM " + name + ".child")) {
					assertTrue(rows.next());
					assertEquals(10, rows.getLong(1));
				}
				var actual = read(c, name);
				assertEquals(original.getTables().get("child").getDefinition(),
						actual.getTables().get("child").getDefinition());
			} finally {
				if (!c.isClosed())
					sql.execute("DROP SCHEMA IF EXISTS " + name + " CASCADE");
			}
		}
	}

	@Test
	void preservesHashShardingComputedHiddenColumnsAndFamilies() throws Exception {
		String name = schema();
		try (var c = connect(); var sql = c.createStatement()) {
			sql.execute("CREATE SCHEMA " + name);
			try {
				sql.execute("CREATE TABLE " + name
						+ ".items (id INT8 PRIMARY KEY USING HASH WITH BUCKET_COUNT=8, label STRING, upper_label STRING AS (upper(label)) STORED, FAMILY keys (id), FAMILY data (label,upper_label))");
				var original = read(c, name).getTables().get("items");
				Table restored = SchemaUtils.readXml(new StringReader(original.asXml()));
				sql.execute("DROP TABLE " + name + ".items");
				create(c, restored);
				assertEquals(original.getDefinition(), read(c, name).getTables().get("items").getDefinition());
				sql.execute("INSERT INTO " + name + ".items(id,label) VALUES (1,'hello')");
				try (var rows = sql.executeQuery("SELECT upper_label FROM " + name + ".items")) {
					assertTrue(rows.next());
					assertEquals("HELLO", rows.getString(1));
				}
			} finally {
				if (!c.isClosed())
					sql.execute("DROP SCHEMA IF EXISTS " + name + " CASCADE");
			}
		}
	}

	@Test
	void copyAndUpsertRespectCallerRollbackAndFailure() throws Exception {
		String name = schema();
		try (var c = connect(); var sql = c.createStatement()) {
			sql.execute("CREATE SCHEMA " + name);
			try {
				sql.execute("CREATE TABLE " + name + ".items (id INT8 PRIMARY KEY,label STRING NOT NULL)");
				var table = read(c, name).getTables().get("items");
				table.getRows().add(r -> {
					r.put("id", 1L);
					r.put("label", "日本語\n\"quoted\"");
				});
				assertEquals(1, BulkInsertResolver.resolve(new CockroachDB()).execute(c, table, BulkOption.defaults()));
				table.getRows().clear();
				table.getRows().add(r -> {
					r.put("id", 1L);
					r.put("label", "updated");
				});
				table.getRows().add(r -> {
					r.put("id", 2L);
					r.put("label", "new");
				});
				assertEquals(2,
						BulkUpsertResolver.resolve(new CockroachDB()).execute(c, table, BulkUpsertOption.defaults()));
				c.setAutoCommit(false);
				table.getRows().clear();
				table.getRows().add(r -> {
					r.put("id", 3L);
					r.put("label", "rollback");
				});
				assertEquals(1, BulkInsertResolver.resolve(new CockroachDB()).execute(c, table, BulkOption.defaults()));
				c.rollback();
				c.setAutoCommit(true);
				try (var rows = sql.executeQuery("SELECT count(*) FROM " + name + ".items")) {
					assertTrue(rows.next());
					assertEquals(2, rows.getInt(1));
				}
			} finally {
				if (!c.getAutoCommit()) {
					c.rollback();
					c.setAutoCommit(true);
				}
				sql.execute("DROP SCHEMA IF EXISTS " + name + " CASCADE");
			}
		}
	}

	@FunctionalInterface
	private interface Work {
		void run(Connection c, Statement sql, String schema) throws Exception;
	}

	private void withSchema(Work work) throws Exception {
		String name = schema();
		try (var c = connect(); var sql = c.createStatement()) {
			sql.execute("CREATE SCHEMA " + name);
			try {
				work.run(c, sql, name);
			} finally {
				if (!c.isClosed()) {
					if (!c.getAutoCommit()) {
						c.rollback();
						c.setAutoCommit(true);
					}
					sql.execute("DROP SCHEMA IF EXISTS " + name + " CASCADE");
				}
			}
		}
	}

	private int count(Statement sql, String table) throws SQLException {
		try (var rows = sql.executeQuery("SELECT count(*) FROM " + table)) {
			assertTrue(rows.next());
			return rows.getInt(1);
		}
	}

	@Test
	void recreatesStandaloneIndexWithStoringAndPredicate() throws Exception {
		withSchema((c, sql, name) -> {
			sql.execute("CREATE TABLE " + name
					+ ".items (id INT8 PRIMARY KEY,label STRING,payload BYTES, INDEX by_label (label DESC) STORING (payload) WHERE label IS NOT NULL)");
			var table = read(c, name).getTables().get("items");
			var index = table.getIndexes().get("by_label");
			assertNotNull(index);
			Index restored = SchemaUtils.readXml(new StringReader(index.asXml()));
			table.getIndexes().clear();
			table.getIndexes().add(restored);
			sql.execute("DROP INDEX " + name + ".items@by_label");
			create(c, restored);
			assertEquals(index.getDefinition(),
					read(c, name).getTables().get("items").getIndexes().get("by_label").getDefinition());
		});
	}

	@Test
	void recreatesModeledTableWithoutDuplicatingConstraintIndexes() throws Exception {
		withSchema((c, sql, name) -> {
			sql.execute("CREATE TABLE " + name + ".items (id INT8 PRIMARY KEY,label STRING NOT NULL UNIQUE)");
			var table = read(c, name).getTables().get("items");
			table.setDefinition((String) null);
			sql.execute("DROP TABLE " + name + ".items");
			create(c, table);
			sql.execute("INSERT INTO " + name + ".items VALUES (1,'ok')");
			assertEquals("23505", assertThrows(SQLException.class,
					() -> sql.execute("INSERT INTO " + name + ".items VALUES (2,'ok')")).getSQLState());
			assertNotNull(read(c, name).getTables().get("items").getPrimaryKeyConstraint());
		});
	}

	@Test
	void preservesEnumsAndQuotedIdentifiers() throws Exception {
		withSchema((c, sql, name) -> {
			sql.execute("CREATE TYPE " + name + ".mood AS ENUM ('ok','日本語','a''b')");
			sql.execute("CREATE TABLE " + name + ".\"Mixed Case\" (\"select\" INT8 PRIMARY KEY, state " + name
					+ ".mood NOT NULL)");
			var model = read(c, name);
			assertEquals(1, model.getTypes().size());
			Schema restored = SchemaUtils.readXml(new StringReader(model.asXml()));
			sql.execute("DROP SCHEMA " + name + " CASCADE");
			create(c, restored);
			sql.execute("INSERT INTO " + name + ".\"Mixed Case\" VALUES (1,'日本語')");
			assertEquals(1, count(sql, name + ".\"Mixed Case\""));
		});
	}

	@Test
	void recreatesOverloadedSqlAndPlpgsqlFunctions() throws Exception {
		withSchema((c, sql, name) -> {
			sql.execute(
					"CREATE FUNCTION " + name + ".inc(v INT8) RETURNS INT8 LANGUAGE SQL IMMUTABLE AS $$SELECT v+1$$");
			sql.execute(
					"CREATE FUNCTION " + name + ".inc(v STRING) RETURNS STRING LANGUAGE SQL AS $$SELECT v || '!'$$");
			sql.execute("CREATE FUNCTION " + name
					+ ".twice(v INT8 DEFAULT 3) RETURNS INT8 LANGUAGE PLpgSQL AS $$BEGIN RETURN v*2; END$$");
			var model = read(c, name);
			assertEquals(3, model.getFunctions().size());
			Schema restored = SchemaUtils.readXml(new StringReader(model.asXml()));
			sql.execute("DROP SCHEMA " + name + " CASCADE");
			create(c, restored);
			assertEquals(model.getFunctions().stream().map(Function::getSpecificName).sorted().toList(),
					read(c, name).getFunctions().stream().map(Function::getSpecificName).sorted().toList());
			try (var rows = sql.executeQuery(
					"SELECT " + name + ".inc(5::INT8)," + name + ".inc('a'::STRING)," + name + ".twice()")) {
				assertTrue(rows.next());
				assertEquals(6, rows.getInt(1));
				assertEquals("a!", rows.getString(2));
				assertEquals(6, rows.getInt(3));
			}
		});
	}

	@Test
	void copiesTypedUnicodeBinaryJsonArraysAndNumbers() throws Exception {
		withSchema((c, sql, name) -> {
			sql.execute("CREATE TABLE " + name
					+ ".items (id UUID PRIMARY KEY, label STRING, payload BYTES, amount DECIMAL(20,4), meta JSONB, tags STRING[])");
			var table = read(c, name).getTables().get("items");
			var id = UUID.randomUUID();
			table.getRows().add(r -> {
				r.put("id", id);
				r.put("label", "日本語\n\"quoted\"");
				r.put("payload", new byte[] { 0, (byte) 255 });
				r.put("amount", new java.math.BigDecimal("123.4500"));
				r.put("meta", "{\"ok\":true}");
				r.put("tags", new String[] { "a", "日本語" });
			});
			assertEquals(1, BulkInsertResolver.resolve(new CockroachDB()).execute(c, table, BulkOption.defaults()));
			assertEquals(1,
					BulkUpsertResolver.resolve(new CockroachDB()).execute(c, table, BulkUpsertOption.defaults()));
			try (var rows = sql
					.executeQuery("SELECT id,label,payload,amount,meta->>'ok',tags FROM " + name + ".items")) {
				assertTrue(rows.next());
				assertEquals(id, rows.getObject(1));
				assertEquals("日本語\n\"quoted\"", rows.getString(2));
				assertArrayEquals(new byte[] { 0, (byte) 255 }, rows.getBytes(3));
				assertEquals(0, new java.math.BigDecimal("123.4500").compareTo(rows.getBigDecimal(4)));
				assertEquals("true", rows.getString(5));
				assertArrayEquals(new String[] { "a", "日本語" }, (Object[]) rows.getArray(6).getArray());
			}
		});
	}

	@Test
	void rollsBackFailedCopyAndRestoresAutoCommit() throws Exception {
		withSchema((c, sql, name) -> {
			sql.execute("CREATE TABLE " + name + ".items (id INT8 PRIMARY KEY,label STRING NOT NULL)");
			var table = read(c, name).getTables().get("items");
			table.getRows().add(r -> {
				r.put("id", 1L);
				r.put("label", "ok");
			});
			table.getRows().add(r -> {
				r.put("id", 1L);
				r.put("label", "duplicate");
			});
			assertEquals("23505", assertThrows(SQLException.class,
					() -> BulkInsertResolver.resolve(new CockroachDB()).execute(c, table, BulkOption.defaults()))
					.getSQLState());
			assertTrue(c.getAutoCommit());
			assertEquals(0, count(sql, name + ".items"));
		});
	}

	@Test
	void upsertHonorsActionsDuplicatesAndCallerRollback() throws Exception {
		withSchema((c, sql, name) -> {
			sql.execute("CREATE TABLE " + name + ".items (id INT8 PRIMARY KEY,label STRING)");
			sql.execute("INSERT INTO " + name + ".items VALUES (1,'initial')");
			var table = read(c, name).getTables().get("items");
			var executor = BulkUpsertResolver.resolve(new CockroachDB());
			table.getRows().add(r -> {
				r.put("id", 1L);
				r.put("label", "updated");
			});
			table.getRows().add(r -> {
				r.put("id", 2L);
				r.put("label", "new");
			});
			assertEquals(1, executor.execute(c, table, BulkUpsertOption.builder().insertWhenNotMatched(false).build()));
			assertEquals(1, count(sql, name + ".items"));
			assertEquals(1, executor.execute(c, table, BulkUpsertOption.builder().updateWhenMatched(false).build()));
			assertEquals(2, count(sql, name + ".items"));
			c.setAutoCommit(false);
			table.getRows().clear();
			table.getRows().add(r -> {
				r.put("id", 3L);
				r.put("label", "rollback");
			});
			executor.execute(c, table, BulkUpsertOption.defaults());
			c.rollback();
			c.setAutoCommit(true);
			assertEquals(2, count(sql, name + ".items"));
			table.getRows().clear();
			table.getRows().add(r -> {
				r.put("id", 4L);
				r.put("label", "first");
			});
			table.getRows().add(r -> {
				r.put("id", 4L);
				r.put("label", "second");
			});
			assertThrows(IllegalArgumentException.class, () -> executor.execute(c, table, BulkUpsertOption.defaults()));
			assertEquals(2, count(sql, name + ".items"));
			assertEquals(1, executor.execute(c, table, BulkUpsertOption.builder()
					.duplicateKeyStrategy(BulkUpsertDuplicateKeyStrategy.KEEP_FIRST).build()));
		});
	}

	@Test
	void upsertsKeyOnlyTablesWithoutEmptyUpdates() throws Exception {
		withSchema((c, sql, name) -> {
			sql.execute("CREATE TABLE " + name + ".items (id INT8 PRIMARY KEY)");
			var table = read(c, name).getTables().get("items");
			table.getRows().add(r -> r.put("id", 1L));
			var executor = BulkUpsertResolver.resolve(new CockroachDB());
			assertEquals(1, executor.execute(c, table, BulkUpsertOption.defaults()));
			assertEquals(0, executor.execute(c, table, BulkUpsertOption.defaults()));
		});
	}

	@Test
	void preservesHiddenImplicitKeyAndRejectsUnwritableUpsertKey() throws Exception {
		withSchema((c, sql, name) -> {
			sql.execute("CREATE TABLE " + name + ".items (label STRING)");
			var table = read(c, name).getTables().get("items");
			assertTrue(table.getColumns().get("rowid").isHidden());
			table.getRows().add(r -> r.put("label", "ok"));
			assertEquals(1, BulkInsertResolver.resolve(new CockroachDB()).execute(c, table, BulkOption.defaults()));
			assertThrows(IllegalArgumentException.class,
					() -> BulkUpsertResolver.resolve(new CockroachDB()).execute(c, table, BulkUpsertOption.defaults()));
		});
	}

	@Test
	void surfacesSerializationFailureForCallerRetry() throws Exception {
		withSchema((c, sql, name) -> {
			c.setAutoCommit(false);
			sql.execute("SELECT 1");
			var failure = assertThrows(SQLException.class,
					() -> sql.execute("SELECT crdb_internal.force_retry('1h'::INTERVAL)"));
			assertEquals("40001", failure.getSQLState());
			c.rollback();
			c.setAutoCommit(true);
		});
	}

	@Test
	void migratesWithAtomicDatabaseCheckpointAndResume() throws Exception {
		withSchema((c, sql, name) -> {
			sql.execute("CREATE TABLE " + name + ".items (code STRING PRIMARY KEY,label STRING)");
			var table = read(c, name).getTables().get("items");
			com.sqlapp.data.db.dialect.test.BulkMigrationTransactionAssertions.assertDatabaseCheckpointAtomic(c, table,
					"code", "label", "SELECT count(*) FROM " + name + ".items");
			sql.execute("TRUNCATE " + name + ".items");
			com.sqlapp.data.db.dialect.test.BulkMigrationTransactionAssertions.assertDatabaseCheckpointInsertAtomic(c,
					table, "code", "label", "SELECT count(*) FROM " + name + ".items");
		});
	}

	@Test
	void sustainsChunkedUnicodeBinaryAndDuplicateLoad() throws Exception {
		withSchema((c, sql, name) -> {
			sql.execute("CREATE TABLE " + name + ".items (code STRING PRIMARY KEY,content STRING,payload BYTES)");
			var table = com.sqlapp.data.db.dialect.test.BulkMigrationLoadAssertions.table(name, "items", "code",
					"content", "payload", com.sqlapp.data.db.datatype.DataType.LONGVARCHAR,
					com.sqlapp.data.db.datatype.DataType.VARBINARY);
			com.sqlapp.data.db.dialect.test.BulkMigrationLoadAssertions.assertChunkedLobAndDuplicateLoad(c, table,
					"code", "content", "payload", "SELECT count(*) FROM " + name + ".items",
					"SELECT content, payload FROM " + name + ".items WHERE code=?");
		});
	}

	private void apply(Connection c, DbObjectDifference difference) throws Exception {
		var registry = new CockroachDB().createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		var operations = registry.createSql(difference);
		assertFalse(operations.isEmpty());
		try (var sql = c.createStatement()) {
			for (var operation : operations) {
				sql.execute(operation.getSqlText());
			}
		}
	}

	private void drop(Connection c, Object object) throws Exception {
		var registry = new CockroachDB().createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		var operations = registry.createSql(object, SqlType.DROP);
		assertFalse(operations.isEmpty());
		try (var sql = c.createStatement()) {
			for (var operation : operations) {
				sql.execute(operation.getSqlText());
			}
		}
	}

	@Test
	void altersColumnsDefaultsNullabilityAndPreservesRows() throws Exception {
		withSchema((c, sql, name) -> {
			sql.execute("CREATE TABLE " + name + ".items (id INT8 PRIMARY KEY,label STRING,obsolete INT8)");
			sql.execute("INSERT INTO " + name + ".items VALUES (1,'hello',9)");
			var original = read(c, name).getTables().get("items");
			var target = original.clone();
			target.getColumns().add("score", col -> col.setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT)
					.setDefaultValue("7").setNotNull(true));
			apply(c, original.diff(target));
			var added = read(c, name).getTables().get("items");
			assertTrue(added.getColumns().get("score").isNotNull());
			try (var rows = sql.executeQuery("SELECT score FROM " + name + ".items WHERE id=1")) {
				assertTrue(rows.next());
				assertEquals(7, rows.getInt(1));
			}
			target = added.clone();
			target.getColumns().get("label").setDataType(com.sqlapp.data.db.datatype.DataType.VARCHAR)
					.setDataTypeName("varchar").setLength(80).setDefaultValue("'new'").setNotNull(true);
			target.getColumns().remove("obsolete");
			apply(c, added.diff(target));
			var changed = read(c, name).getTables().get("items");
			assertNull(changed.getColumns().get("obsolete"));
			assertTrue(changed.getColumns().get("label").isNotNull());
			assertEquals(80, changed.getColumns().get("label").getLength());
			target = changed.clone();
			target.getColumns().get("label").setDefaultValue(null).setNotNull(false);
			apply(c, changed.diff(target));
			var finalTable = read(c, name).getTables().get("items");
			assertFalse(finalTable.getColumns().get("label").isNotNull());
			assertNull(finalTable.getColumns().get("label").getDefaultValue());
			assertEquals(1, count(sql, name + ".items"));
		});
	}

	@Test
	void altersIndexesAndUsesTableScopedNames() throws Exception {
		withSchema((c, sql, name) -> {
			sql.execute("CREATE TABLE " + name
					+ ".items (id INT8 PRIMARY KEY,label STRING,payload BYTES, INDEX by_label (label) STORING (payload))");
			sql.execute("INSERT INTO " + name + ".items VALUES (1,'ok',decode('ff','hex'))");
			var table = read(c, name).getTables().get("items");
			var index = table.getIndexes().get("by_label");
			var target = index.clone().setDefinition("CREATE INDEX by_label ON " + name
					+ ".items (label DESC) STORING (payload) WHERE label IS NOT NULL");
			table.getIndexes().remove("by_label");
			table.getIndexes().add(target);
			apply(c, index.diff(target));
			var actual = read(c, name).getTables().get("items");
			assertTrue(String.join(" ", actual.getIndexes().get("by_label").getDefinition()).contains("WHERE"));
			var indexBefore = actual.getIndexes().get("by_label");
			var indexAfter = indexBefore.clone().setRemarks("index note");
			actual.getIndexes().remove("by_label");
			actual.getIndexes().add(indexAfter);
			apply(c, indexBefore.diff(indexAfter));
			assertEquals("index note",
					read(c, name).getTables().get("items").getIndexes().get("by_label").getRemarks());

			var next = actual.clone();
			next.getIndexes().remove("by_label");
			next.getIndexes().add("by_payload", next.getColumns().get("id")).getIncludes().add("payload");
			apply(c, actual.diff(next));
			var changed = read(c, name).getTables().get("items");
			assertNull(changed.getIndexes().get("by_label"));
			assertNotNull(changed.getIndexes().get("by_payload"));
			drop(c, changed.getIndexes().get("by_payload"));
			assertEquals(1, count(sql, name + ".items"));
		});
	}

	@Test
	void addsChangesAndDropsCheckUniqueAndForeignKeyConstraints() throws Exception {
		withSchema((c, sql, name) -> {
			sql.execute("CREATE TABLE " + name + ".parent (id INT8 PRIMARY KEY)");
			sql.execute("CREATE TABLE " + name
					+ ".items (id INT8 PRIMARY KEY,parent_id INT8,label STRING,CONSTRAINT positive CHECK (id>0))");
			var original = read(c, name).getTables().get("items");
			sql.execute("ALTER TABLE " + name + ".items ADD CONSTRAINT label_unique UNIQUE(label)");
			sql.execute("ALTER TABLE " + name + ".items ADD CONSTRAINT parent_fk FOREIGN KEY(parent_id) REFERENCES "
					+ name + ".parent(id)");
			sql.execute("ALTER TABLE " + name + ".items DROP CONSTRAINT positive");
			sql.execute("ALTER TABLE " + name + ".items ADD CONSTRAINT positive CHECK(id>=0)");
			var target = read(c, name).getTables().get("items");
			drop(c, target);
			create(c, original);
			apply(c, original.diff(target));
			var actual = read(c, name).getTables().get("items");
			assertNotNull(actual.getConstraints().get("label_unique"));
			assertNotNull(actual.getConstraints().get("parent_fk"));
			drop(c, actual.getConstraints().get("parent_fk"));
			drop(c, actual.getConstraints().get("label_unique"));
			drop(c, actual.getConstraints().get("positive"));
			assertEquals(1, read(c, name).getTables().get("items").getConstraints().size());
		});
	}

	@Test
	void renamesTableAndChangesTableAndColumnComments() throws Exception {
		withSchema((c, sql, name) -> {
			sql.execute("CREATE TABLE " + name + ".items (id INT8 PRIMARY KEY,label STRING)");
			sql.execute("INSERT INTO " + name + ".items VALUES (1,'kept')");
			var original = read(c, name).getTables().get("items");
			var target = original.clone().setName("Renamed Table").setRemarks("new note");
			target.getColumns().get("label").setRemarks("column note");
			apply(c, original.diff(target));
			var actual = read(c, name).getTables().get("Renamed Table");
			assertNotNull(actual);
			assertEquals("new note", actual.getRemarks());
			try (var rows = sql.executeQuery("SELECT col_description('" + name + ".\"Renamed Table\"'::regclass,2)")) {
				assertTrue(rows.next());
				assertEquals("column note", rows.getString(1));
			}

			var cleared = actual.clone().setRemarks(null);
			cleared.getColumns().get("label").setRemarks(null);
			apply(c, actual.diff(cleared));
			actual = read(c, name).getTables().get("Renamed Table");
			assertNull(actual.getRemarks());
			assertNull(actual.getColumns().get("label").getRemarks());
			assertEquals(1, count(sql, name + ".\"Renamed Table\""));
			drop(c, actual);
			assertTrue(read(c, name).getTables().isEmpty());
		});
	}

	@Test
	void replacesAndDropsOnlyTheSelectedFunctionOverload() throws Exception {
		withSchema((c, sql, name) -> {
			sql.execute("CREATE FUNCTION " + name + ".inc(v INT8) RETURNS INT8 LANGUAGE SQL AS $$SELECT v+1$$");
			sql.execute(
					"CREATE FUNCTION " + name + ".inc(v STRING) RETURNS STRING LANGUAGE SQL AS $$SELECT v || '!'$$");
			var original = read(c, name).getFunctions().stream()
					.filter(f -> f.getSpecificName().toUpperCase(Locale.ROOT).contains("INT")).findFirst()
					.orElseThrow();
			var target = original.clone().setDefinition(
					"CREATE FUNCTION " + name + ".inc(v INT8) RETURNS INT8 LANGUAGE SQL AS $$SELECT v+2$$");
			apply(c, original.diff(target));
			try (var rows = sql.executeQuery("SELECT " + name + ".inc(5::INT8)," + name + ".inc('a'::STRING)")) {
				assertTrue(rows.next());
				assertEquals(7, rows.getInt(1));
				assertEquals("a!", rows.getString(2));
			}
			drop(c, original);
			assertEquals(1, read(c, name).getFunctions().size());
		});
	}

	@Test
	void altersSequenceConfigurationWithoutRestarting() throws Exception {
		withSchema((c, sql, name) -> {
			sql.execute("CREATE SEQUENCE " + name + ".seq START 10 INCREMENT 3 MINVALUE 1 MAXVALUE 1000");
			var original = read(c, name).getSequences().get("seq");
			assertEquals(java.math.BigInteger.TEN, original.getStartValue());
			try (var rows = sql.executeQuery("SELECT nextval('" + name + ".seq')")) {
				assertTrue(rows.next());
				assertEquals(10, rows.getInt(1));
			}
			var target = original.clone().setIncrementBy(java.math.BigInteger.valueOf(5))
					.setMaxValue(java.math.BigInteger.valueOf(2000)).setStartValue(java.math.BigInteger.valueOf(30));
			apply(c, original.diff(target));
			var actual = read(c, name).getSequences().get("seq");
			assertEquals(java.math.BigInteger.valueOf(5), actual.getIncrementBy());
			assertEquals(java.math.BigInteger.valueOf(2000), actual.getMaxValue());
			assertEquals(java.math.BigInteger.valueOf(30), actual.getStartValue());
			try (var rows = sql.executeQuery("SELECT nextval('" + name + ".seq')")) {
				assertTrue(rows.next());
				assertEquals(15, rows.getInt(1));
			}
			drop(c, actual);
			assertTrue(read(c, name).getSequences().isEmpty());
		});
	}

	@Test
	void addsAndRenamesEnumLabelsWithDependentData() throws Exception {
		withSchema((c, sql, name) -> {
			sql.execute("CREATE TYPE " + name + ".mood AS ENUM ('ok','done')");
			sql.execute("CREATE TABLE " + name + ".items (id INT8 PRIMARY KEY,state " + name + ".mood)");
			sql.execute("INSERT INTO " + name + ".items VALUES (1,'ok')");
			var original = read(c, name).getTypes().get("mood");
			var target = original.clone()
					.setDefinition("CREATE TYPE " + name + ".mood AS ENUM ('ok','a''b','done','日本語')");
			apply(c, original.diff(target));
			var actual = read(c, name).getTypes().get("mood");
			target = actual.clone().setDefinition("CREATE TYPE " + name + ".mood AS ENUM ('new','a''b','done','日本語')");
			apply(c, actual.diff(target));
			try (var rows = sql.executeQuery("SELECT state::STRING FROM " + name + ".items")) {
				assertTrue(rows.next());
				assertEquals("new", rows.getString(1));
			}
			drop(c, read(c, name).getTables().get("items"));
			drop(c, read(c, name).getTypes().get("mood"));
			assertTrue(read(c, name).getTypes().isEmpty());
		});
	}

	@Test
	void replacesViewWithoutDroppingItsDependents() throws Exception {
		withSchema((c, sql, name) -> {
			sql.execute("CREATE TABLE " + name + ".items (id INT8 PRIMARY KEY)");
			sql.execute("INSERT INTO " + name + ".items VALUES (1)");
			sql.execute("CREATE VIEW " + name + ".v AS SELECT id FROM " + name + ".items");
			sql.execute("CREATE VIEW " + name + ".dependent AS SELECT id FROM " + name + ".v");
			var original = read(c, name).getViews().get("v");
			var target = original.clone()
					.setDefinition("CREATE VIEW " + name + ".v AS SELECT id+1 AS id FROM " + name + ".items");
			apply(c, original.diff(target));
			try (var rows = sql.executeQuery("SELECT id FROM " + name + ".dependent")) {
				assertTrue(rows.next());
				assertEquals(2, rows.getInt(1));
			}
			drop(c, read(c, name).getViews().get("dependent"));
			drop(c, read(c, name).getViews().get("v"));
		});
	}

	@Test
	void appliesWholeSchemaDifferenceAndVerifiesReReadDefinition() throws Exception {
		withSchema((c, sql, name) -> {
			sql.execute("CREATE TABLE " + name + ".items (id INT8 PRIMARY KEY,label STRING)");
			sql.execute("INSERT INTO " + name + ".items VALUES (1,'kept')");
			var original = read(c, name);
			sql.execute("ALTER TABLE " + name + ".items ADD COLUMN score INT8 DEFAULT 7 NOT NULL");
			sql.execute("CREATE INDEX by_label ON " + name + ".items(label) STORING(score)");
			sql.execute("CREATE SEQUENCE " + name + ".seq START 20 INCREMENT 2");
			sql.execute("CREATE VIEW " + name + ".v AS SELECT label,score FROM " + name + ".items");
			var target = read(c, name);
			drop(c, target.getViews().get("v"));
			drop(c, target.getSequences().get("seq"));
			drop(c, target.getTables().get("items"));
			create(c, original.getTables().get("items"));
			sql.execute("INSERT INTO " + name + ".items VALUES(1,'kept')");
			Schema restored = SchemaUtils.readXml(new StringReader(target.asXml()));
			apply(c, original.diff(restored));
			var actual = read(c, name);
			assertEquals(restored.getTables().get("items").getDefinition(),
					actual.getTables().get("items").getDefinition());
			assertEquals(restored.getViews().get("v").getDefinition(), actual.getViews().get("v").getDefinition());
			assertEquals(1, count(sql, name + ".items"));
		});
	}

	@Test
	void renamesQuotedColumnWithoutLosingValues() throws Exception {
		withSchema((c, sql, name) -> {
			sql.execute("CREATE TABLE " + name + ".items (id INT8 PRIMARY KEY,label STRING)");
			sql.execute("INSERT INTO " + name + ".items VALUES (1,'kept')");
			var original = read(c, name).getTables().get("items");
			var target = original.clone();
			target.getColumns().get("label").setName("New Label");
			apply(c, original.diff(target));
			try (var rows = sql.executeQuery("SELECT \"New Label\" FROM " + name + ".items")) {
				assertTrue(rows.next());
				assertEquals("kept", rows.getString(1));
			}
		});
	}

	@Test
	void removesSchemaDependentsBeforeTheirPrerequisites() throws Exception {
		withSchema((c, sql, name) -> {
			sql.execute("CREATE TYPE " + name + ".mood AS ENUM ('ok')");
			sql.execute("CREATE SEQUENCE " + name + ".seq");
			sql.execute("CREATE TABLE " + name + ".a_parent (id INT8 PRIMARY KEY)");
			sql.execute("CREATE TABLE " + name + ".z_child (id INT8 PRIMARY KEY DEFAULT nextval('" + name
					+ ".seq'), parent_id INT8 REFERENCES " + name + ".a_parent(id), state " + name + ".mood)");
			sql.execute("CREATE VIEW " + name + ".v AS SELECT state FROM " + name + ".z_child");
			sql.execute("CREATE TABLE " + name + ".kept (id INT8 PRIMARY KEY)");
			sql.execute("INSERT INTO " + name + ".kept VALUES(7)");
			var original = read(c, name);
			var target = original.clone();
			target.getViews().clear();
			target.getTables().remove("a_parent");
			target.getTables().remove("z_child");
			target.getSequences().clear();
			target.getTypes().clear();
			apply(c, original.diff(target));
			var actual = read(c, name);
			assertEquals(1, actual.getTables().size());
			assertEquals(1, count(sql, name + ".kept"));
			assertTrue(actual.getTypes().isEmpty());
			assertTrue(actual.getSequences().isEmpty());
			assertTrue(actual.getViews().isEmpty());
		});
	}

	@Test
	void recreatesAndBulkLoadsQuotedEnumArraysOutsideSearchPath() throws Exception {
		withSchema((c, sql, name) -> {
			String type = name + ".\"_Mood quoted\"";
			sql.execute("CREATE TYPE " + type + " AS ENUM ('ok','a,b','NULL','日本語','a\"b','')");
			sql.execute(
					"CREATE TABLE " + name + ".items (id INT8 PRIMARY KEY, moods " + type + "[], mood " + type + ")");
			var original = read(c, name);
			var column = original.getTables().get("items").getColumns().get("moods");
			assertEquals(1, column.getArrayDimension());
			assertEquals("\"" + name + "\".\"_Mood quoted\"", column.getDataTypeName());
			Schema restored = SchemaUtils.readXml(new StringReader(original.asXml()));
			sql.execute("DROP SCHEMA " + name + " CASCADE");
			create(c, restored);
			assertEquals(original.getTables().get("items").getDefinition(),
					read(c, name).getTables().get("items").getDefinition());
			sql.execute("DROP TABLE " + name + ".items");
			var modeled = restored.getTables().get("items");
			modeled.setDefinition((String) null);
			create(c, modeled);
			assertEquals(column.getDataTypeName(),
					read(c, name).getTables().get("items").getColumns().get("moods").getDataTypeName());
			String[] values = { "ok", "a,b", "NULL", "日本語", "a\"b", "", null };
			modeled.getRows().add(r -> {
				r.put("id", 1L);
				r.put("moods", values);
				r.put("mood", "日本語");
			});
			modeled.getRows().add(r -> {
				r.put("id", 2L);
				r.put("moods", new String[0]);
				r.put("mood", null);
			});
			modeled.getRows().add(r -> {
				r.put("id", 3L);
				r.put("moods", null);
				r.put("mood", "ok");
			});
			assertEquals(3, BulkInsertResolver.resolve(new CockroachDB()).execute(c, modeled, BulkOption.defaults()));
			String[] updated = { null, "", "a\"b", "日本語", "NULL", "a,b", "ok" };
			modeled.getRows().get(0).put("moods", updated);
			assertEquals(3,
					BulkUpsertResolver.resolve(new CockroachDB()).execute(c, modeled, BulkUpsertOption.defaults()));
			try (var rows = sql.executeQuery("SELECT moods,mood FROM " + name + ".items ORDER BY id")) {
				assertTrue(rows.next());
				assertArrayEquals(updated, (Object[]) rows.getArray(1).getArray());
				assertEquals("日本語", rows.getString(2));
				assertTrue(rows.next());
				assertEquals(0, ((Object[]) rows.getArray(1).getArray()).length);
				assertNull(rows.getString(2));
				assertTrue(rows.next());
				assertNull(rows.getArray(1));
				assertEquals("ok", rows.getString(2));
			}
		});
	}

	@Test
	void distinguishesEnumTypesWithBuiltinNamesAndRejectsInvalidArrayData() throws Exception {
		withSchema((c, sql, name) -> {
			String other = name + "_types";
			sql.execute("CREATE SCHEMA " + other);
			try {
				sql.execute("CREATE TYPE " + other + ".text AS ENUM ('ok','日本語')");
				sql.execute("CREATE TYPE " + name + ".text AS ENUM ('different')");
				sql.execute("CREATE TABLE " + name + ".items (id INT8 PRIMARY KEY, mood " + other + ".text, moods "
						+ other + ".text[], ordinary TEXT[])");
				var table = read(c, name).getTables().get("items");
				for (String col : List.of("mood", "moods")) {
					assertEquals(com.sqlapp.data.db.datatype.DataType.OTHER, table.getColumns().get(col).getDataType());
					assertEquals("\"" + other + "\".\"text\"", table.getColumns().get(col).getDataTypeName());
				}
				assertNotEquals(com.sqlapp.data.db.datatype.DataType.OTHER,
						table.getColumns().get("ordinary").getDataType());
				table.getRows().add(r -> {
					r.put("id", 1L);
					r.put("mood", "日本語");
					r.put("moods", new String[] { "ok", null });
					r.put("ordinary", new String[] { "free" });
				});
				assertEquals(1,
						BulkUpsertResolver.resolve(new CockroachDB()).execute(c, table, BulkUpsertOption.defaults()));
				table.getRows().clear();
				table.getRows().add(r -> {
					r.put("id", 2L);
					r.put("moods", new String[] { "invalid" });
				});
				assertThrows(SQLException.class,
						() -> BulkInsertResolver.resolve(new CockroachDB()).execute(c, table, BulkOption.defaults()));
				assertTrue(c.getAutoCommit());
				assertEquals(1, count(sql, name + ".items"));
				assertThrows(SQLException.class, () -> BulkUpsertResolver.resolve(new CockroachDB()).execute(c, table,
						BulkUpsertOption.defaults()));
				assertTrue(c.getAutoCommit());
				assertEquals(1, count(sql, name + ".items"));
			} finally {
				sql.execute("DROP TABLE IF EXISTS " + name + ".items");
				sql.execute("DROP SCHEMA " + other + " CASCADE");
			}
		});
	}

	@Test
	void preservesDatabaseAndTablePlacement() throws Exception {
		String database = schema();
		try (var c = connect(); var sql = c.createStatement()) {
			try {
				sql.execute("CREATE DATABASE " + database + " PRIMARY REGION \"sqlapp-region\"");
				sql.execute("USE " + database);
				sql.execute(
						"CREATE TABLE public.global_items (id INT8 PRIMARY KEY, note STRING DEFAULT 'LOCALITY GLOBAL') LOCALITY GLOBAL");
				sql.execute("COMMENT ON TABLE public.global_items IS 'LOCALITY REGIONAL BY ROW'");
				sql.execute(
						"CREATE TABLE public.regional_items (id INT8 PRIMARY KEY) LOCALITY REGIONAL BY TABLE IN \"sqlapp-region\"");
				sql.execute("CREATE TABLE public.row_items (id INT8 PRIMARY KEY) LOCALITY REGIONAL BY ROW");
				sql.execute(
						"CREATE TABLE public.named_row_items (id INT8 PRIMARY KEY, \"Home Region\" public.crdb_internal_region NOT NULL DEFAULT 'sqlapp-region') LOCALITY REGIONAL BY ROW AS \"Home Region\"");
				var dialect = new CockroachDB();
				var reader = dialect.getCatalogReader();
				var original = reader.getAll(c).get(0);
				assertEquals("sqlapp-region", original.getSpecifics().get("COCKROACH_PRIMARY_REGION"));
				assertEquals("ZONE", original.getSpecifics().get("COCKROACH_SURVIVAL_GOAL"));
				Catalog restored = SchemaUtils.readXml(new StringReader(original.asXml()));
				assertEquals(original.getSpecifics(), restored.getSpecifics());
				var tables = read(c, "public");
				assertEquals("GLOBAL", tables.getTables().get("global_items").getSpecifics().get("COCKROACH_LOCALITY"));
				assertTrue(tables.getTables().get("regional_items").getSpecifics().get("COCKROACH_LOCALITY")
						.startsWith("REGIONAL BY TABLE IN"));
				assertEquals("REGIONAL BY ROW",
						tables.getTables().get("row_items").getSpecifics().get("COCKROACH_LOCALITY"));
				assertEquals("REGIONAL BY ROW AS \"Home Region\"",
						tables.getTables().get("named_row_items").getSpecifics().get("COCKROACH_LOCALITY"));
				Schema tableSnapshot = SchemaUtils.readXml(new StringReader(tables.asXml()));
				for (String table : List.of("global_items", "regional_items", "row_items", "named_row_items"))
					sql.execute("DROP TABLE public." + table);
				for (var table : tableSnapshot.getTables())
					create(c, table);
				assertEquals("REGIONAL BY ROW",
						read(c, "public").getTables().get("row_items").getSpecifics().get("COCKROACH_LOCALITY"));
				var global = tableSnapshot.getTables().get("global_items");
				sql.execute("DROP TABLE public.global_items");
				global.getSpecifics().put("COCKROACH_LOCALITY", "REGIONAL BY TABLE IN PRIMARY REGION");
				create(c, global);
				assertEquals("REGIONAL BY TABLE IN PRIMARY REGION",
						read(c, "public").getTables().get("global_items").getSpecifics().get("COCKROACH_LOCALITY"));
				sql.execute("INSERT INTO public.global_items(id) VALUES (1)");
				try (var rows = sql.executeQuery("SELECT note FROM public.global_items")) {
					assertTrue(rows.next());
					assertEquals("LOCALITY GLOBAL", rows.getString(1));
				}
				var modeled = new Table("modeled").setSchemaName("public");
				modeled.getColumns().add("id", col -> col.setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT));
				modeled.getSpecifics().put("COCKROACH_LOCALITY", "GLOBAL");
				create(c, modeled);
				assertEquals("GLOBAL",
						read(c, "public").getTables().get("modeled").getSpecifics().get("COCKROACH_LOCALITY"));
				Catalog full = SchemaUtils.readXml(new StringReader(reader.getAllFull(c).get(0).asXml()));
				sql.execute("USE defaultdb");
				sql.execute("DROP DATABASE " + database + " CASCADE");
				sql.execute("CREATE DATABASE " + database);
				sql.execute("USE " + database);
				create(c, full);
				assertEquals(original.getSpecifics(), reader.getAll(c).get(0).getSpecifics());
				sql.execute("INSERT INTO public.named_row_items(id) VALUES (1)");
				try (var rows = sql.executeQuery("SELECT \"Home Region\"::STRING FROM public.named_row_items")) {
					assertTrue(rows.next());
					assertEquals("sqlapp-region", rows.getString(1));
				}
				assertEquals("REGIONAL BY ROW",
						read(c, "public").getTables().get("row_items").getSpecifics().get("COCKROACH_LOCALITY"));
			} finally {
				sql.execute("USE defaultdb");
				sql.execute("DROP DATABASE IF EXISTS " + database + " CASCADE");
			}
		}
	}

	@Test
	void preservesOrdinaryCatalogAndUserTypeWithoutPlacement() throws Exception {
		withSchema((c, sql, name) -> {
			var catalog = new CockroachDB().getCatalogReader().getAll(c).get(0);
			assertTrue(catalog.getSpecifics().isEmpty());
			assertTrue(new CockroachDB().createSqlFactoryRegistry().createSql(catalog, SqlType.CREATE).isEmpty());
			sql.execute("CREATE TYPE " + name + ".crdb_internal_region AS ENUM ('user-value')");
			assertNotNull(read(c, name).getTypes().get("crdb_internal_region"));
		});
	}
}
