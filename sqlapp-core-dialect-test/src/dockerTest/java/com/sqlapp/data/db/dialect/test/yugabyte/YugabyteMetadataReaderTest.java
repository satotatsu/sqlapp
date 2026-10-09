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
				statement.execute("COMMENT ON SEQUENCE " + schemaName + ".sample_seq IS '採番 ''sequence'''");
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
				assertEquals("採番 'sequence'", sequence.getRemarks());
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
				Sequence recreated = sequenceReader.getAllFull(connection).stream()
						.filter(t -> "sample_seq".equals(t.getName())).findFirst().orElseThrow();
				assertEquals(sequence.getRemarks(), recreated.getRemarks());
				assertEquals(sequence.getIncrementBy(), recreated.getIncrementBy());
				assertEquals(sequence.getStartValue(), recreated.getStartValue());
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
	void recreatesTypedAscendingAndDescendingSequences() throws Exception {
		String schemaName = "sqlapp_ysql_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader().getSequenceReader();
			reader.setSchemaName(schemaName);
			var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				var types = List.of(DataType.SMALLINT, DataType.INT, DataType.BIGINT);
				var names = List.of("smallint", "integer", "bigint");
				for (int index = 0; index < types.size(); index++) {
					for (int direction : new int[] { 1, -1 }) {
						String name = "typed_" + index + (direction > 0 ? "_asc" : "_desc");
						statement.execute("CREATE SEQUENCE " + schemaName + "." + name + " AS " + names.get(index)
								+ " START WITH " + direction * 10 + " INCREMENT BY " + direction * 3
								+ " MINVALUE " + (direction > 0 ? 10 : -1000)
								+ " MAXVALUE " + (direction > 0 ? 1000 : -10) + " CACHE 100");
						reader.setObjectName(name);
						Sequence sequence = reader.getAllFull(connection).stream()
								.filter(t -> name.equals(t.getName())).findFirst().orElseThrow();
						assertEquals(types.get(index), sequence.getDataType());
						assertEquals(direction * 10, sequence.getStartValue().intValueExact());
						assertEquals(direction * 3, sequence.getIncrementBy().intValueExact());
						assertEquals(direction > 0 ? 10 : -1000, sequence.getMinValue().intValueExact());
						assertEquals(direction > 0 ? 1000 : -10, sequence.getMaxValue().intValueExact());
						statement.execute("DROP SEQUENCE " + schemaName + "." + name);
						SqlFactory<Sequence> factory = registry.getSqlFactory(sequence, SqlType.CREATE);
						for (var operation : factory.createSql(sequence)) statement.execute(operation.getSqlText());
						Sequence recreated = reader.getAllFull(connection).stream()
								.filter(t -> name.equals(t.getName())).findFirst().orElseThrow();
						assertEquals(sequence.getDataType(), recreated.getDataType());
						assertEquals(sequence.getMinValue(), recreated.getMinValue());
						assertEquals(sequence.getMaxValue(), recreated.getMaxValue());
						assertEquals(sequence.getCacheSize(), recreated.getCacheSize());
						try (var rows = statement.executeQuery("SELECT nextval('" + schemaName + "." + name
								+ "'), nextval('" + schemaName + "." + name + "')")) {
							assertTrue(rows.next());
							assertEquals(direction * 10, rows.getLong(1));
							assertEquals(direction * 13, rows.getLong(2));
						}
					}
				}
			} finally {
				statement.execute("DROP SCHEMA " + schemaName + " CASCADE");
			}
		}
	}

	@Test
	void recreatesSequenceTypeBoundsAndCycleBehavior() throws Exception {
		String schemaName = "sqlapp_ysql_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader().getSequenceReader();
			reader.setSchemaName(schemaName);
			var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				var types = List.of("smallint", "integer", "bigint");
				long[] minima = { Short.MIN_VALUE, Integer.MIN_VALUE, Long.MIN_VALUE };
				long[] maxima = { Short.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE };
				for (int index = 0; index < types.size(); index++) {
					for (int direction : new int[] { 1, -1 }) {
						for (boolean cycle : new boolean[] { false, true }) {
							String name = "boundary_" + index + (direction > 0 ? "_asc" : "_desc")
									+ (cycle ? "_cycle" : "_stop");
							long start = direction > 0 ? maxima[index] : minima[index];
							long wrapped = direction > 0 ? minima[index] : maxima[index];
							statement.execute("CREATE SEQUENCE " + schemaName + "." + name + " AS " + types.get(index)
									+ " START WITH " + start + " INCREMENT BY " + direction + " MINVALUE " + minima[index]
									+ " MAXVALUE " + maxima[index] + " CACHE 100" + (cycle ? " CYCLE" : " NO CYCLE"));
							reader.setObjectName(name);
							Sequence sequence = reader.getAllFull(connection).stream()
									.filter(t -> name.equals(t.getName())).findFirst().orElseThrow();
							assertEquals(minima[index], sequence.getMinValue().longValueExact());
							assertEquals(maxima[index], sequence.getMaxValue().longValueExact());
							assertEquals(start, sequence.getStartValue().longValueExact());
							assertEquals(cycle, sequence.isCycle());
							statement.execute("DROP SEQUENCE " + schemaName + "." + name);
							SqlFactory<Sequence> factory = registry.getSqlFactory(sequence, SqlType.CREATE);
							for (var operation : factory.createSql(sequence)) statement.execute(operation.getSqlText());
							Sequence recreated = reader.getAllFull(connection).stream()
									.filter(t -> name.equals(t.getName())).findFirst().orElseThrow();
							assertEquals(sequence.getDataType(), recreated.getDataType());
							assertEquals(sequence.getMinValue(), recreated.getMinValue());
							assertEquals(sequence.getMaxValue(), recreated.getMaxValue());
							assertEquals(cycle, recreated.isCycle());
							String next = "SELECT nextval('" + schemaName + "." + name + "')";
							try (var rows = statement.executeQuery(next)) {
								assertTrue(rows.next());
								assertEquals(start, rows.getLong(1));
							}
							if (cycle) {
								try (var rows = statement.executeQuery(next)) {
									assertTrue(rows.next());
									assertEquals(wrapped, rows.getLong(1));
								}
								try (var rows = statement.executeQuery(next)) {
									assertTrue(rows.next());
									assertEquals(wrapped + direction, rows.getLong(1));
								}
							} else {
								SQLException error = assertThrows(SQLException.class, () -> statement.executeQuery(next));
								assertEquals("2200H", error.getSQLState());
							}
						}
					}
				}
			} finally {
				statement.execute("DROP SCHEMA " + schemaName + " CASCADE");
			}
		}
	}

	@Test
	void recreatesEnumCatalogOrderAndCommentsAfterInsertedLabels() throws Exception {
		String schemaName = "ysql_enum_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE TYPE " + schemaName + ".status AS ENUM ('zulu', 'a''quote', '日本語')");
				statement.execute("ALTER TYPE " + schemaName + ".status ADD VALUE 'before' BEFORE 'a''quote'");
				statement.execute("ALTER TYPE " + schemaName + ".status ADD VALUE 'after' AFTER 'a''quote'");
				statement.execute("COMMENT ON TYPE " + schemaName + ".status IS '列挙 ''comment'''");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getDomainReader();
				reader.setSchemaName(schemaName);
				reader.setObjectName("status");
				var values = List.of("zulu", "before", "a'quote", "after", "日本語");
				var domain = reader.getAllFull(connection).stream().filter(t -> "status".equals(t.getName())).findFirst().orElseThrow();
				assertEquals(values, List.copyOf(domain.getValues()));
				assertEquals("列挙 'comment'", domain.getRemarks());
				statement.execute("DROP TYPE " + schemaName + ".status");
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var operation : registry.createSql(domain, SqlType.CREATE)) statement.execute(operation.getSqlText());
				var recreated = reader.getAllFull(connection).stream().filter(t -> "status".equals(t.getName())).findFirst().orElseThrow();
				assertEquals(values, List.copyOf(recreated.getValues()));
				assertEquals(domain.getRemarks(), recreated.getRemarks());
				try (var rows = statement.executeQuery("SELECT unnest(enum_range(NULL::" + schemaName + ".status))::text")) {
					for (String expected : values) { assertTrue(rows.next()); assertEquals(expected, rows.getString(1)); }
					assertFalse(rows.next());
				}
			} finally { statement.execute("DROP SCHEMA " + schemaName + " CASCADE"); }
		}
	}

	@Test
	void recreatesDomainMultipleChecksDefaultsNullabilityAndComments() throws Exception {
		String schemaName = "ysql_domain_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE DOMAIN " + schemaName + ".amount AS numeric(12,2) DEFAULT 12.34 NOT NULL "
						+ "CONSTRAINT amount_low CHECK (VALUE >= 10 OR VALUE = -1) CONSTRAINT amount_high CHECK (VALUE <= 100)");
				statement.execute("COMMENT ON DOMAIN " + schemaName + ".amount IS '金額 ''comment'''");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getDomainReader();
				reader.setSchemaName(schemaName);
				reader.setObjectName("amount");
				var domains = reader.getAllFull(connection).stream().filter(t -> "amount".equals(t.getName())).toList();
				assertEquals(1, domains.size(), "Multiple CHECK constraints must not duplicate the domain");
				var domain = domains.get(0);
				assertTrue(domain.isNotNull());
				assertEquals("金額 'comment'", domain.getRemarks());
				assertNotNull(domain.getDefaultValue());
				assertTrue(domain.getCheck().contains("AND"), domain.getCheck());
				statement.execute("DROP DOMAIN " + schemaName + ".amount");
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var operation : registry.createSql(domain, SqlType.CREATE)) statement.execute(operation.getSqlText());
				var recreated = reader.getAllFull(connection).stream().filter(t -> "amount".equals(t.getName())).findFirst().orElseThrow();
				assertEquals(domain.getRemarks(), recreated.getRemarks());
				assertEquals(domain.getDataType(), recreated.getDataType());
				assertEquals(domain.getLength(), recreated.getLength());
				assertEquals(domain.getScale(), recreated.getScale());
				statement.execute("CREATE TABLE " + schemaName + ".items (amount " + schemaName + ".amount)");
				statement.execute("INSERT INTO " + schemaName + ".items DEFAULT VALUES");
				try (var rows = statement.executeQuery("SELECT amount FROM " + schemaName + ".items")) {
					assertTrue(rows.next()); assertEquals(new BigDecimal("12.34"), rows.getBigDecimal(1));
				}
				statement.execute("INSERT INTO " + schemaName + ".items VALUES (-1), (10), (100)");
				for (String invalid : List.of("5", "101", "NULL")) {
					SQLException error = assertThrows(SQLException.class,
							() -> statement.execute("INSERT INTO " + schemaName + ".items VALUES (" + invalid + ")"));
					assertEquals("NULL".equals(invalid) ? "23502" : "23514", error.getSQLState());
				}
			} finally { statement.execute("DROP SCHEMA " + schemaName + " CASCADE"); }
		}
	}

	@Test
	void preservesUnconstrainedNumericAndVarcharColumnsArraysAndDomains() throws Exception {
		String schemaName = "ysql_unbounded_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE TABLE " + schemaName + ".items (id integer PRIMARY KEY, amount numeric, label varchar, amounts numeric[], labels varchar[])");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var registry = dialect.createSqlFactoryRegistry(); registry.getOptions().setDecorateSchemaName(true);
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName(schemaName); reader.setObjectName("items");
				Table table = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(reader.getAllFull(connection).get(0).asXml()));
				statement.execute("DROP TABLE " + schemaName + ".items");
				for (var operation : registry.createSql(table, SqlType.CREATE)) statement.execute(operation.getSqlText());
				for (String declaration : List.of("n AS numeric", "v AS varchar", "ns AS numeric[]", "vs AS varchar[]")) {
					statement.execute("CREATE DOMAIN " + schemaName + "." + declaration);
				}
				var domainReader = dialect.getCatalogReader().getSchemaReader().getDomainReader(); domainReader.setSchemaName(schemaName);
				for (var domain : domainReader.getAllFull(connection)) {
					assertNull(domain.getLength(), domain.asXml());
					com.sqlapp.data.schemas.Domain restored = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(domain.asXml()));
					assertNull(restored.getLength(), restored.asXml());
					statement.execute("DROP DOMAIN " + schemaName + "." + domain.getName());
					for (var operation : registry.createSql(restored, SqlType.CREATE)) statement.execute(operation.getSqlText());
				}
				BigDecimal amount = new BigDecimal("1" + "0".repeat(1500) + ".123456789");
				String label = "雪".repeat(40000);
				table.getRows().add(r -> { r.put("id", 1); r.put("amount", amount); r.put("label", label);
					r.put("amounts", new BigDecimal[] { amount, null }); r.put("labels", new String[] { label, null }); });
				assertEquals(1, BulkInsertResolver.execute(connection, table, BulkOption.defaults()));
				statement.execute("CREATE TABLE " + schemaName + ".domain_items (amount " + schemaName + ".n, label "
						+ schemaName + ".v, amounts " + schemaName + ".ns, labels " + schemaName + ".vs)");
				statement.execute("INSERT INTO " + schemaName + ".domain_items SELECT amount,label,amounts,labels FROM " + schemaName + ".items");
				for (String name : List.of("items", "domain_items")) {
					try (var rows = statement.executeQuery("SELECT amount,label,amounts[1],labels[1],amounts[2],labels[2] FROM " + schemaName + "." + name)) {
						assertTrue(rows.next()); assertEquals(amount, rows.getBigDecimal(1)); assertEquals(label, rows.getString(2));
						assertEquals(amount, rows.getBigDecimal(3)); assertEquals(label, rows.getString(4));
						assertNull(rows.getBigDecimal(5)); assertNull(rows.getString(6));
					}
				}
				assertEquals(4, scalar(connection, "SELECT count(*) FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid "
						+ "JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='" + schemaName
						+ "' AND c.relname='items' AND a.attname IN ('amount','label','amounts','labels') AND a.atttypmod=-1"));
			} finally { statement.execute("DROP SCHEMA " + schemaName + " CASCADE"); }
		}
	}

	@Test
	void preservesIntervalFieldsAndFractionalPrecisionAcrossXmlRecreation() throws Exception {
		String schemaName = "ysql_intervals_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				var declarations = List.of("interval", "interval year", "interval month", "interval day", "interval hour", "interval minute", "interval second", "interval year to month", "interval day to hour", "interval day to minute", "interval day to second", "interval hour to minute", "interval hour to second", "interval minute to second", "interval(0)", "interval(3)", "interval second(0)", "interval day to second(3)", "interval hour to second(6)", "interval minute to second(0)");
				var columns = new java.util.StringJoiner(","); var domainColumns = new java.util.StringJoiner(","); var values = new java.util.StringJoiner(",");
				String literal = "INTERVAL '1 year 2 months 3 days 04:05:06.789123'";
				for (int i = 0; i < declarations.size(); i++) {
					String declaration = declarations.get(i);
					columns.add("v" + i + " " + declaration).add("a" + i + " " + declaration + "[]");
					values.add(literal).add("ARRAY[" + literal + ",NULL]");
					statement.execute("CREATE DOMAIN " + schemaName + ".d" + i + " AS " + declaration + " DEFAULT " + literal);
					statement.execute("CREATE DOMAIN " + schemaName + ".da" + i + " AS " + declaration + "[] DEFAULT ARRAY[" + literal + ",NULL]");
					domainColumns.add("v" + i + " " + schemaName + ".d" + i).add("a" + i + " " + schemaName + ".da" + i);
				}
				statement.execute("CREATE TABLE " + schemaName + ".items (" + columns + ")");
				String createDomainTable = "CREATE TABLE " + schemaName + ".domain_items (" + domainColumns + ")";
				statement.execute(createDomainTable);
				String insert = "INSERT INTO " + schemaName + ".items VALUES (" + values + ")";
				statement.execute(insert); statement.execute("INSERT INTO " + schemaName + ".domain_items DEFAULT VALUES");
				var expected = new java.util.ArrayList<String>(); var domainExpected = new java.util.ArrayList<String>();
				try (var rows = statement.executeQuery("SELECT * FROM " + schemaName + ".items")) { assertTrue(rows.next()); for (int i = 1; i <= declarations.size() * 2; i++) expected.add(rows.getString(i)); }
				try (var rows = statement.executeQuery("SELECT * FROM " + schemaName + ".domain_items")) { assertTrue(rows.next()); for (int i = 1; i <= declarations.size() * 2; i++) domainExpected.add(rows.getString(i)); }
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var registry = dialect.createSqlFactoryRegistry(); registry.getOptions().setDecorateSchemaName(true);
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader(); reader.setSchemaName(schemaName); reader.setObjectName("items");
				Table restored = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(reader.getAllFull(connection).get(0).asXml()));
				var domainReader = dialect.getCatalogReader().getSchemaReader().getDomainReader(); domainReader.setSchemaName(schemaName);
				var domains = new java.util.ArrayList<com.sqlapp.data.schemas.Domain>();
				for (var domain : domainReader.getAllFull(connection)) domains.add(com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(domain.asXml())));
				assertEquals(declarations.size() * 2, domains.size());
				statement.execute("DROP TABLE " + schemaName + ".items"); statement.execute("DROP TABLE " + schemaName + ".domain_items");
				for (var domain : domains) statement.execute("DROP DOMAIN " + schemaName + "." + domain.getName());
				for (var operation : registry.createSql(restored, SqlType.CREATE)) statement.execute(operation.getSqlText());
				for (var domain : domains) for (var operation : registry.createSql(domain, SqlType.CREATE)) statement.execute(operation.getSqlText());
				statement.execute(createDomainTable); statement.execute(insert); statement.execute("INSERT INTO " + schemaName + ".domain_items DEFAULT VALUES");
				try (var rows = statement.executeQuery("SELECT * FROM " + schemaName + ".items")) { assertTrue(rows.next()); for (int i = 1; i <= expected.size(); i++) assertEquals(expected.get(i - 1), rows.getString(i), declarations.get((i - 1) / 2)); }
				try (var rows = statement.executeQuery("SELECT * FROM " + schemaName + ".domain_items")) { assertTrue(rows.next()); for (int i = 1; i <= domainExpected.size(); i++) assertEquals(domainExpected.get(i - 1), rows.getString(i), declarations.get((i - 1) / 2)); }
			} finally { statement.execute("DROP SCHEMA " + schemaName + " CASCADE"); }
		}
	}

	@Test
	void preservesBitStringLengthsAcrossColumnsArraysAndDomains() throws Exception {
		String schemaName = "ysql_bits_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE TABLE " + schemaName + ".items (id integer PRIMARY KEY, unlimited bit varying, bounded bit varying(7), fixed bit(7), single bit, bits bit varying[])");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var registry = dialect.createSqlFactoryRegistry(); registry.getOptions().setDecorateSchemaName(true);
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName(schemaName); reader.setObjectName("items");
				Table table = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(reader.getAllFull(connection).get(0).asXml()));
				assertNull(table.getColumns().get("unlimited").getLength());
				assertNull(table.getColumns().get("bits").getLength());
				assertEquals(7L, table.getColumns().get("bounded").getLength());
				assertEquals(7L, table.getColumns().get("fixed").getLength());
				assertEquals(1L, table.getColumns().get("single").getLength());
				statement.execute("DROP TABLE " + schemaName + ".items");
				for (var operation : registry.createSql(table, SqlType.CREATE)) statement.execute(operation.getSqlText());
				for (String declaration : List.of("v AS bit varying", "vs AS bit varying[]", "b AS bit varying(7)", "f AS bit(7)")) {
					statement.execute("CREATE DOMAIN " + schemaName + "." + declaration);
				}
				var domainReader = dialect.getCatalogReader().getSchemaReader().getDomainReader(); domainReader.setSchemaName(schemaName);
				for (var domain : domainReader.getAllFull(connection)) {
					com.sqlapp.data.schemas.Domain restored = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(domain.asXml()));
					if (domain.getName().equals("v") || domain.getName().equals("vs")) assertNull(restored.getLength(), restored.asXml());
					statement.execute("DROP DOMAIN " + schemaName + "." + domain.getName());
					for (var operation : registry.createSql(restored, SqlType.CREATE)) statement.execute(operation.getSqlText());
				}
				String bits = "1010011".repeat(6000);
				statement.execute("INSERT INTO " + schemaName + ".items VALUES (1, B'" + bits + "', B'101', B'1010011', B'1', ARRAY[B'" + bits + "',NULL,B'']::bit varying[])");
				statement.execute("CREATE TABLE " + schemaName + ".domain_items (v " + schemaName + ".v, vs " + schemaName + ".vs, b " + schemaName + ".b, f " + schemaName + ".f)");
				statement.execute("INSERT INTO " + schemaName + ".domain_items SELECT unlimited,bits,bounded,fixed FROM " + schemaName + ".items");
				for (String query : List.of("SELECT unlimited,bits[1],bits[2],bits[3] FROM " + schemaName + ".items", "SELECT v,vs[1],vs[2],vs[3] FROM " + schemaName + ".domain_items")) {
					try (var rows = statement.executeQuery(query)) {
						assertTrue(rows.next()); assertEquals(bits, rows.getString(1)); assertEquals(bits, rows.getString(2));
						assertNull(rows.getString(3)); assertEquals("", rows.getString(4));
					}
				}
				assertEquals("22001", assertThrows(SQLException.class, () -> statement.execute("INSERT INTO " + schemaName + ".items (id,bounded) VALUES (2,B'10101010')")).getSQLState());
				assertEquals("22026", assertThrows(SQLException.class, () -> statement.execute("INSERT INTO " + schemaName + ".items (id,fixed) VALUES (3,B'101')")).getSQLState());
			} finally { statement.execute("DROP SCHEMA " + schemaName + " CASCADE"); }
		}
	}

	@Test
	void preservesNegativeAndExcessNumericScaleAtEngineBoundary() throws Exception {
		String schemaName = "ysql_numeric_scale_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				if (connection.getMetaData().getDatabaseMajorVersion() < 15) {
					SQLException unsupported = assertThrows(SQLException.class,
							() -> statement.execute("CREATE TABLE " + schemaName + ".items (amount numeric(2,-3))"));
					assertEquals("22023", unsupported.getSQLState()); return;
				}
				statement.execute("CREATE TABLE " + schemaName + ".items (amount numeric(2,-3), amounts numeric(2,-3)[], fraction numeric(3,5))");
				statement.execute("CREATE DOMAIN " + schemaName + ".rounded AS numeric(2,-3) DEFAULT 12345");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var registry = dialect.createSqlFactoryRegistry(); registry.getOptions().setDecorateSchemaName(true);
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader(); reader.setSchemaName(schemaName); reader.setObjectName("items");
				Table table = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(reader.getAllFull(connection).get(0).asXml()));
				assertEquals(-3, table.getColumns().get("amount").getScale());
				assertEquals(-3, table.getColumns().get("amounts").getScale());
				assertEquals(5, table.getColumns().get("fraction").getScale());
				statement.execute("DROP TABLE " + schemaName + ".items");
				for (var operation : registry.createSql(table, SqlType.CREATE)) statement.execute(operation.getSqlText());
				var domains = dialect.getCatalogReader().getSchemaReader().getDomainReader(); domains.setSchemaName(schemaName);
				var domain = domains.getAllFull(connection).get(0); assertEquals(-3, domain.getScale());
				com.sqlapp.data.schemas.Domain restored = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(domain.asXml()));
				statement.execute("DROP DOMAIN " + schemaName + ".rounded");
				for (var operation : registry.createSql(restored, SqlType.CREATE)) statement.execute(operation.getSqlText());
				statement.execute("INSERT INTO " + schemaName + ".items VALUES (12345,ARRAY[12345,-12345],0.00123456)");
				try (var rows = statement.executeQuery("SELECT amount,amounts[1],amounts[2],fraction FROM " + schemaName + ".items")) {
					assertTrue(rows.next()); assertEquals(new BigDecimal("12000"), rows.getBigDecimal(1));
					assertEquals(new BigDecimal("12000"), rows.getBigDecimal(2)); assertEquals(new BigDecimal("-12000"), rows.getBigDecimal(3));
					assertEquals(new BigDecimal("0.00123"), rows.getBigDecimal(4));
				}
				statement.execute("CREATE TABLE " + schemaName + ".domain_items (amount " + schemaName + ".rounded)");
				statement.execute("INSERT INTO " + schemaName + ".domain_items DEFAULT VALUES");
				assertEquals(12000, scalar(connection, "SELECT amount FROM " + schemaName + ".domain_items"));
				for (String values : List.of("(99500,NULL,NULL)", "(NULL,NULL,0.01)")) {
					SQLException overflow = assertThrows(SQLException.class, () -> statement.execute("INSERT INTO " + schemaName + ".items VALUES " + values));
					assertEquals("22003", overflow.getSQLState());
				}
			} finally { statement.execute("DROP SCHEMA " + schemaName + " CASCADE"); }
		}
	}

	@Test
	void recreatesArrayDomainsWithPrecisionDefaultsAndConstraints() throws Exception {
		String schemaName = "ysql_array_domains_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE DOMAIN " + schemaName + ".\"Amount Grid\" AS numeric(12,3)[][] "
						+ "DEFAULT '{{1.234,NULL},{2.345,3.456}}'::numeric[] NOT NULL "
						+ "CHECK (array_length(VALUE,1)=2 AND VALUE[1][1]>=0)");
				statement.execute("CREATE DOMAIN " + schemaName + ".\"Label Grid\" AS varchar(7)[][]");
				statement.execute("CREATE DOMAIN " + schemaName + ".\"Moment Grid\" AS timestamp(3)[][]");
				statement.execute("CREATE DOMAIN " + schemaName + ".\"Whole Moment\" AS timestamp(0)");
				for (String name : List.of("Amount Grid", "Label Grid", "Moment Grid", "Whole Moment")) {
					statement.execute("COMMENT ON DOMAIN " + schemaName + ".\"" + name + "\" IS '配列 O''Brien'");
				}
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getDomainReader();
				reader.setSchemaName(schemaName);
				var domains = reader.getAllFull(connection);
				assertEquals(4, domains.size());
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var domain : domains) {
					com.sqlapp.data.schemas.Domain restored = com.sqlapp.data.schemas.SchemaUtils.readXml(
							new java.io.StringReader(domain.asXml()));
					assertEquals("配列 O'Brien", restored.getRemarks());
					assertEquals(domain.getName().equals("Whole Moment") ? 0 : 2, restored.getArrayDimension());
					switch (domain.getName()) {
						case "Amount Grid" -> {
							assertEquals(12L, restored.getLength()); assertEquals(3, restored.getScale());
							assertTrue(restored.isNotNull()); assertNotNull(restored.getDefaultValue());
							assertNotNull(restored.getCheck());
						}
						case "Label Grid" -> assertEquals(7L, restored.getLength());
						case "Moment Grid" -> assertEquals(3L, restored.getLength());
						case "Whole Moment" -> assertEquals(0L, restored.getLength());
						default -> fail(domain.getName());
					}
					statement.execute("DROP DOMAIN " + schemaName + ".\"" + domain.getName() + "\"");
					for (var operation : registry.createSql(restored, SqlType.CREATE)) statement.execute(operation.getSqlText());
				}
				var recreated = reader.getAllFull(connection);
				for (var domain : domains) {
					var actual = recreated.stream().filter(d -> d.getName().equals(domain.getName())).findFirst().orElseThrow();
					assertEquals(domain.getDataType(), actual.getDataType(), domain.getName());
					assertEquals(domain.getArrayDimension(), actual.getArrayDimension(), domain.getName());
					assertEquals(domain.getLength(), actual.getLength(), domain.getName());
					assertEquals(domain.getScale(), actual.getScale(), domain.getName());
					assertEquals(domain.getRemarks(), actual.getRemarks(), domain.getName());
				}
				statement.execute("CREATE TABLE " + schemaName + ".items (amounts " + schemaName + ".\"Amount Grid\", "
						+ "labels " + schemaName + ".\"Label Grid\", moments " + schemaName + ".\"Moment Grid\", "
						+ "whole " + schemaName + ".\"Whole Moment\")");
				statement.execute("INSERT INTO " + schemaName + ".items(labels,moments,whole) VALUES "
						+ "(ARRAY[['O''Brien',NULL],['雪','quote\"']], "
						+ "ARRAY[['2024-02-29 12:34:56.123456'::timestamp,NULL],['2000-01-01'::timestamp,'1970-01-01'::timestamp]], "
						+ "'2024-02-29 12:34:56.123456')");
				try (var rows = statement.executeQuery("SELECT amounts[1][1],amounts[1][2],labels[1][1], "
						+ "moments[1][1],whole FROM " + schemaName + ".items")) {
					assertTrue(rows.next()); assertEquals(new BigDecimal("1.234"), rows.getBigDecimal(1));
					assertNull(rows.getBigDecimal(2)); assertEquals("O'Brien", rows.getString(3));
					assertEquals(java.sql.Timestamp.valueOf("2024-02-29 12:34:56.123"), rows.getTimestamp(4));
					assertEquals(java.sql.Timestamp.valueOf("2024-02-29 12:34:56"), rows.getTimestamp(5));
				}
				for (String invalid : List.of("NULL", "ARRAY[[-1,2],[3,4]]", "ARRAY[[1,2]]")) {
					SQLException error = assertThrows(SQLException.class,
							() -> statement.execute("INSERT INTO " + schemaName + ".items(amounts) VALUES (" + invalid + ")"));
					assertEquals(invalid.equals("NULL") ? "23502" : "23514", error.getSQLState());
				}
			} finally { statement.execute("DROP SCHEMA " + schemaName + " CASCADE"); }
		}
	}

	@Test
	void recreatesDomainsOverQuotedUserDefinedTypesOutsideSearchPath() throws Exception {
		String schemaName = "Ysql Custom " + UUID.randomUUID().toString().replace("-", "");
		String schemaSql = "\"" + schemaName + "\"";
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaSql);
			try {
				String typeName = schemaSql + ".\"State.Type\"";
				statement.execute("CREATE TYPE " + typeName + " AS ENUM ('ready','O''Brien')");
				statement.execute("CREATE DOMAIN " + schemaSql + ".\"State List\" AS " + typeName
						+ "[] DEFAULT ARRAY['ready','O''Brien']::" + typeName + "[] CHECK (cardinality(VALUE)<=2)");
				statement.execute("CREATE DOMAIN " + schemaSql + ".\"Single State\" AS " + typeName + " DEFAULT 'ready'");
				statement.execute("SET search_path TO " + schemaSql + ",pg_catalog,public");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getDomainReader();
				reader.setSchemaName(schemaName);
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				var domains = reader.getAllFull(connection).stream().filter(d -> d.getDataType() != DataType.ENUM).toList();
				assertEquals(2, domains.size());
				statement.execute("SET search_path TO pg_catalog,public");
				for (var domain : domains) {
					com.sqlapp.data.schemas.Domain restored = com.sqlapp.data.schemas.SchemaUtils.readXml(
							new java.io.StringReader(domain.asXml()));
					assertEquals(typeName, restored.getDataTypeName());
					assertEquals(domain.getName().equals("State List") ? 1 : 0, restored.getArrayDimension());
					statement.execute("DROP DOMAIN " + schemaSql + ".\"" + domain.getName() + "\"");
					for (var operation : registry.createSql(restored, SqlType.CREATE)) statement.execute(operation.getSqlText());
				}
				statement.execute("CREATE TABLE " + schemaSql + ".items (states " + schemaSql
						+ ".\"State List\", state " + schemaSql + ".\"Single State\")");
				statement.execute("INSERT INTO " + schemaSql + ".items DEFAULT VALUES");
				try (var rows = statement.executeQuery("SELECT states[1]::text,states[2]::text,state::text FROM " + schemaSql + ".items")) {
					assertTrue(rows.next()); assertEquals("ready", rows.getString(1));
					assertEquals("O'Brien", rows.getString(2)); assertEquals("ready", rows.getString(3));
				}
				SQLException check = assertThrows(SQLException.class, () -> statement.execute("INSERT INTO "
						+ schemaSql + ".items(states) VALUES (ARRAY['ready','ready','ready']::" + typeName + "[])"));
				assertEquals("23514", check.getSQLState());
				SQLException invalid = assertThrows(SQLException.class, () -> statement.execute("INSERT INTO "
						+ schemaSql + ".items(state) VALUES ('invalid')"));
				assertEquals("22P02", invalid.getSQLState());
			} finally { statement.execute("DROP SCHEMA " + schemaSql + " CASCADE"); }
		}
	}

	@Test
	void recreatesIndexCommentsThroughStandaloneAndTableFactories() throws Exception {
		String schemaName = "ysql_index_comment_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE TABLE " + schemaName + ".items (id integer PRIMARY KEY, label text)");
				statement.execute("CREATE UNIQUE INDEX label_idx ON " + schemaName + ".items(label)");
				statement.execute("COMMENT ON INDEX " + schemaName + ".label_idx IS '索引 ''comment'''");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName(schemaName); reader.setObjectName("items");
				Table table = reader.getAllFull(connection).stream().filter(t -> "items".equals(t.getName())).findFirst().orElseThrow();
				var index = table.getIndexes().get("label_idx");
				assertNotNull(index); assertEquals("索引 'comment'", index.getRemarks());
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				statement.execute("DROP INDEX " + schemaName + ".label_idx");
				for (var operation : registry.createSql(index, SqlType.CREATE)) statement.execute(operation.getSqlText());
				Table afterIndex = reader.getAllFull(connection).stream().filter(t -> "items".equals(t.getName())).findFirst().orElseThrow();
				assertEquals(index.getRemarks(), afterIndex.getIndexes().get("label_idx").getRemarks());
				statement.execute("DROP TABLE " + schemaName + ".items");
				for (var operation : registry.createSql(table, SqlType.CREATE)) statement.execute(operation.getSqlText());
				Table afterTable = reader.getAllFull(connection).stream().filter(t -> "items".equals(t.getName())).findFirst().orElseThrow();
				assertEquals(index.getRemarks(), afterTable.getIndexes().get("label_idx").getRemarks());
				statement.execute("INSERT INTO " + schemaName + ".items VALUES (1, 'same')");
				SQLException duplicate = assertThrows(SQLException.class,
						() -> statement.execute("INSERT INTO " + schemaName + ".items VALUES (2, 'same')"));
				assertEquals("23505", duplicate.getSQLState());
			} finally { statement.execute("DROP SCHEMA " + schemaName + " CASCADE"); }
		}
	}

	@Test
	void copiesAndUpsertsUuidNumericBooleanDateAndTimestampArrays() throws Exception {
		String schemaName = "ysql_arrays_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE TABLE " + schemaName + ".items (id integer PRIMARY KEY, ids uuid[], "
						+ "amounts numeric(12,3)[], flags boolean[], dates date[], moments timestamp(6)[])");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName(schemaName); reader.setObjectName("items");
				Table table = reader.getAllFull(connection).stream().filter(t -> "items".equals(t.getName())).findFirst().orElseThrow();
				String[] columns = { "ids", "amounts", "flags", "dates", "moments" };
				Object[][] arrays = {
					{ UUID.fromString("12345678-1234-5678-9abc-123456789abc"), null },
					{ new BigDecimal("-123456.789"), null, new BigDecimal("0.001") },
					{ true, null, false },
					{ java.sql.Date.valueOf("2024-02-29"), null, java.sql.Date.valueOf("1970-01-01") },
					{ java.sql.Timestamp.valueOf("2024-02-29 12:34:56.123456"), null }
				};
				table.getRows().add(r -> { r.put("id", 1); for (String column : columns) r.put(column, null); });
				table.getRows().add(r -> { r.put("id", 2); for (String column : columns) r.put(column, new Object[0]); });
				table.getRows().add(r -> {
					r.put("id", 3); for (int i = 0; i < columns.length; i++) r.put(columns[i], arrays[i]);
				});
				assertEquals(3, BulkInsertResolver.execute(connection, table, BulkOption.defaults()));
				try (var rows = statement.executeQuery("SELECT * FROM " + schemaName + ".items ORDER BY id")) {
					assertTrue(rows.next()); for (String column : columns) assertNull(rows.getArray(column));
					assertTrue(rows.next());
					for (String column : columns) {
						var array = rows.getArray(column);
						try { assertEquals(0, ((Object[]) array.getArray()).length); } finally { array.free(); }
					}
					assertTrue(rows.next());
					for (int i = 0; i < columns.length; i++) {
						var array = rows.getArray(columns[i]);
						try { assertArrayEquals(arrays[i], (Object[]) array.getArray(), columns[i]); } finally { array.free(); }
					}
				}
				table.getRows().clear();
				table.getRows().add(r -> {
					r.put("id", 1); for (int i = 0; i < columns.length; i++) r.put(columns[i], arrays[i]);
				});
				assertEquals(1, BulkUpsertResolver.execute(connection, table, BulkUpsertOption.defaults()));
				try (var rows = statement.executeQuery("SELECT * FROM " + schemaName + ".items WHERE id=1")) {
					assertTrue(rows.next());
					for (int i = 0; i < columns.length; i++) {
						var array = rows.getArray(columns[i]);
						try { assertArrayEquals(arrays[i], (Object[]) array.getArray(), columns[i]); } finally { array.free(); }
					}
				}
			} finally { statement.execute("DROP SCHEMA " + schemaName + " CASCADE"); }
		}
	}

	@Test
	void recreatesAndBulkLoadsMultidimensionalTypedArrays() throws Exception {
		String schemaName = "ysql_nested_types_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE TABLE " + schemaName + ".items (id integer PRIMARY KEY, ids uuid[][], "
						+ "amounts numeric(12,3)[][], flags boolean[][], dates date[][], moments timestamp(3)[][], payloads bytea[][], labels varchar(7)[][])");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName(schemaName); reader.setObjectName("items");
				Table original = reader.getAllFull(connection).get(0);
				Table table = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(original.asXml()));
				String[] columns = { "ids", "amounts", "flags", "dates", "moments", "payloads", "labels" };
				for (String column : columns) assertEquals(2, table.getColumns().get(column).getArrayDimension(), column);
				assertEquals(12L, table.getColumns().get("amounts").getLength());
				assertEquals(3, table.getColumns().get("amounts").getScale());
				assertEquals(3L, table.getColumns().get("moments").getLength());
				assertEquals(7L, table.getColumns().get("labels").getLength());
				statement.execute("DROP TABLE " + schemaName + ".items");
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var operation : registry.createSql(table, SqlType.CREATE)) statement.execute(operation.getSqlText());
				Table recreated = reader.getAllFull(connection).get(0);
				for (String column : columns) {
					assertEquals(2, recreated.getColumns().get(column).getArrayDimension(), column);
					assertEquals(table.getColumns().get(column).getDataType(), recreated.getColumns().get(column).getDataType(), column);
				}
				for (String column : List.of("amounts", "moments", "labels")) {
					assertEquals(table.getColumns().get(column).getLength(), recreated.getColumns().get(column).getLength(), column);
					assertEquals(table.getColumns().get(column).getScale(), recreated.getColumns().get(column).getScale(), column);
				}
				Object[][][] matrices = {
					{ { UUID.fromString("12345678-1234-5678-9abc-123456789abc"), null }, { null, UUID.fromString("00000000-0000-0000-0000-000000000000") } },
					{ { new BigDecimal("-123456.789"), null }, { new BigDecimal("0.001"), new BigDecimal("999999999.999") } },
					{ { true, null }, { false, true } },
					{ { java.sql.Date.valueOf("2024-02-29"), null }, { java.sql.Date.valueOf("1970-01-01"), java.sql.Date.valueOf("2000-01-01") } },
					{ { java.sql.Timestamp.valueOf("2024-02-29 12:34:56.123"), null }, { java.sql.Timestamp.valueOf("1970-01-01 00:00:00.001"), java.sql.Timestamp.valueOf("2000-01-01 23:59:59.999") } },
					{ { new byte[] {0, (byte)255, 34, 92}, null }, { new byte[0], new byte[] {1, 2} } },
					{ { "O'Brien", null }, { "雪☃", "a\\b\"c" } }
				};
				for (int phase = 0; phase < 2; phase++) {
					table.getRows().clear();
					int nullId = phase == 0 ? 1 : 2;
					int emptyId = phase == 0 ? 2 : 3;
					int matrixId = phase == 0 ? 3 : 1;
					table.getRows().add(r -> { r.put("id", nullId); for (String column : columns) r.put(column, null); });
					table.getRows().add(r -> { r.put("id", emptyId); for (String column : columns) r.put(column, new Object[0]); });
					table.getRows().add(r -> { r.put("id", matrixId); for (int i = 0; i < columns.length; i++) r.put(columns[i], matrices[i]); });
					assertEquals(3, phase == 0
							? BulkInsertResolver.execute(connection, table, BulkOption.defaults())
							: BulkUpsertResolver.execute(connection, table, BulkUpsertOption.defaults()));
					try (var rows = statement.executeQuery("SELECT * FROM " + schemaName + ".items ORDER BY id")) {
						for (int id = 1; id <= 3; id++) {
							assertTrue(rows.next()); assertEquals(id, rows.getInt("id"));
							for (int i = 0; i < columns.length; i++) {
								var array = rows.getArray(columns[i]);
								if (id == nullId) { assertNull(array, columns[i]); continue; }
								assertNotNull(array, columns[i]);
								try {
									Object[] actual = (Object[]) array.getArray();
									if (id == emptyId) assertEquals(0, actual.length, columns[i]);
									else assertArrayEquals(matrices[i], actual, columns[i]);
								} finally { array.free(); }
							}
						}
						assertFalse(rows.next());
					}
				}
			} finally { statement.execute("DROP SCHEMA " + schemaName + " CASCADE"); }
		}
	}

	@Test
	void recreatesCompositeTypeDefinitionAttributesCollationAndComments() throws Exception {
		String schemaName = "ysql_composite_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE TYPE " + schemaName + ".status AS ENUM ('new', 'done')");
				statement.execute("CREATE DOMAIN " + schemaName + ".positive AS numeric(12,2) CHECK (VALUE > 0)");
				statement.execute("CREATE TYPE " + schemaName + ".\"Order item\" AS (\"Item name\" text COLLATE \"C\", "
						+ "amount numeric(12,2), tags text[][], status " + schemaName + ".status, states "
						+ schemaName + ".status[], valid " + schemaName + ".positive)");
				statement.execute("COMMENT ON TYPE " + schemaName + ".\"Order item\" IS '複合型 ''comment'''");
				statement.execute("COMMENT ON COLUMN " + schemaName + ".\"Order item\".\"Item name\" IS '属性 ''comment'''");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getTypeReader();
				reader.setSchemaName(schemaName); reader.setObjectName("Order item");
				statement.execute("SET search_path TO " + schemaName + ", pg_catalog");
				var type = reader.getAllFull(connection).stream().filter(t -> "Order item".equals(t.getName())).findFirst().orElseThrow();
				statement.execute("RESET search_path");
				assertEquals("複合型 'comment'", type.getRemarks());
				assertEquals(6, type.getColumns().size());
				assertEquals(2, type.getColumns().get("tags").getArrayDimension());
				assertEquals(1, type.getColumns().get("states").getArrayDimension());
				assertEquals("属性 'comment'", type.getColumns().get("Item name").getRemarks());
				String definition = String.join("\n", type.getDefinition());
				assertTrue(definition.contains("CREATE TYPE"), definition);
				assertTrue(definition.contains("COLLATE"), definition);
				assertTrue(definition.contains(schemaName + ".status"), definition);
				statement.execute("DROP TYPE " + schemaName + ".\"Order item\"");
				var registry = dialect.createSqlFactoryRegistry(); registry.getOptions().setDecorateSchemaName(true);
				for (var operation : registry.createSql(type, SqlType.CREATE)) statement.execute(operation.getSqlText());
				var recreated = reader.getAllFull(connection).stream().filter(t -> "Order item".equals(t.getName())).findFirst().orElseThrow();
				assertEquals(type.getRemarks(), recreated.getRemarks());
				assertEquals(6, recreated.getColumns().size());
				assertEquals(type.getColumns().get("tags").getArrayDimension(), recreated.getColumns().get("tags").getArrayDimension());
				assertEquals(type.getColumns().get("states").getArrayDimension(), recreated.getColumns().get("states").getArrayDimension());
				assertEquals(definition, String.join("\n", recreated.getDefinition()));
				assertEquals(type.getColumns().get("Item name").getRemarks(), recreated.getColumns().get("Item name").getRemarks());
				String value = "ROW('日本語',12.34,ARRAY[['tag',NULL]]::text[],'new'::" + schemaName
						+ ".status,ARRAY['done']::" + schemaName + ".status[],5.67::" + schemaName + ".positive)::"
						+ schemaName + ".\"Order item\"";
				try (var rows = statement.executeQuery("SELECT (value).\"Item name\", (value).amount, (value).status::text, "
						+ "(value).states[1]::text, (value).valid FROM (SELECT " + value + " AS value) data")) {
					assertTrue(rows.next()); assertEquals("日本語", rows.getString(1));
					assertEquals(new BigDecimal("12.34"), rows.getBigDecimal(2));
					assertEquals("new", rows.getString(3)); assertEquals("done", rows.getString(4));
					assertEquals(new BigDecimal("5.67"), rows.getBigDecimal(5));
				}
			} finally {
				statement.execute("RESET search_path");
				statement.execute("DROP SCHEMA " + schemaName + " CASCADE");
			}
		}
	}

	@Test
	void recreatesViewAndColumnCommentsWithQuotedNames() throws Exception {
		String schemaName = "ysql_view_comments_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE TABLE " + schemaName + ".items (id integer, label text)");
				statement.execute("INSERT INTO " + schemaName + ".items VALUES (7, '日本語')");
				statement.execute("CREATE VIEW " + schemaName + ".\"Item view\" AS SELECT id AS \"Row id\", label AS \"Display name\" FROM " + schemaName + ".items");
				statement.execute("COMMENT ON VIEW " + schemaName + ".\"Item view\" IS 'ビュー ''comment'''");
				statement.execute("COMMENT ON COLUMN " + schemaName + ".\"Item view\".\"Display name\" IS '表示列 ''comment'''");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getViewReader();
				reader.setSchemaName(schemaName); reader.setObjectName("Item view");
				var view = reader.getAllFull(connection).stream().filter(t -> "Item view".equals(t.getName())).findFirst().orElseThrow();
				assertEquals("ビュー 'comment'", view.getRemarks());
				assertEquals("表示列 'comment'", view.getColumns().get("Display name").getRemarks());
				statement.execute("DROP VIEW " + schemaName + ".\"Item view\"");
				var registry = dialect.createSqlFactoryRegistry(); registry.getOptions().setDecorateSchemaName(true);
				for (var operation : registry.createSql(view, SqlType.CREATE)) statement.execute(operation.getSqlText());
				var recreated = reader.getAllFull(connection).stream().filter(t -> "Item view".equals(t.getName())).findFirst().orElseThrow();
				assertEquals(view.getRemarks(), recreated.getRemarks());
				assertEquals(view.getColumns().get("Display name").getRemarks(), recreated.getColumns().get("Display name").getRemarks());
				try (var rows = statement.executeQuery("SELECT * FROM " + schemaName + ".\"Item view\"")) {
					assertTrue(rows.next()); assertEquals(7, rows.getInt(1)); assertEquals("日本語", rows.getString(2));
				}
			} finally { statement.execute("DROP SCHEMA " + schemaName + " CASCADE"); }
		}
	}

	@Test
	void separatesSameNamedForeignKeysAndPreservesConstraintOrderAndComments() throws Exception {
		String schemaName = "ysql_constraints_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				for (int i = 1; i <= 2; i++) {
					statement.execute("CREATE TABLE " + schemaName + ".parent" + i + " (a integer, b integer, PRIMARY KEY (b,a))");
					statement.execute("INSERT INTO " + schemaName + ".parent" + i + " VALUES (" + i + ", " + (i * 10) + ")");
					statement.execute("CREATE TABLE " + schemaName + ".child" + i + " (id integer PRIMARY KEY, parent_a integer, parent_b integer, "
							+ "label text, CONSTRAINT \"Parent link\" FOREIGN KEY (parent_b,parent_a) REFERENCES " + schemaName + ".parent" + i
							+ "(b,a), CONSTRAINT \"Positive id\" CHECK (id > 0), CONSTRAINT \"Always ok\" CHECK (true), "
							+ "CONSTRAINT label_unique_" + i + " UNIQUE (label,parent_b))");
					for (String name : List.of("Parent link", "Positive id", "Always ok", "label_unique_" + i)) {
						statement.execute("COMMENT ON CONSTRAINT \"" + name + "\" ON " + schemaName + ".child" + i + " IS '制約 ''comment'''");
					}
				}
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader(); reader.setSchemaName(schemaName);
				var schema = reader.getAllFull(connection).stream().filter(t -> schemaName.equals(t.getName())).findFirst().orElseThrow();
				var registry = dialect.createSqlFactoryRegistry(); registry.getOptions().setDecorateSchemaName(true);
				for (int i = 1; i <= 2; i++) {
					var parent = schema.getTables().get("parent" + i);
					assertEquals(List.of("b", "a"), parent.getConstraints().getPrimaryKeyConstraint().getColumns().stream().map(c -> c.getName()).toList());
					var child = schema.getTables().get("child" + i);
					var fk = (ForeignKeyConstraint) child.getConstraints().get("Parent link");
					assertNotNull(fk);
					assertEquals(List.of("parent_b", "parent_a"), fk.getColumns().stream().map(c -> c.getName()).toList());
					assertEquals("parent" + i, fk.getRelatedColumns().get(0).getTableName());
					for (String name : List.of("Parent link", "Positive id", "Always ok", "label_unique_" + i)) {
						assertNotNull(child.getConstraints().get(name));
						assertEquals("制約 'comment'", child.getConstraints().get(name).getRemarks());
					}
					assertEquals(List.of("label", "parent_b"), ((com.sqlapp.data.schemas.UniqueConstraint) child.getConstraints()
							.get("label_unique_" + i)).getColumns().stream().map(c -> c.getName()).toList());
					statement.execute("DROP TABLE " + schemaName + ".child" + i);
					statement.execute("DROP TABLE " + schemaName + ".parent" + i);
					for (var operation : registry.createSql(parent, SqlType.CREATE)) statement.execute(operation.getSqlText());
					statement.execute("INSERT INTO " + schemaName + ".parent" + i + " VALUES (" + i + "," + i * 10 + ")");
					for (var operation : registry.createSql(child, SqlType.CREATE)) statement.execute(operation.getSqlText());
					statement.execute("INSERT INTO " + schemaName + ".child" + i + " VALUES (1," + i + "," + i * 10 + ",'valid')");
					final int childIndex = i;
					SQLException invalid = assertThrows(SQLException.class, () -> statement.execute("INSERT INTO " + schemaName
							+ ".child" + childIndex + " VALUES (2,999,999,'orphan')"));
					assertEquals("23503", invalid.getSQLState());
					SQLException badCheck = assertThrows(SQLException.class, () -> statement.execute("INSERT INTO " + schemaName
							+ ".child" + childIndex + " VALUES (-1,NULL,NULL,'bad')"));
					assertEquals("23514", badCheck.getSQLState());
					SQLException duplicate = assertThrows(SQLException.class, () -> statement.execute("INSERT INTO " + schemaName
							+ ".child" + childIndex + " VALUES (3," + childIndex + "," + childIndex * 10 + ",'valid')"));
					assertEquals("23505", duplicate.getSQLState());
				}
				var recreated = reader.getAllFull(connection).stream().filter(t -> schemaName.equals(t.getName())).findFirst().orElseThrow();
				for (int i = 1; i <= 2; i++) for (String name : List.of("Parent link", "Positive id", "Always ok", "label_unique_" + i)) {
					assertEquals("制約 'comment'", recreated.getTables().get("child" + i).getConstraints().get(name).getRemarks());
				}
			} finally { statement.execute("DROP SCHEMA " + schemaName + " CASCADE"); }
		}
	}


	@Test
	void recreatesNoInheritChecksAndStandaloneConstraintComments() throws Exception {
		String schemaName = "ysql_constraint_notes_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE TABLE " + schemaName + ".parent(id integer PRIMARY KEY)");
				statement.execute("INSERT INTO " + schemaName + ".parent VALUES (1)");
				String child = schemaName + ".child";
				statement.execute("CREATE TABLE " + child + " (id integer, amount integer, CONSTRAINT \"Unique Key\" UNIQUE(id),"
						+ "CONSTRAINT \"Parent FK\" FOREIGN KEY(id) REFERENCES " + schemaName + ".parent(id))");
				statement.execute("ALTER TABLE " + child + " ADD CONSTRAINT \"Positive Check\" CHECK(amount >= 0) NO INHERIT NOT VALID");
				for (String name : List.of("Unique Key","Parent FK","Positive Check")) {
					statement.execute("COMMENT ON CONSTRAINT \"" + name + "\" ON " + child + " IS '日本語 O''Brien'");
				}
				statement.execute("COMMENT ON INDEX " + schemaName + ".\"Unique Key\" IS 'index O''Brien'");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName(schemaName); reader.setObjectName("child");
				var model = reader.getAllFull(connection).stream().filter(t -> "child".equals(t.getName())).findFirst().orElseThrow();
				assertEquals("true", model.getConstraints().get("Positive Check").getSpecifics().get("noInherit"));
				Table restored = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(model.asXml()));
				var registry = dialect.createSqlFactoryRegistry(); registry.getOptions().setDecorateSchemaName(true);
				for (var constraint : restored.getConstraints()) {
					statement.execute("ALTER TABLE " + child + " DROP CONSTRAINT \"" + constraint.getName() + "\"");
					for (var operation : registry.createSql(constraint, SqlType.CREATE)) statement.execute(operation.getSqlText());
				}
				var reread = reader.getAllFull(connection).stream().filter(t -> "child".equals(t.getName())).findFirst().orElseThrow();
				for (var constraint : reread.getConstraints()) assertEquals("日本語 O'Brien", constraint.getRemarks());
				assertEquals("index O'Brien", reread.getIndexes().get("Unique Key").getRemarks());
				assertEquals("true", reread.getConstraints().get("Positive Check").getSpecifics().get("noInherit"));
				assertEquals("true", reread.getConstraints().get("Positive Check").getSpecifics().get("notValid"));
				try (var rows = statement.executeQuery("SELECT connoinherit,convalidated FROM pg_catalog.pg_constraint c "
						+ "JOIN pg_catalog.pg_namespace n ON n.oid=c.connamespace WHERE n.nspname='" + schemaName + "' AND c.conname='Positive Check'")) {
					assertTrue(rows.next()); assertTrue(rows.getBoolean(1)); assertFalse(rows.getBoolean(2));
				}
				statement.execute("INSERT INTO " + child + " VALUES (1,1)");
				assertEquals("23514", assertThrows(SQLException.class, () -> statement.execute("INSERT INTO " + child + " VALUES (NULL,-1)")).getSQLState());
				assertEquals("23503", assertThrows(SQLException.class, () -> statement.execute("INSERT INTO " + child + " VALUES (2,1)")).getSQLState());
				assertEquals("23505", assertThrows(SQLException.class, () -> statement.execute("INSERT INTO " + child + " VALUES (1,2)")).getSQLState());
				statement.execute("DROP TABLE " + child);
				for (var operation : registry.createSql(restored, SqlType.CREATE)) statement.execute(operation.getSqlText());
				var tableReread = reader.getAllFull(connection).stream().filter(t -> "child".equals(t.getName())).findFirst().orElseThrow();
				assertEquals("true", tableReread.getConstraints().get("Positive Check").getSpecifics().get("noInherit"));
				for (var constraint : tableReread.getConstraints()) assertEquals("日本語 O'Brien", constraint.getRemarks());
			} finally { statement.execute("DROP SCHEMA " + schemaName + " CASCADE"); }
		}
	}

	@Test
	void recreatesCoveringConstraintsThroughXmlWithoutChangingUniqueness() throws Exception {
		String schemaName = "ysql_covering_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				boolean nullsNotDistinct = connection.getMetaData().getDatabaseMajorVersion() >= 15;
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader(); reader.setSchemaName(schemaName);
				var registry = dialect.createSqlFactoryRegistry(); registry.getOptions().setDecorateSchemaName(true);
				for (boolean primary : new boolean[] {true,false}) {
					String name = primary ? "primary_case" : "unique_case";
					String qualified = schemaName + "." + name;
					statement.execute("CREATE TABLE " + qualified + " (a integer,b integer,\"Payload Value\" text, CONSTRAINT covering_key "
							+ (primary ? "PRIMARY KEY" : "UNIQUE" + (nullsNotDistinct ? " NULLS NOT DISTINCT" : ""))
							+ " (b,a) INCLUDE (\"Payload Value\"))");
					reader.setObjectName(name);
					var model = reader.getAllFull(connection).stream().filter(t -> name.equals(t.getName())).findFirst().orElseThrow();
					var key = (com.sqlapp.data.schemas.UniqueConstraint) model.getConstraints().get("covering_key");
					assertEquals(List.of("b","a"), key.getColumns().stream().map(c -> c.getName()).toList());
					assertEquals(List.of("Payload Value"), key.getIndex().getIncludes().stream().map(c -> c.getName()).toList());
					assertEquals(!primary && nullsNotDistinct ? "true" : null, key.getSpecifics().get("nullsNotDistinct"));
					Table restored = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(model.asXml()));
					var restoredKey = (com.sqlapp.data.schemas.UniqueConstraint) restored.getConstraints().get("covering_key");
					assertEquals(List.of("Payload Value"), restoredKey.getIndex().getIncludes().stream().map(c -> c.getName()).toList());
					statement.execute("DROP TABLE " + qualified);
					for (var operation : registry.createSql(restored, SqlType.CREATE)) statement.execute(operation.getSqlText());
					statement.execute("INSERT INTO " + qualified + " VALUES (1,2,'first')");
					assertEquals("23505", assertThrows(SQLException.class,
							() -> statement.execute("INSERT INTO " + qualified + " VALUES (1,2,'different payload')")).getSQLState());
					if (!primary) {
						statement.execute("INSERT INTO " + qualified + " VALUES (NULL,3,'null first')");
						if (nullsNotDistinct) assertEquals("23505", assertThrows(SQLException.class,
								() -> statement.execute("INSERT INTO " + qualified + " VALUES (NULL,3,'null second')")).getSQLState());
						else statement.execute("INSERT INTO " + qualified + " VALUES (NULL,3,'null second')");
						statement.execute("ALTER TABLE " + qualified + " DROP CONSTRAINT covering_key");
						for (var operation : registry.createSql(restoredKey, SqlType.CREATE)) statement.execute(operation.getSqlText());
					}
					statement.execute("DROP TABLE " + qualified);
				}
			} finally { statement.execute("DROP SCHEMA " + schemaName + " CASCADE"); }
		}
	}

	@Test
	void preservesCompositeMatchAndAvoidsKeywordFalsePositives() throws Exception {
		String schemaName = "ysql_match_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE TABLE " + schemaName + ".parent (a integer,b integer,PRIMARY KEY (a,b))");
				statement.execute("INSERT INTO " + schemaName + ".parent VALUES (1,2)");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName(schemaName);
				var registry = dialect.createSqlFactoryRegistry(); registry.getOptions().setDecorateSchemaName(true);
				for (String mode : List.of("FULL", "SIMPLE")) {
					String name = "child_" + mode.toLowerCase(java.util.Locale.ROOT);
					String child = schemaName + "." + name;
					statement.execute("CREATE TABLE " + child + " (period_id integer, b integer, label text, "
							+ "CONSTRAINT parent_fk FOREIGN KEY(period_id,b) REFERENCES " + schemaName + ".parent(a,b) MATCH " + mode
							+ ",CONSTRAINT message_check CHECK (label <> 'NOT ENFORCED'))");
					reader.setObjectName(name);
					var model = reader.getAllFull(connection).stream().filter(t -> name.equals(t.getName())).findFirst().orElseThrow();
					var fk = (ForeignKeyConstraint) model.getConstraints().get("parent_fk");
					assertEquals(mode, fk.getMatchOption().getSqlValue());
					assertNull(fk.getSpecifics().get("period"));
					assertNull(model.getConstraints().get("message_check").getSpecifics().get("notEnforced"));
					statement.execute("DROP TABLE " + child);
					for (var operation : registry.createSql(model, SqlType.CREATE)) statement.execute(operation.getSqlText());
					// Exercise the standalone path as well as inline table generation.
					statement.execute("ALTER TABLE " + child + " DROP CONSTRAINT parent_fk");
					for (var operation : registry.createSql(fk, SqlType.CREATE)) statement.execute(operation.getSqlText());
					statement.execute("INSERT INTO " + child + " VALUES (1,2,'ok'),(NULL,NULL,'ok')");
					if (mode.equals("FULL")) assertEquals("23503", assertThrows(SQLException.class,
							() -> statement.execute("INSERT INTO " + child + " VALUES (1,NULL,'ok')")).getSQLState());
					else statement.execute("INSERT INTO " + child + " VALUES (1,NULL,'ok')");
					assertEquals("23514", assertThrows(SQLException.class,
							() -> statement.execute("INSERT INTO " + child + " VALUES (1,2,'NOT ENFORCED')")).getSQLState());
				}
			} finally { statement.execute("DROP SCHEMA " + schemaName + " CASCADE"); }
		}
	}

	@Test
	void documentsUnsupportedDeferredPrimaryAndUniqueConstraints() throws Exception {
		String schemaName = "ysql_deferred_unique_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (boolean primary : new boolean[] {true,false}) {
					Table table = new Table(primary ? "primary_case" : "unique_case").setSchemaName(schemaName);
					table.getColumns().add("id", c -> c.setDataType(DataType.INT));
					var key = new com.sqlapp.data.schemas.UniqueConstraint("deferred_key", primary);
					key.getColumns().add(table.getColumns().get("id"));
					key.setDeferrability(com.sqlapp.data.schemas.Deferrability.InitiallyDeferred);
					table.getConstraints().add(key);
					String sql = registry.createSql(table, SqlType.CREATE).get(0).getSqlText();
					assertTrue(sql.contains("DEFERRABLE INITIALLY DEFERRED"), sql);
					assertEquals("0A000", assertThrows(SQLException.class, () -> statement.execute(sql)).getSQLState());
				}
			} finally { statement.execute("DROP SCHEMA " + schemaName + " CASCADE"); }
		}
	}

	@Test
	void preservesEveryForeignKeyReferentialAction() throws Exception {
		String schemaName = "ysql_actions_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader(); reader.setSchemaName(schemaName);
				var registry = dialect.createSqlFactoryRegistry(); registry.getOptions().setDecorateSchemaName(true);
				int index = 0;
				for (var rule : com.sqlapp.data.schemas.CascadeRule.values()) {
					String parent = schemaName + ".parent" + index;
					String name = "child" + index++;
					String child = schemaName + "." + name;
					String action = rule == com.sqlapp.data.schemas.CascadeRule.None ? "NO ACTION" : rule.getSqlValue();
					statement.execute("CREATE TABLE " + parent + " (id integer PRIMARY KEY)");
					statement.execute("INSERT INTO " + parent + " VALUES (0),(1)");
					statement.execute("CREATE TABLE " + child + " (id integer PRIMARY KEY, parent_id integer DEFAULT 0, "
							+ "CONSTRAINT parent_fk FOREIGN KEY (parent_id) REFERENCES " + parent + "(id) ON DELETE " + action
							+ " ON UPDATE " + action + " DEFERRABLE INITIALLY DEFERRED)");
					reader.setObjectName(name);
					var model = reader.getAllFull(connection).stream().filter(t -> name.equals(t.getName())).findFirst().orElseThrow();
					var fk = (ForeignKeyConstraint) model.getConstraints().get("parent_fk");
					assertEquals(rule, fk.getDeleteRule()); assertEquals(rule, fk.getUpdateRule());
					statement.execute("DROP TABLE " + child);
					for (var operation : registry.createSql(model, SqlType.CREATE)) statement.execute(operation.getSqlText());
					statement.execute("INSERT INTO " + child + " VALUES (10,1)");
					boolean rejects = rule == com.sqlapp.data.schemas.CascadeRule.None || rule == com.sqlapp.data.schemas.CascadeRule.Restrict;
					if (rejects) assertEquals("23503", assertThrows(SQLException.class,
							() -> statement.execute("UPDATE " + parent + " SET id=2 WHERE id=1")).getSQLState());
					else statement.execute("UPDATE " + parent + " SET id=2 WHERE id=1");
					try (var rows = statement.executeQuery("SELECT parent_id FROM " + child)) {
						assertTrue(rows.next());
						assertEquals(rule == com.sqlapp.data.schemas.CascadeRule.SetNull ? null
								: rule == com.sqlapp.data.schemas.CascadeRule.SetDefault ? 0
								: rule == com.sqlapp.data.schemas.CascadeRule.Cascade ? 2 : 1, rows.getObject(1));
					}
					statement.execute("DELETE FROM " + child);
					statement.execute("DELETE FROM " + parent + " WHERE id<>0");
					statement.execute("INSERT INTO " + parent + " VALUES (1)");
					statement.execute("INSERT INTO " + child + " VALUES (10,1)");
					if (rejects) assertEquals("23503", assertThrows(SQLException.class,
							() -> statement.execute("DELETE FROM " + parent + " WHERE id=1")).getSQLState());
					else statement.execute("DELETE FROM " + parent + " WHERE id=1");
					try (var rows = statement.executeQuery("SELECT parent_id FROM " + child)) {
						if (rule == com.sqlapp.data.schemas.CascadeRule.Cascade) assertFalse(rows.next());
						else {
							assertTrue(rows.next());
							assertEquals(rule == com.sqlapp.data.schemas.CascadeRule.SetNull ? null
									: rule == com.sqlapp.data.schemas.CascadeRule.SetDefault ? 0 : 1, rows.getObject(1));
						}
					}
					if (rejects) {
						connection.setAutoCommit(false);
						try {
							if (rule == com.sqlapp.data.schemas.CascadeRule.Restrict) {
								assertEquals("23503", assertThrows(SQLException.class,
										() -> statement.execute("DELETE FROM " + parent + " WHERE id=1")).getSQLState());
							} else {
								statement.execute("DELETE FROM " + parent + " WHERE id=1");
								statement.execute("DELETE FROM " + child + " WHERE id=10");
								connection.commit();
							}
						} finally { connection.rollback(); connection.setAutoCommit(true); }
					}
				}
			} finally { statement.execute("DROP SCHEMA " + schemaName + " CASCADE"); }
		}
	}

	@Test
	void executesLegacyAndModernConstraintQueriesWithoutChangingKeyOrder() throws Exception {
		String schemaName = "ysql_old_queries_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE TABLE " + schemaName + ".parent (a integer,b integer,label text,PRIMARY KEY (b,a),UNIQUE (label,a))");
				statement.execute("CREATE TABLE " + schemaName + ".child (local_a integer,local_b integer,FOREIGN KEY (local_b,local_a) REFERENCES " + schemaName + ".parent(b,a))");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				for (int[] version : new int[][] {{8,3},{8,4},{9,2},{11,0},{15,0}}) {
					if (version[0] > connection.getMetaData().getDatabaseMajorVersion()) continue;
					var forced = new com.sqlapp.data.schemas.ProductVersionInfo().setMajorVersion(version[0]).setMinorVersion(version[1]);
					var foreignKeys = new com.sqlapp.data.db.dialect.postgres.metadata.PostgresForeignKeyConstraintReader(dialect) {
						@Override
						protected com.sqlapp.jdbc.sql.node.SqlNode getSqlSqlNode(com.sqlapp.data.schemas.ProductVersionInfo ignored) {
							return super.getSqlSqlNode(forced);
						}
					};
					foreignKeys.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
					foreignKeys.setSchemaName(schemaName); foreignKeys.setObjectName("child");
					var constraints = foreignKeys.getAll(connection); assertEquals(1, constraints.size());
					assertEquals(List.of("local_b","local_a"), constraints.get(0).getColumns().stream().map(c -> c.getName()).toList());
					assertEquals(List.of("b","a"), constraints.get(0).getRelatedColumns().stream().map(c -> c.getName()).toList());
					var uniqueKeys = new com.sqlapp.data.db.dialect.postgres.metadata.PostgresUniqueConstraintReader(dialect) {
						@Override
						protected com.sqlapp.jdbc.sql.node.SqlNode getSqlSqlNode(com.sqlapp.data.schemas.ProductVersionInfo ignored) {
							return super.getSqlSqlNode(forced);
						}
					};
					uniqueKeys.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
					uniqueKeys.setSchemaName(schemaName); uniqueKeys.setObjectName("parent");
					var unique = uniqueKeys.getAll(connection); assertEquals(2, unique.size());
					for (var constraint : unique) assertEquals(constraint.isPrimaryKey() ? List.of("b","a") : List.of("label","a"),
							constraint.getColumns().stream().map(c -> c.getName()).toList());
				}
			} finally { statement.execute("DROP SCHEMA " + schemaName + " CASCADE"); }
		}
	}

	@Test
	void preservesNotValidConstraintsAndEnforcesNewWrites() throws Exception {
		String schemaName = "ysql_not_valid_" + UUID.randomUUID().toString().replace("-", "");
		String child = schemaName + ".child";
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE TABLE " + schemaName + ".parent (id integer PRIMARY KEY)");
				statement.execute("INSERT INTO " + schemaName + ".parent VALUES (1)");
				statement.execute("CREATE TABLE " + child + " (id integer, parent_id integer, amount integer)");
				statement.execute("INSERT INTO " + child + " VALUES (1,999,-1)");
				statement.execute("ALTER TABLE " + child + " ADD CONSTRAINT positive CHECK (amount > 0) NOT VALID");
				statement.execute("ALTER TABLE " + child + " ADD CONSTRAINT parent_fk FOREIGN KEY (parent_id) REFERENCES "
						+ schemaName + ".parent(id) NOT VALID");
				for (String name : List.of("positive", "parent_fk")) {
					statement.execute("COMMENT ON CONSTRAINT " + name + " ON " + child + " IS '未検証 ''comment'''");
				}
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName(schemaName); reader.setObjectName("child");
				var model = reader.getAllFull(connection).stream().filter(t -> "child".equals(t.getName())).findFirst().orElseThrow();
				var registry = dialect.createSqlFactoryRegistry(); registry.getOptions().setDecorateSchemaName(true);
				for (String name : List.of("positive", "parent_fk")) {
					var constraint = model.getConstraints().get(name);
					assertEquals("true", constraint.getSpecifics().get("notValid"));
					assertEquals("未検証 'comment'", constraint.getRemarks());
					statement.execute("ALTER TABLE " + child + " DROP CONSTRAINT " + name);
					for (var operation : registry.createSql(constraint, SqlType.CREATE)) statement.execute(operation.getSqlText());
				}
				try (var rows = statement.executeQuery("SELECT count(*) FROM " + child)) {
					assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
				}
				assertEquals("23514", assertThrows(SQLException.class,
						() -> statement.execute("INSERT INTO " + child + " VALUES (2,1,-1)")).getSQLState());
				assertEquals("23503", assertThrows(SQLException.class,
						() -> statement.execute("INSERT INTO " + child + " VALUES (3,999,1)")).getSQLState());
				assertEquals("23514", assertThrows(SQLException.class,
						() -> statement.execute("ALTER TABLE " + child + " VALIDATE CONSTRAINT positive")).getSQLState());
				assertEquals("23503", assertThrows(SQLException.class,
						() -> statement.execute("ALTER TABLE " + child + " VALIDATE CONSTRAINT parent_fk")).getSQLState());
				statement.execute("UPDATE " + child + " SET parent_id=1,amount=1");
				statement.execute("ALTER TABLE " + child + " VALIDATE CONSTRAINT positive");
				statement.execute("ALTER TABLE " + child + " VALIDATE CONSTRAINT parent_fk");
				var validated = reader.getAllFull(connection).stream().filter(t -> "child".equals(t.getName())).findFirst().orElseThrow();
				for (String name : List.of("positive", "parent_fk")) assertNull(validated.getConstraints().get(name).getSpecifics().get("notValid"));
				statement.execute("DROP TABLE " + child);
				var operations = registry.createSql(model, SqlType.CREATE);
				assertFalse(operations.get(0).getSqlText().contains("NOT VALID"));
				for (var operation : operations) statement.execute(operation.getSqlText());
				var recreated = reader.getAllFull(connection).stream().filter(t -> "child".equals(t.getName())).findFirst().orElseThrow();
				for (String name : List.of("positive", "parent_fk")) {
					assertEquals("true", recreated.getConstraints().get(name).getSpecifics().get("notValid"));
					assertEquals("未検証 'comment'", recreated.getConstraints().get(name).getRemarks());
				}
				statement.execute("INSERT INTO " + child + " VALUES (4,1,1)");
				assertEquals("23514", assertThrows(SQLException.class,
						() -> statement.execute("INSERT INTO " + child + " VALUES (5,1,-1)")).getSQLState());
				assertEquals("23503", assertThrows(SQLException.class,
						() -> statement.execute("INSERT INTO " + child + " VALUES (6,999,1)")).getSQLState());
			} finally { statement.execute("DROP SCHEMA " + schemaName + " CASCADE"); }
		}
	}

	@Test
	void recreatesViewCheckOptionsAndSecurityBarrier() throws Exception {
		String schemaName = "ysql_view_options_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE TABLE " + schemaName + ".items (id integer, enabled boolean, score integer)");
				statement.execute("CREATE VIEW " + schemaName + ".enabled AS SELECT * FROM " + schemaName + ".items WHERE enabled");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getViewReader(); reader.setSchemaName(schemaName);
				var registry = dialect.createSqlFactoryRegistry(); registry.getOptions().setDecorateSchemaName(true);
				for (String mode : List.of("local", "cascaded")) {
					String name = "visible_" + mode;
					String viewName = schemaName + "." + name;
					statement.execute("CREATE VIEW " + viewName + " WITH (security_barrier=true,check_option='" + mode
							+ "') AS SELECT * FROM " + schemaName + ".enabled WHERE score > 0");
					reader.setObjectName(name);
					var model = reader.getAllFull(connection).stream().filter(v -> name.equals(v.getName())).findFirst().orElseThrow();
					assertEquals("true", model.getSpecifics().get("security_barrier"));
					assertEquals(mode, model.getSpecifics().get("check_option"));
					statement.execute("DROP VIEW " + viewName);
					for (var operation : registry.createSql(model, SqlType.CREATE)) statement.execute(operation.getSqlText());
					statement.execute("INSERT INTO " + viewName + " VALUES (1,true,1)");
					assertEquals("44000", assertThrows(SQLException.class,
							() -> statement.execute("INSERT INTO " + viewName + " VALUES (2,true,-1)")).getSQLState());
					if ("local".equals(mode)) statement.execute("INSERT INTO " + viewName + " VALUES (3,false,1)");
					else assertEquals("44000", assertThrows(SQLException.class,
							() -> statement.execute("INSERT INTO " + viewName + " VALUES (4,false,1)")).getSQLState());
					var recreated = reader.getAllFull(connection).stream().filter(v -> name.equals(v.getName())).findFirst().orElseThrow();
					assertEquals(model.getSpecifics(), recreated.getSpecifics());
				}
			} finally { statement.execute("DROP SCHEMA " + schemaName + " CASCADE"); }
		}
	}

	@Test
	void recreatesVersionedViewInvokerPermissions() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "");
		String schemaName = "ysql_permissions_" + suffix;
		String role = "ysql_reader_" + suffix;
		try (var connection = connect(); var statement = connection.createStatement()) {
			boolean invoker = connection.getMetaData().getDatabaseMajorVersion() >= 15;
			statement.execute("CREATE ROLE " + role + " NOLOGIN");
			try {
				statement.execute("CREATE SCHEMA " + schemaName);
				try {
					statement.execute("CREATE TABLE " + schemaName + ".items (id integer)");
					statement.execute("INSERT INTO " + schemaName + ".items VALUES (1)");
					statement.execute("CREATE VIEW " + schemaName + ".visible"
							+ (invoker ? " WITH (security_invoker=true)" : "") + " AS SELECT * FROM " + schemaName + ".items");
					var dialect = DialectResolver.getInstance().getDialect(connection);
					var reader = dialect.getCatalogReader().getSchemaReader().getViewReader();
					reader.setSchemaName(schemaName); reader.setObjectName("visible");
					var model = reader.getAllFull(connection).stream().filter(v -> "visible".equals(v.getName())).findFirst().orElseThrow();
					assertEquals(invoker ? "true" : null, model.getSpecifics().get("security_invoker"));
					statement.execute("DROP VIEW " + schemaName + ".visible");
					var registry = dialect.createSqlFactoryRegistry(); registry.getOptions().setDecorateSchemaName(true);
					for (var operation : registry.createSql(model, SqlType.CREATE)) statement.execute(operation.getSqlText());
					statement.execute("GRANT USAGE ON SCHEMA " + schemaName + " TO " + role);
					statement.execute("GRANT SELECT ON " + schemaName + ".visible TO " + role);
					statement.execute("SET ROLE " + role);
					try {
						if (invoker) assertEquals("42501", assertThrows(SQLException.class,
								() -> statement.executeQuery("SELECT * FROM " + schemaName + ".visible")).getSQLState());
						else try (var rows = statement.executeQuery("SELECT * FROM " + schemaName + ".visible")) {
							assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
						}
					} finally { statement.execute("RESET ROLE"); }
					statement.execute("GRANT SELECT ON " + schemaName + ".items TO " + role);
					statement.execute("SET ROLE " + role);
					try (var rows = statement.executeQuery("SELECT * FROM " + schemaName + ".visible")) {
						assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
					} finally { statement.execute("RESET ROLE"); }
				} finally { statement.execute("DROP SCHEMA " + schemaName + " CASCADE"); }
			} finally { statement.execute("DROP ROLE " + role); }
		}
	}

	@Test
	void recreatesStandaloneTableWithCrossSchemaCompositeForeignKey() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "");
		String parentSchema = "ysql_parent_" + suffix;
		String childSchema = "ysql_child_" + suffix;
		String parent = parentSchema + ".\"Parent table\"";
		String child = childSchema + ".\"Child table\"";
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + parentSchema);
			statement.execute("CREATE SCHEMA " + childSchema);
			try {
				statement.execute("CREATE TABLE " + parent + " (a integer, b integer, PRIMARY KEY (b,a))");
				statement.execute("INSERT INTO " + parent + " VALUES (1,2)");
				statement.execute("CREATE TABLE " + child + " (a integer, b integer, CONSTRAINT \"Parent link\" "
						+ "FOREIGN KEY (b,a) REFERENCES " + parent + " (b,a) DEFERRABLE INITIALLY DEFERRED)");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName(childSchema); reader.setObjectName("Child table");
				var model = reader.getAllFull(connection).stream().filter(t -> "Child table".equals(t.getName())).findFirst().orElseThrow();
				var fk = (ForeignKeyConstraint) model.getConstraints().get("Parent link");
				assertEquals("Parent table", fk.getRelatedTableName());
				assertEquals(parentSchema, fk.getRelatedTableSchemaName());
				assertEquals("Parent table", fk.getRelatedTable().getName());
				assertEquals(parentSchema, fk.getRelatedTable().getSchemaName());
				assertEquals(List.of("b", "a"), fk.getRelatedColumns().stream().map(c -> c.getName()).toList());
				for (var column : fk.getRelatedColumns()) {
					assertEquals("Parent table", column.getColumn().getTable().getName());
					assertNotSame(model.getColumns().get(column.getName()), column.getColumn());
				}
				var registry = dialect.createSqlFactoryRegistry(); registry.getOptions().setDecorateSchemaName(true);
				statement.execute("DROP TABLE " + child);
				for (var operation : registry.createSql(model, SqlType.CREATE)) statement.execute(operation.getSqlText());
				statement.execute("INSERT INTO " + child + " VALUES (1,2)");
				assertEquals("23503", assertThrows(SQLException.class,
						() -> statement.execute("INSERT INTO " + child + " VALUES (9,9)")).getSQLState());
				statement.execute("ALTER TABLE " + child + " DROP CONSTRAINT \"Parent link\"");
				for (var operation : registry.createSql(fk, SqlType.CREATE)) statement.execute(operation.getSqlText());
				assertEquals("23503", assertThrows(SQLException.class,
						() -> statement.execute("INSERT INTO " + child + " VALUES (8,8)")).getSQLState());
				var recreated = reader.getAllFull(connection).stream().filter(t -> "Child table".equals(t.getName())).findFirst().orElseThrow();
				var recreatedFk = (ForeignKeyConstraint) recreated.getConstraints().get("Parent link");
				assertEquals(parentSchema, recreatedFk.getRelatedTable().getSchemaName());
				assertEquals("Parent table", recreatedFk.getRelatedTable().getName());
			} finally {
				statement.execute("DROP SCHEMA " + childSchema + " CASCADE");
				statement.execute("DROP SCHEMA " + parentSchema + " CASCADE");
			}
		}
	}

	@Test
	void recreatesDeferrableForeignKeysAndPreservesTransactionChecks() throws Exception {
		String schemaName = "ysql_deferred_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE TABLE " + schemaName + ".parent (id integer PRIMARY KEY)");
				for (String mode : List.of("DEFERRED", "IMMEDIATE")) {
					String childName = "child_" + mode.toLowerCase(java.util.Locale.ROOT);
					String child = schemaName + "." + childName;
					statement.execute("CREATE TABLE " + child + " (id integer, CONSTRAINT parent_fk FOREIGN KEY (id) REFERENCES "
							+ schemaName + ".parent(id) DEFERRABLE INITIALLY " + mode + ")");
					var dialect = DialectResolver.getInstance().getDialect(connection);
					var reader = dialect.getCatalogReader().getSchemaReader();
					reader.setSchemaName(schemaName);
					var model = reader.getAllFull(connection).stream().filter(t -> schemaName.equals(t.getName()))
							.findFirst().orElseThrow().getTables().get(childName);
					var expected = "DEFERRED".equals(mode) ? com.sqlapp.data.schemas.Deferrability.InitiallyDeferred
							: com.sqlapp.data.schemas.Deferrability.InitiallyImmediate;
					assertEquals(expected, ((ForeignKeyConstraint) model.getConstraints().get("parent_fk")).getDeferrability());
					statement.execute("DROP TABLE " + child);
					var registry = dialect.createSqlFactoryRegistry(); registry.getOptions().setDecorateSchemaName(true);
					for (var operation : registry.createSql(model, SqlType.CREATE)) statement.execute(operation.getSqlText());
					if ("IMMEDIATE".equals(mode)) {
						statement.execute("ALTER TABLE " + child + " DROP CONSTRAINT parent_fk");
						for (var operation : registry.createSql(model.getConstraints().get("parent_fk"), SqlType.CREATE)) {
							statement.execute(operation.getSqlText());
						}
					}
					connection.setAutoCommit(false);
					try {
						if ("IMMEDIATE".equals(mode)) {
							assertEquals("23503", assertThrows(SQLException.class,
									() -> statement.execute("INSERT INTO " + child + " VALUES (100)")).getSQLState());
							connection.rollback();
							statement.execute("SET CONSTRAINTS ALL DEFERRED");
						}
						int id = "DEFERRED".equals(mode) ? 1 : 2;
						statement.execute("INSERT INTO " + child + " VALUES (" + id + ")");
						statement.execute("INSERT INTO " + schemaName + ".parent VALUES (" + id + ")");
						connection.commit();
						statement.execute("SET CONSTRAINTS ALL DEFERRED");
						statement.execute("INSERT INTO " + child + " VALUES (999)");
						assertEquals("23503", assertThrows(SQLException.class,
								() -> statement.execute("SET CONSTRAINTS ALL IMMEDIATE")).getSQLState());
					} finally { connection.rollback(); connection.setAutoCommit(true); }
					var recreated = reader.getAllFull(connection).stream().filter(t -> schemaName.equals(t.getName()))
							.findFirst().orElseThrow().getTables().get(childName);
					assertEquals(expected, ((ForeignKeyConstraint) recreated.getConstraints().get("parent_fk")).getDeferrability());
				}
			} finally { statement.execute("DROP SCHEMA " + schemaName + " CASCADE"); }
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
	void recreatesWholeSchemaWithForeignKeysWithoutInternalTriggers() throws Exception {
		String name = "ysql_fk_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + name);
			try {
				statement.execute("CREATE TABLE " + name + ".parent (id integer PRIMARY KEY, txt text)");
				statement.execute("CREATE TABLE " + name + ".child (id integer PRIMARY KEY, parent_id integer NOT NULL REFERENCES " + name + ".parent(id))");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader();
				reader.setSchemaName(name);
				var schema = reader.getAllFull(connection).stream().filter(v -> name.equals(v.getName())).findFirst().orElseThrow();
				assertTrue(schema.getTriggers().isEmpty(), "FK implementation triggers must not be recreated as user triggers");
				statement.execute("DROP SCHEMA " + name + " CASCADE");
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				SqlFactory<com.sqlapp.data.schemas.Schema> factory = registry.getSqlFactory(schema, SqlType.CREATE);
				var operations = factory.createSql(schema);
				assertFalse(operations.isEmpty());
				for (var op : operations) statement.execute(op.getSqlText());
				statement.execute("INSERT INTO " + name + ".parent VALUES (1,'parent')");
				statement.execute("INSERT INTO " + name + ".child VALUES (1,1)");
				assertThrows(SQLException.class, () -> statement.execute("INSERT INTO " + name + ".child VALUES (2,999)"));
				assertEquals(1, scalar(connection, "SELECT count(*) FROM " + name + ".child c JOIN " + name + ".parent p ON p.id=c.parent_id"));
			} finally {
				statement.execute("DROP SCHEMA IF EXISTS " + name + " CASCADE");
			}
		}
	}

	@Test
	void alignsGeneratedParentChildKeysAcrossPartialBatches() throws Exception {
		String name = "ysql_tree_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var observer = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + name);
			try {
				statement.execute("CREATE TABLE " + name + ".parent (id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, txt text)");
				statement.execute("CREATE TABLE " + name + ".child (id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, parent_id bigint NOT NULL REFERENCES " + name + ".parent(id), txt text)");
				var schema = com.sqlapp.data.schemas.SchemaUtils.getSchema(connection, name, "parent", "child").orElseThrow();
				Table parent = schema.getTables().get("parent");
				Table child = schema.getTables().get("child");
				connection.setAutoCommit(false);
				new com.sqlapp.jdbc.sql.JdbcTreeDataSession(connection, parent, child).execute(session -> {
					session.setRootBatchSize(2);
					session.setTableOperationMode(com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode.INSERT);
					for (int i = 0; i < 5; i++) {
						session.newRow(parent).put("txt", "parent-" + i);
						session.newRow(child).put("txt", "child-" + i);
					}
				});
				connection.rollback();
				try (var query = observer.createStatement(); var rows = query.executeQuery("SELECT p.txt,c.txt FROM " + name + ".parent p JOIN " + name + ".child c ON c.parent_id=p.id ORDER BY p.id")) {
					for (int i = 0; i < 5; i++) {
						assertTrue(rows.next());
						assertEquals("parent-" + i, rows.getString(1));
						assertEquals("child-" + i, rows.getString(2));
					}
					assertFalse(rows.next());
				}
			} finally {
				connection.rollback();
				connection.setAutoCommit(true);
				statement.execute("DROP SCHEMA " + name + " CASCADE");
			}
		}
	}

	@Test
	void resumesAfterACompositeJdbcKeyset() throws Exception {
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE public.ysql_composite (\"KEY1\" integer, \"KEY2\" integer, \"TXT\" text, PRIMARY KEY (\"KEY1\",\"KEY2\"))");
			statement.execute("INSERT INTO public.ysql_composite VALUES (1,1,'a'),(1,2,'b'),(2,1,'c'),(2,2,'d')");
			var reader = DialectResolver.getInstance().getDialect(connection).getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("public");
			reader.setObjectName("ysql_composite");
			var table = reader.getAllFull(connection).stream().filter(t -> "ysql_composite".equals(t.getName())).findFirst().orElseThrow();
			BulkMigrationKeysetAssertions.assertCompositeResume(connection, table);
		}
	}

	@Test
	void executesKeyOnlyGeneratedAndBulkUpserts() throws Exception {
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE public.ysql_key_only (id integer PRIMARY KEY)");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			Table table = new Table("ysql_key_only").setSchemaName("public");
			table.getColumns().add("id", c -> c.setDataType(DataType.INT));
			table.setPrimaryKey("ysql_key_only_pkey", table.getColumns().get("id"));
			var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			var nodes = registry.createSqlNodes(table, SqlType.MERGE);
			assertEquals(1, nodes.size());
			var context = new com.sqlapp.data.parameter.ParametersContext();
			context.put("id", 7);
			var handler = new com.sqlapp.jdbc.sql.JdbcHandler(nodes.get(0));
			handler.execute(connection, context);
			handler.execute(connection, context);
			assertEquals(1, scalar(connection, "SELECT count(*) FROM public.ysql_key_only WHERE id=7"));
			table.getRows().add(row -> row.put("id", 7));
			table.getRows().add(row -> row.put("id", 8));
			assertEquals(1, BulkUpsertResolver.resolve(dialect).execute(connection, table, BulkUpsertOption.defaults()));
			assertEquals(2, scalar(connection, "SELECT count(*) FROM public.ysql_key_only"));
		}
	}

	@Test
	void copiesAndUpsertsNestedTextNumericAndBinaryArrays() throws Exception {
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE public.ysql_nested_arrays (id integer PRIMARY KEY, matrix integer[][], labels text[][], numbers smallint[], payloads bytea[])");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("public");
			reader.setObjectName("ysql_nested_arrays");
			Table table = reader.getAllFull(connection).stream().filter(t -> "ysql_nested_arrays".equals(t.getName())).findFirst().orElseThrow();
			String[][] labels = {{"日本語,\"\\\n", ""}, {"NULL", null}};
			table.getRows().add(row -> {
				row.put("id", 1);
				row.put("matrix", new int[][] {{1,2},{3,4}});
				row.put("labels", labels);
				row.put("numbers", new byte[] {-1,127});
				row.put("payloads", new byte[][] {{0,(byte)255},{1,2}});
			});
			assertEquals(1, BulkInsertResolver.resolve(dialect).execute(connection, table, BulkOption.defaults()));
			for (int iteration = 0; iteration < 2; iteration++) {
				if (iteration == 1) {
					table.getRows().get(0).put("matrix", new int[][] {{5,6},{7,8}});
					assertEquals(1, BulkUpsertResolver.resolve(dialect).execute(connection, table, BulkUpsertOption.defaults()));
				}
				try (var rows = statement.executeQuery("SELECT * FROM public.ysql_nested_arrays WHERE id=1")) {
					assertTrue(rows.next());
					Object[] matrix = (Object[]) rows.getArray("matrix").getArray();
					assertArrayEquals(iteration == 0 ? new Integer[] {1,2} : new Integer[] {5,6}, (Object[]) matrix[0]);
					assertArrayEquals(iteration == 0 ? new Integer[] {3,4} : new Integer[] {7,8}, (Object[]) matrix[1]);
					Object[] actualLabels = (Object[]) rows.getArray("labels").getArray();
					assertArrayEquals(labels[0], (Object[]) actualLabels[0]);
					assertArrayEquals(labels[1], (Object[]) actualLabels[1]);
					assertArrayEquals(new Short[] {-1,127}, (Object[]) rows.getArray("numbers").getArray());
					Object[] payloads = (Object[]) rows.getArray("payloads").getArray();
					assertArrayEquals(new byte[] {0,(byte)255}, (byte[]) payloads[0]);
					assertArrayEquals(new byte[] {1,2}, (byte[]) payloads[1]);
				}
			}
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

	@Test
	void recreatesConditionalStatementAndDisabledTriggerStates() throws Exception {
		String schemaName = "ysql_triggers_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE TABLE " + schemaName + ".items (id integer PRIMARY KEY)");
				statement.execute("CREATE TABLE " + schemaName + ".audit (value integer)");
				for (String name : List.of("row_log", "statement_log", "truncate_log")) {
					String value = name.equals("row_log") ? "NEW.id" : name.equals("statement_log") ? "0" : "-1";
					statement.execute("CREATE FUNCTION " + schemaName + "." + name
							+ "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN INSERT INTO " + schemaName
							+ ".audit VALUES (" + value + "); RETURN NULL; END $$");
				}
				statement.execute("CREATE TRIGGER row_log AFTER INSERT ON " + schemaName
						+ ".items FOR EACH ROW WHEN (NEW.id > 1) EXECUTE PROCEDURE " + schemaName + ".row_log()");
				statement.execute("CREATE TRIGGER statement_log AFTER INSERT ON " + schemaName
						+ ".items FOR EACH STATEMENT EXECUTE PROCEDURE " + schemaName + ".statement_log()");
				statement.execute("CREATE TRIGGER truncate_log AFTER TRUNCATE ON " + schemaName
						+ ".items FOR EACH STATEMENT EXECUTE PROCEDURE " + schemaName + ".truncate_log()");
				statement.execute("COMMENT ON TRIGGER row_log ON " + schemaName + ".items IS 'row note'");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (String mode : List.of("O", "D", "R", "A")) {
					String alter = switch (mode) {
						case "D" -> "DISABLE TRIGGER";
						case "R" -> "ENABLE REPLICA TRIGGER";
						case "A" -> "ENABLE ALWAYS TRIGGER";
						default -> "ENABLE TRIGGER";
					};
					statement.execute("ALTER TABLE " + schemaName + ".items " + alter + " row_log");
					var reader = dialect.getCatalogReader().getSchemaReader();
					reader.setSchemaName(schemaName);
					var schema = reader.getAllFull(connection).stream().filter(v -> schemaName.equals(v.getName())).findFirst().orElseThrow();
					var row = schema.getTriggers().get("row_log");
					assertEquals(!mode.equals("D"), row.isEnable());
					assertEquals("row note", row.getRemarks());
					assertEquals(mode.equals("A") ? "ALWAYS" : mode.equals("R") ? "REPLICA" : null,
							row.getSpecifics().get("TRIGGER_FIRING_MODE"));
					assertTrue(String.join("\n", row.getDefinition()).contains("WHEN"), row.getDefinition().toString());
					assertEquals("STATEMENT", schema.getTriggers().get("statement_log").getActionOrientation());
					assertTrue(schema.getTriggers().get("truncate_log").getEventManipulation().contains("TRUNCATE"));
					for (var trigger : schema.getTriggers()) {
						statement.execute("DROP TRIGGER " + trigger.getName() + " ON " + schemaName + ".items");
						SqlFactory<com.sqlapp.data.schemas.Trigger> factory = registry.getSqlFactory(trigger, SqlType.CREATE);
						for (var op : factory.createSql(trigger)) statement.execute(op.getSqlText());
					}
					try (var rows = statement.executeQuery("SELECT t.tgenabled FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid "
							+ "JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='" + schemaName + "' AND t.tgname='row_log'")) {
						assertTrue(rows.next());
						assertEquals(mode, rows.getString(1));
					}
					statement.execute("DELETE FROM " + schemaName + ".items");
					statement.execute("DELETE FROM " + schemaName + ".audit");
					statement.execute("INSERT INTO " + schemaName + ".items VALUES (1),(2),(3)");
					assertEquals(mode.equals("O") || mode.equals("A") ? 2 : 0,
							scalar(connection, "SELECT count(*) FROM " + schemaName + ".audit WHERE value > 0"));
					assertEquals(1, scalar(connection, "SELECT count(*) FROM " + schemaName + ".audit WHERE value=0"));
					statement.execute("TRUNCATE " + schemaName + ".items");
					assertEquals(1, scalar(connection, "SELECT count(*) FROM " + schemaName + ".audit WHERE value=-1"));
				}
			} finally {
				statement.execute("DROP SCHEMA " + schemaName + " CASCADE");
			}
		}
	}

	@Test
	void recreatesCoveringPartialUniqueIndexAndDescendingKey() throws Exception {
		String schemaName = "ysql_indexes_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE TABLE " + schemaName + ".items (id integer PRIMARY KEY, code integer, label text, deleted boolean)");
				statement.execute("CREATE UNIQUE INDEX active_code ON " + schemaName
						+ ".items (code DESC) INCLUDE (label) WHERE (NOT deleted) AND (length(label) > 0)");
				String originalDefinition;
				try (var rows = statement.executeQuery("SELECT pg_get_indexdef('" + schemaName + ".active_code'::regclass)")) {
					assertTrue(rows.next()); originalDefinition = rows.getString(1);
				}
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName(schemaName);
				var table = reader.getAllFull(connection).stream().filter(v -> "items".equals(v.getName())).findFirst().orElseThrow();
				var index = table.getIndexes().get("active_code");
				assertNotNull(index);
				assertTrue(index.isUnique());
				assertEquals(1, index.getColumns().size());
				assertEquals("code", index.getColumns().get(0).getName());
				assertEquals(com.sqlapp.data.schemas.Order.Desc, index.getColumns().get(0).getOrder());
				assertNotNull(index.getIncludes().get("label"));
				assertTrue(index.getWhere().contains("length(label)"), index.getWhere());
				statement.execute("DROP INDEX " + schemaName + ".active_code");
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var op : registry.createSql(index, SqlType.CREATE)) statement.execute(op.getSqlText());
				try (var rows = statement.executeQuery("SELECT pg_get_indexdef('" + schemaName + ".active_code'::regclass)")) {
					assertTrue(rows.next()); assertEquals(originalDefinition, rows.getString(1));
				}
				statement.execute("INSERT INTO " + schemaName + ".items VALUES (1,7,'active',false),(2,7,'deleted',true),(3,7,'',false)");
				SQLException duplicate = assertThrows(SQLException.class,
						() -> statement.execute("INSERT INTO " + schemaName + ".items VALUES (4,7,'duplicate',false)"));
				assertEquals("23505", duplicate.getSQLState());
				assertEquals(3, scalar(connection, "SELECT count(*) FROM " + schemaName + ".items"));
			} finally {
				statement.execute("DROP SCHEMA " + schemaName + " CASCADE");
			}
		}
	}

	@Test
	void recreatesExplicitNullOrderingAndExpressionIndexes() throws Exception {
		String schemaName = "ysql_nulls_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE TABLE " + schemaName + ".items (id integer PRIMARY KEY, code integer, label text)");
				statement.execute("CREATE INDEX null_orders ON " + schemaName + ".items (code ASC NULLS FIRST, label DESC NULLS LAST)");
				statement.execute("CREATE UNIQUE INDEX expression_order ON " + schemaName + ".items ((lower(label)) DESC NULLS LAST)");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName(schemaName);
				var table = reader.getAllFull(connection).stream().filter(v -> "items".equals(v.getName())).findFirst().orElseThrow();
				var ordered = table.getIndexes().get("null_orders");
				assertEquals(com.sqlapp.data.schemas.NullsOrder.NullsFirst, ordered.getColumns().get(0).getNullsOrder());
				assertEquals(com.sqlapp.data.schemas.NullsOrder.NullsLast, ordered.getColumns().get(1).getNullsOrder());
				var expression = table.getIndexes().get("expression_order");
				assertEquals(com.sqlapp.data.schemas.IndexType.Function, expression.getIndexType());
				assertEquals(com.sqlapp.data.schemas.Order.Desc, expression.getColumns().get(0).getOrder());
				assertEquals(com.sqlapp.data.schemas.NullsOrder.NullsLast, expression.getColumns().get(0).getNullsOrder());
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var index : List.of(ordered, expression)) {
					String original;
					try (var rows = statement.executeQuery("SELECT pg_get_indexdef('" + schemaName + "." + index.getName() + "'::regclass)")) {
						assertTrue(rows.next()); original = rows.getString(1);
					}
					statement.execute("DROP INDEX " + schemaName + "." + index.getName());
					for (var op : registry.createSql(index, SqlType.CREATE)) statement.execute(op.getSqlText());
					try (var rows = statement.executeQuery("SELECT pg_get_indexdef('" + schemaName + "." + index.getName() + "'::regclass)")) {
						assertTrue(rows.next()); assertEquals(original, rows.getString(1));
					}
				}
				statement.execute("INSERT INTO " + schemaName + ".items VALUES (1,NULL,'Alpha'),(2,1,NULL),(3,2,NULL)");
				SQLException duplicate = assertThrows(SQLException.class,
						() -> statement.execute("INSERT INTO " + schemaName + ".items VALUES (4,3,'ALPHA')"));
				assertEquals("23505", duplicate.getSQLState());
			} finally {
				statement.execute("DROP SCHEMA " + schemaName + " CASCADE");
			}
		}
	}

	@Test
	void recreatesVersionedNullDistinctnessAndIgnoresUnsupportedOption() throws Exception {
		String schemaName = "ysql_distinct_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			boolean supportsNotDistinct = connection.getMetaData().getDatabaseMajorVersion() >= 15;
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE TABLE " + schemaName + ".items (id integer PRIMARY KEY, code integer, label text, active boolean)");
				statement.execute("CREATE UNIQUE INDEX null_code ON " + schemaName + ".items (code ASC) INCLUDE (label) "
						+ (supportsNotDistinct ? "NULLS NOT DISTINCT " : "") + "WHERE active");
				String original;
				try (var rows = statement.executeQuery("SELECT pg_get_indexdef('" + schemaName + ".null_code'::regclass)")) {
					assertTrue(rows.next()); original = rows.getString(1);
				}
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName(schemaName);
				var table = reader.getAllFull(connection).stream().filter(v -> "items".equals(v.getName())).findFirst().orElseThrow();
				var index = table.getIndexes().get("null_code");
				assertNotNull(index);
				assertEquals(supportsNotDistinct ? "true" : null, index.getSpecifics().get("nullsNotDistinct"));
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				if (!supportsNotDistinct) {
					index.getSpecifics().put("nullsNotDistinct", "true");
					assertFalse(registry.createSql(index, SqlType.CREATE).get(0).getSqlText().contains("NULLS NOT DISTINCT"));
					index.getSpecifics().remove("nullsNotDistinct");
				}
				statement.execute("DROP INDEX " + schemaName + ".null_code");
				for (var op : registry.createSql(index, SqlType.CREATE)) statement.execute(op.getSqlText());
				try (var rows = statement.executeQuery("SELECT pg_get_indexdef('" + schemaName + ".null_code'::regclass)")) {
					assertTrue(rows.next()); assertEquals(original, rows.getString(1));
				}
				statement.execute("INSERT INTO " + schemaName + ".items VALUES (1,NULL,'first',true),(2,NULL,'excluded',false),(3,NULL,'excluded again',false)");
				String nullDuplicate = "INSERT INTO " + schemaName + ".items VALUES (4,NULL,'second',true)";
				if (supportsNotDistinct) {
					assertEquals("23505", assertThrows(SQLException.class, () -> statement.execute(nullDuplicate)).getSQLState());
				} else {
					statement.execute(nullDuplicate);
				}
				statement.execute("INSERT INTO " + schemaName + ".items VALUES (5,7,'key',true)");
				assertEquals("23505", assertThrows(SQLException.class,
						() -> statement.execute("INSERT INTO " + schemaName + ".items VALUES (6,7,'duplicate key',true)")).getSQLState());
			} finally {
				statement.execute("DROP SCHEMA " + schemaName + " CASCADE");
			}
		}
	}

	@Test
	void readsAndRecreatesInsteadOfViewTriggerForInsertUpdateAndDelete() throws Exception {
		String schemaName = "ysql_view_trigger_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE TABLE " + schemaName + ".items (id integer PRIMARY KEY, label text)");
				statement.execute("CREATE VIEW " + schemaName + ".editable AS SELECT id,label FROM " + schemaName
						+ ".items UNION ALL SELECT -1,'sentinel'::text");
				statement.execute("CREATE FUNCTION " + schemaName + ".edit_item() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
						+ "IF TG_OP = 'DELETE' THEN DELETE FROM " + schemaName + ".items WHERE id=OLD.id; RETURN OLD; "
						+ "ELSIF TG_OP = 'UPDATE' THEN UPDATE " + schemaName + ".items SET label=upper(NEW.label) WHERE id=OLD.id; RETURN NEW; "
						+ "ELSE INSERT INTO " + schemaName + ".items VALUES (NEW.id,upper(NEW.label)); RETURN NEW; END IF; END $$");
				statement.execute("CREATE TRIGGER edit_item INSTEAD OF INSERT OR UPDATE OR DELETE ON " + schemaName
						+ ".editable FOR EACH ROW EXECUTE PROCEDURE " + schemaName + ".edit_item()");
				statement.execute("COMMENT ON TRIGGER edit_item ON " + schemaName + ".editable IS '日本語 ''quoted'''");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader();
				reader.setSchemaName(schemaName);
				var schema = reader.getAllFull(connection).stream().filter(v -> schemaName.equals(v.getName())).findFirst().orElseThrow();
				var trigger = schema.getTriggers().get("edit_item");
				assertNotNull(trigger);
				assertEquals("INSTEAD OF", trigger.getActionTiming());
				assertEquals("日本語 'quoted'", trigger.getRemarks());
				assertEquals("ROW", trigger.getActionOrientation());
				assertEquals("editable", trigger.getTableName());
				assertTrue(trigger.getEventManipulation().containsAll(List.of("INSERT", "UPDATE", "DELETE")));
				assertTrue(String.join("\n", trigger.getDefinition()).contains("INSTEAD OF"));
				statement.execute("DROP TRIGGER edit_item ON " + schemaName + ".editable");
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var op : registry.createSql(trigger, SqlType.CREATE)) statement.execute(op.getSqlText());
				try (var rows = statement.executeQuery("SELECT obj_description(t.oid,'pg_trigger') FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid "
						+ "JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='" + schemaName + "' AND t.tgname='edit_item'")) {
					assertTrue(rows.next()); assertEquals("日本語 'quoted'", rows.getString(1));
				}

				assertEquals(2, statement.executeUpdate("INSERT INTO " + schemaName + ".editable VALUES (1,'first'),(2,'second')"));
				assertEquals(2, scalar(connection, "SELECT count(*) FROM " + schemaName + ".items WHERE label IN ('FIRST','SECOND')"));
				assertEquals(1, statement.executeUpdate("UPDATE " + schemaName + ".editable SET label='changed' WHERE id=1"));
				assertEquals(1, scalar(connection, "SELECT count(*) FROM " + schemaName + ".items WHERE id=1 AND label='CHANGED'"));
				assertEquals(1, statement.executeUpdate("DELETE FROM " + schemaName + ".editable WHERE id=2"));
				assertEquals(1, scalar(connection, "SELECT count(*) FROM " + schemaName + ".items"));
			} finally {
				statement.execute("DROP SCHEMA " + schemaName + " CASCADE");
			}
		}
	}

}
