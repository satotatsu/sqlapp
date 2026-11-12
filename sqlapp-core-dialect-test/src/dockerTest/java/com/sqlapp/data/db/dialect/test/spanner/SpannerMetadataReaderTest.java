/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.spanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.google.cloud.spanner.jdbc.CloudSpannerJdbcConnection;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.spanner.Spanner;
import com.sqlapp.data.db.dialect.spanner.sql.SpannerAlterSequenceFactory;
import com.sqlapp.data.db.dialect.spanner.sql.SpannerAlterTableFactory;
import com.sqlapp.data.db.dialect.spanner.sql.SpannerAlterCatalogFactory;
import com.sqlapp.data.db.dialect.spanner.sql.SpannerCreateIndexFactory;
import com.sqlapp.data.db.dialect.spanner.sql.SpannerCreateSequenceFactory;
import com.sqlapp.data.db.dialect.spanner.sql.SpannerCreateTableFactory;
import com.sqlapp.data.db.dialect.test.BulkMigrationJobAssertions;
import com.sqlapp.data.db.dialect.test.BulkMigrationTransactionAssertions;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.data.schemas.RoleMember;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.EqualsHandler;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Synonym;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.data.schemas.VectorDistanceType;

/** Cloud Spanner emulator integration coverage for MetadataReader. */
class SpannerMetadataReaderTest {
	private static final GenericContainer<?> SPANNER = ReusableTestcontainers
			.configure(new GenericContainer<>(DockerImageName.parse("gcr.io/cloud-spanner-emulator/emulator:latest"))
					.withExposedPorts(9010)
					.waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2))));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(SPANNER);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(SPANNER);
	}

	@Test
	void appliesDatabaseOptionsAndRoleMembershipThroughCatalogDifferences() throws Exception {
		final String database = "admin-" + UUID.randomUUID().toString().substring(0, 8);
		try (Connection connection = createConnection(database); Statement sql = connection.createStatement()) {
			final var dialect = DialectResolver.getInstance().getDialect(connection);
			final var registry = dialect.createSqlFactoryRegistry();
			final var original = new Catalog(database).setDialect(dialect);
			final var configured = original.clone().setDialect(dialect);
			configured.getSpecifics().put(SpannerAlterCatalogFactory.DEFAULT_SEQUENCE_KIND, "bit_reversed_positive");
			configured.getSpecifics().put(SpannerAlterCatalogFactory.VERSION_RETENTION_PERIOD, "2h");
			for (final var operation : registry.createSql(original.diff(configured)))
				sql.execute(operation.getSqlText());
			try (final var rows = sql.executeQuery("SELECT OPTION_VALUE FROM INFORMATION_SCHEMA.DATABASE_OPTIONS "
					+ "WHERE OPTION_NAME='default_sequence_kind'")) {
				assertTrue(rows.next());
				assertTrue(rows.getString(1).contains("bit_reversed_positive"));
			}
			final var withRoles = configured.clone().setDialect(dialect);
			withRoles.getRoles().add(new Role("Reader"));
			withRoles.getRoles().add(new Role("App"));
			withRoles.getRoleMembers()
					.add(new RoleMember(withRoles.getRoles().get("App"), withRoles.getRoles().get("Reader")));
			final var roleSql = registry.createSql(configured.diff(withRoles));
			assertEquals(3, roleSql.size());
			for (final var operation : roleSql)
				sql.execute(operation.getSqlText());
			assertThrows(SQLException.class, () -> sql.execute("CREATE ROLE Reader"));
			for (final var operation : registry.createSql(withRoles.diff(configured)))
				sql.execute(operation.getSqlText());
			sql.execute(registry.createSql(new Role("Reader"), SqlType.CREATE).getFirst().getSqlText());
			sql.execute(registry.createSql(new Role("Reader"), SqlType.DROP).getFirst().getSqlText());
			for (final var operation : registry.createSql(configured.diff(original)))
				sql.execute(operation.getSqlText());
		}
	}

	@Test
	void appliesExplicitSequenceAndIdentityRestarts() throws Exception {
		try (Connection connection = createConnection("restart-" + UUID.randomUUID().toString().substring(0, 8));
				Statement sql = connection.createStatement()) {
			sql.execute("CREATE SEQUENCE clause_restart BIT_REVERSED_POSITIVE START COUNTER WITH 100");
			sql.execute(
					"CREATE SEQUENCE option_restart OPTIONS(sequence_kind='bit_reversed_positive', start_with_counter=100)");
			sql.execute("CREATE TABLE restart_identity (id INT64 GENERATED BY DEFAULT AS IDENTITY "
					+ "(BIT_REVERSED_POSITIVE START COUNTER WITH 100), payload STRING(30)) PRIMARY KEY (id)");
			sql.executeUpdate("INSERT INTO restart_identity (payload) VALUES ('before')");
			final var dialect = DialectResolver.getInstance().getDialect(connection);
			final var registry = dialect.createSqlFactoryRegistry();
			final var reader = dialect.getCatalogReader().getSchemaReader();
			for (final var original : reader.getSequenceReader().getAll(connection)) {
				original.getSpecifics().put(SpannerAlterSequenceFactory.SEQUENCE_DDL_STYLE,
						original.getName().startsWith("clause") ? "CLAUSES" : "OPTIONS");
				final var target = original.clone();
				target.getSpecifics().put(SpannerAlterSequenceFactory.RESTART_COUNTER_WITH, "10000");
				sql.execute(registry.createSql(original.diff(target)).getFirst().getSqlText());
				connection.setReadOnly(false);
				connection.setAutoCommit(false);
				try (final var rows = sql.executeQuery("SELECT GET_NEXT_SEQUENCE_VALUE(SEQUENCE " + original.getName()
						+ "), BIT_REVERSE(10000, true)")) {
					assertTrue(rows.next());
					assertEquals(rows.getLong(2), rows.getLong(1));
				}
				connection.commit();
				connection.setAutoCommit(true);
			}
			final var original = reader.getTableReader().getAllFull(connection).stream()
					.filter(table -> "restart_identity".equals(table.getName())).findFirst().orElseThrow();
			final var target = original.clone();
			target.getColumns().get("id").getSpecifics().put(SpannerAlterTableFactory.IDENTITY_RESTART_COUNTER_WITH,
					"10000");
			sql.execute(registry.createSql(original.diff(target)).getFirst().getSqlText());
			sql.executeUpdate("INSERT INTO restart_identity (payload) VALUES ('after')");
			try (final var rows = sql
					.executeQuery("SELECT id, BIT_REVERSE(10000, true) FROM restart_identity WHERE payload='after'")) {
				assertTrue(rows.next());
				assertEquals(rows.getLong(2), rows.getLong(1));
			}
			try (final var rows = sql.executeQuery("SELECT COUNT(*) FROM restart_identity")) {
				assertTrue(rows.next());
				assertEquals(2L, rows.getLong(1));
			}
		}
	}

	@Test
	void stagesCyclicForeignKeysForCreationAndDeletion() throws Exception {
		try (Connection connection = createConnection("cycle-" + UUID.randomUUID().toString().substring(0, 8));
				Statement sql = connection.createStatement()) {
			final var dialect = DialectResolver.getInstance().getDialect(connection);
			final var registry = dialect.createSqlFactoryRegistry();
			final var empty = new Schema("Sales").setDialect(dialect);
			sql.execute(registry.createSql(empty, SqlType.CREATE).getFirst().getSqlText());
			final var populated = new Schema("Sales").setDialect(dialect);
			for (String name : List.of("FirstTable", "SecondTable")) {
				final var table = new Table(name);
				table.getColumns().add(new Column("id").setDataType(DataType.BIGINT).setNotNull(true));
				table.getColumns().add(new Column("peer_id").setDataType(DataType.BIGINT));
				table.getConstraints().addPrimaryKeyConstraint("pk", table.getColumns().getFirst());
				populated.getTables().add(table);
			}
			final var first = populated.getTables().get("FirstTable");
			final var second = populated.getTables().get("SecondTable");
			first.getConstraints().add(new ForeignKeyConstraint("FirstSecond", first.getColumns().get("peer_id"),
					second.getColumns().get("id")));
			second.getConstraints().add(new ForeignKeyConstraint("SecondFirst", second.getColumns().get("peer_id"),
					first.getColumns().get("id")));
			final var added = registry.createSql(empty.diff(populated));
			assertEquals(4, added.size());
			for (final var operation : added)
				sql.execute(operation.getSqlText());
			sql.executeUpdate("INSERT INTO Sales.FirstTable (id) VALUES (1)");
			sql.executeUpdate("INSERT INTO Sales.SecondTable (id) VALUES (2)");
			sql.executeUpdate("UPDATE Sales.FirstTable SET peer_id=2 WHERE id=1");
			sql.executeUpdate("UPDATE Sales.SecondTable SET peer_id=1 WHERE id=2");
			assertThrows(SQLException.class,
					() -> sql.executeUpdate("UPDATE Sales.FirstTable SET peer_id=999 WHERE id=1"));
			final var reader = dialect.getCatalogReader().getSchemaReader();
			final var actual = reader.getAllFull(connection).stream().filter(schema -> "Sales".equals(schema.getName()))
					.findFirst().orElseThrow();
			final var removed = registry.createSql(actual.diff(empty));
			assertEquals(4, removed.size());
			for (final var operation : removed)
				sql.execute(operation.getSqlText());
			assertTrue(reader.getAllFull(connection).stream().filter(schema -> "Sales".equals(schema.getName()))
					.findFirst().orElseThrow().getTables().isEmpty());
		}
	}

	@Test
	void convertsStringAndBytesWithServiceValidationAndPreservedRows() throws Exception {
		try (Connection connection = createConnection("convert-" + UUID.randomUUID().toString().substring(0, 8));
				Statement sql = connection.createStatement()) {
			sql.execute("CREATE TABLE type_fixture (id INT64 NOT NULL, payload STRING(30) NOT NULL) PRIMARY KEY (id)");
			sql.executeUpdate("INSERT INTO type_fixture (id, payload) VALUES (1, '日本語-é')");
			final var dialect = DialectResolver.getInstance().getDialect(connection);
			final var registry = dialect.createSqlFactoryRegistry();
			final var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
			final var original = reader.getAllFull(connection).stream()
					.filter(table -> "type_fixture".equals(table.getName())).findFirst().orElseThrow();
			final var bytes = original.clone();
			bytes.getColumns().get("payload").setDataType(DataType.VARBINARY).setDataTypeName("BYTES").setLength(100);
			sql.execute(registry.createSql(original.diff(bytes)).getFirst().getSqlText());
			try (final var rows = sql.executeQuery("SELECT payload FROM type_fixture WHERE id=1")) {
				assertTrue(rows.next());
				assertEquals("日本語-é", new String(rows.getBytes(1), java.nio.charset.StandardCharsets.UTF_8));
			}
			try (final var insert = connection
					.prepareStatement("INSERT INTO type_fixture (id, payload) VALUES (2, ?)")) {
				insert.setBytes(1, new byte[] { (byte) 0xff });
				insert.executeUpdate();
			}
			final var current = reader.getAllFull(connection).stream()
					.filter(table -> "type_fixture".equals(table.getName())).findFirst().orElseThrow();
			final var strings = current.clone();
			strings.getColumns().get("payload").setDataType(DataType.VARCHAR).setDataTypeName("STRING").setLength(30);
			final var conversion = registry.createSql(current.diff(strings)).getFirst().getSqlText();
			assertThrows(SQLException.class, () -> sql.execute(conversion));
			sql.executeUpdate("DELETE FROM type_fixture WHERE id=2");
			sql.execute(conversion);
			try (final var rows = sql.executeQuery("SELECT payload FROM type_fixture WHERE id=1")) {
				assertTrue(rows.next());
				assertEquals("日本語-é", rows.getString(1));
			}
		}
	}

	@Test
	void dropsSameNamedCrossSchemaForeignKeyTargetsInOrder() throws Exception {
		try (Connection connection = createConnection("cross-drop-" + UUID.randomUUID().toString().substring(0, 8));
				Statement sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA Sales");
			sql.execute("CREATE SCHEMA Archive");
			sql.execute("CREATE TABLE Archive.Parent (id INT64 NOT NULL) PRIMARY KEY (id)");
			sql.execute("CREATE TABLE Sales.Parent (id INT64 NOT NULL, "
					+ "CONSTRAINT CrossSchema FOREIGN KEY (id) REFERENCES Archive.Parent (id)) PRIMARY KEY (id)");
			final var parent = new Table("Parent").setSchemaName("Archive");
			final var child = new Table("Parent").setSchemaName("Sales");
			child.getColumns().add(new Column("id").setDataType(DataType.BIGINT));
			final var reference = new ForeignKeyConstraint("CrossSchema").setRelatedTableName("Parent")
					.setRelatedTableSchemaName("Archive");
			reference.getColumns().add(child.getColumns().getFirst());
			reference.getRelatedColumns().add("id");
			child.getConstraints().add(reference);
			final var dialect = DialectResolver.getInstance().getDialect(connection);
			final SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(parent, SqlType.DROP);
			final var operations = factory.createSql(List.of(child, parent));
			assertEquals(2, operations.size());
			assertTrue(operations.getFirst().getSqlText().contains("`Sales`.`Parent`"));
			assertTrue(operations.getLast().getSqlText().contains("`Archive`.`Parent`"));
			for (final var operation : operations)
				sql.execute(operation.getSqlText());
			final var schemas = dialect.getCatalogReader().getSchemaReader().getAllFull(connection);
			for (String name : List.of("Sales", "Archive"))
				assertTrue(schemas.stream().filter(schema -> name.equals(schema.getName())).findFirst().orElseThrow()
						.getTables().isEmpty());
		}
	}

	@Test
	void dropsDetachedTablesWithNamedForeignKeyTargets() throws Exception {
		try (Connection connection = createConnection("named-drop-" + UUID.randomUUID().toString().substring(0, 8));
				Statement sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA Sales");
			sql.execute("CREATE TABLE Sales.Parent (id INT64 NOT NULL) PRIMARY KEY (id)");
			sql.execute("CREATE TABLE Sales.Child (id INT64 NOT NULL, "
					+ "CONSTRAINT ChildParent FOREIGN KEY (id) REFERENCES Sales.Parent (id)) PRIMARY KEY (id)");
			final var parent = new Table("Parent").setSchemaName("Sales");
			final var child = new Table("Child").setSchemaName("Sales");
			child.getColumns().add(new Column("id").setDataType(DataType.BIGINT));
			final var reference = new ForeignKeyConstraint("ChildParent").setRelatedTableName("Parent")
					.setRelatedTableSchemaName("Sales");
			reference.getColumns().add(child.getColumns().getFirst());
			reference.getRelatedColumns().add("id");
			child.getConstraints().add(reference);
			final var dialect = DialectResolver.getInstance().getDialect(connection);
			final SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(parent, SqlType.DROP);
			final var operations = factory.createSql(List.of(child, parent));
			assertEquals(2, operations.size());
			assertTrue(operations.getFirst().getSqlText().contains("`Sales`.`Child`"));
			for (final var operation : operations)
				sql.execute(operation.getSqlText());
			final var remaining = dialect.getCatalogReader().getSchemaReader().getAllFull(connection).stream()
					.filter(schema -> "Sales".equals(schema.getName())).findFirst().orElseThrow();
			assertTrue(remaining.getTables().isEmpty());
		}
	}

	@Test
	void dropsDependentTableBatchesThroughTheFactory() throws Exception {
		for (boolean interleaved : List.of(true, false)) {
			try (Connection connection = createConnection("batch-drop-" + UUID.randomUUID().toString().substring(0, 8));
					Statement sql = connection.createStatement()) {
				sql.execute("CREATE SCHEMA Sales");
				sql.execute("CREATE TABLE Sales.Parent (id INT64 NOT NULL) PRIMARY KEY (id)");
				if (interleaved)
					sql.execute("CREATE TABLE Sales.Child (id INT64 NOT NULL) PRIMARY KEY (id), "
							+ "INTERLEAVE IN PARENT Sales.Parent ON DELETE CASCADE");
				else
					sql.execute("CREATE TABLE Sales.Child (id INT64 NOT NULL, "
							+ "CONSTRAINT ChildParent FOREIGN KEY (id) REFERENCES Sales.Parent (id)) PRIMARY KEY (id)");
				sql.execute("CREATE INDEX Sales.ById ON Sales.Child (id)");
				sql.executeUpdate("INSERT INTO Sales.Parent (id) VALUES (7)");
				sql.executeUpdate("INSERT INTO Sales.Child (id) VALUES (7)");
				final var dialect = DialectResolver.getInstance().getDialect(connection);
				final var reader = dialect.getCatalogReader().getSchemaReader();
				final var schema = reader.getAllFull(connection).stream()
						.filter(value -> "Sales".equals(value.getName())).findFirst().orElseThrow();
				final var parent = schema.getTables().get("Parent");
				final var child = schema.getTables().get("Child");
				final SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(parent,
						SqlType.DROP);
				final var operations = interleaved ? factory.createSql(List.of(parent, child))
						: factory.createDiffSql(List.of(new DbObjectDifference(parent, null, new EqualsHandler()),
								new DbObjectDifference(child, null, new EqualsHandler())));
				assertEquals(3, operations.size());
				assertTrue(operations.getFirst().getSqlText().startsWith("DROP INDEX"));
				assertTrue(operations.get(1).getSqlText().contains("`Sales`.`Child`"));
				assertTrue(operations.getLast().getSqlText().contains("`Sales`.`Parent`"));
				for (final var operation : operations)
					sql.execute(operation.getSqlText());
				final var remaining = reader.getAllFull(connection).stream()
						.filter(value -> "Sales".equals(value.getName())).findFirst().orElseThrow();
				assertTrue(remaining.getTables().isEmpty());
			}
		}
	}

	@Test
	void addsAndDropsForeignKeyOnlyChainsThroughSchemaDifferences() throws Exception {
		try (Connection connection = createConnection("fk-chain-" + UUID.randomUUID().toString().substring(0, 8));
				Statement sql = connection.createStatement()) {
			final var dialect = DialectResolver.getInstance().getDialect(connection);
			final var registry = dialect.createSqlFactoryRegistry();
			final var empty = new Schema("Sales").setDialect(dialect);
			sql.execute(registry.createSql(empty, SqlType.CREATE).getFirst().getSqlText());
			final var populated = new Schema("Sales").setDialect(dialect);
			for (String name : List.of("ZLeaf", "AParent", "MChild")) {
				final var table = new Table(name);
				table.getColumns().add(new Column("id").setDataType(DataType.BIGINT).setNotNull(true));
				table.getConstraints().addPrimaryKeyConstraint("pk", table.getColumns().getFirst());
				populated.getTables().add(table);
			}
			final var parent = populated.getTables().get("AParent");
			final var child = populated.getTables().get("MChild");
			final var leaf = populated.getTables().get("ZLeaf");
			child.getConstraints().add(new ForeignKeyConstraint("ChildParent", child.getColumns().getFirst(),
					parent.getColumns().getFirst()));
			leaf.getConstraints().add(
					new ForeignKeyConstraint("LeafChild", leaf.getColumns().getFirst(), child.getColumns().getFirst()));
			final var added = registry.createSql(empty.diff(populated));
			assertEquals(3, added.size());
			for (final var operation : added)
				sql.execute(operation.getSqlText());
			for (String name : List.of("AParent", "MChild", "ZLeaf"))
				sql.executeUpdate("INSERT INTO Sales." + name + " (id) VALUES (7)");
			assertThrows(SQLException.class, () -> sql.executeUpdate("INSERT INTO Sales.ZLeaf (id) VALUES (999)"));
			final var reader = dialect.getCatalogReader().getSchemaReader();
			final var actual = reader.getAllFull(connection).stream().filter(schema -> "Sales".equals(schema.getName()))
					.findFirst().orElseThrow();
			final var deleted = registry.createSql(actual.diff(empty));
			assertEquals(3, deleted.size());
			assertTrue(deleted.getFirst().getSqlText().contains("`Sales`.`ZLeaf`"));
			assertTrue(deleted.getLast().getSqlText().contains("`Sales`.`AParent`"));
			for (final var operation : deleted)
				sql.execute(operation.getSqlText());
			final var remaining = reader.getAllFull(connection).stream()
					.filter(schema -> "Sales".equals(schema.getName())).findFirst().orElseThrow();
			assertTrue(remaining.getTables().isEmpty());
		}
	}

	@Test
	void dropsIndexedTablesFromMetadataBeforeTheirIndexesBlockDeletion() throws Exception {
		try (Connection connection = createConnection("drop-indexed-" + UUID.randomUUID().toString().substring(0, 8));
				Statement sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA Sales");
			sql.execute("CREATE TABLE Sales.Parents (id INT64 NOT NULL) PRIMARY KEY (id)");
			sql.execute("""
					CREATE TABLE Sales.Items (
					 id INT64 NOT NULL, parent_id INT64, label STRING(30),
					 tokens TOKENLIST AS (TOKENIZE_FULLTEXT(label)) HIDDEN,
					 embedding ARRAY<FLOAT32>(vector_length=>3),
					 CONSTRAINT RefParent FOREIGN KEY (parent_id) REFERENCES Sales.Parents (id)
					) PRIMARY KEY (id)
					""");
			sql.execute("CREATE INDEX Sales.ByLabel ON Sales.Items(label)");
			sql.execute("CREATE UNIQUE INDEX Sales.UniqueLabel ON Sales.Items(label)");
			sql.execute("CREATE SEARCH INDEX Sales.SearchItems ON Sales.Items(tokens)");
			sql.execute("CREATE VECTOR INDEX Sales.VectorItems ON Sales.Items(embedding) "
					+ "WHERE embedding IS NOT NULL OPTIONS(distance_type='COSINE')");
			final var dialect = DialectResolver.getInstance().getDialect(connection);
			final var reader = dialect.getCatalogReader().getSchemaReader();
			final var original = reader.getAllFull(connection).stream()
					.filter(schema -> "Sales".equals(schema.getName())).findFirst().orElseThrow();
			final var table = original.getTables().get("Items");
			final var managed = table.getIndexes().stream()
					.filter(index -> "true"
							.equalsIgnoreCase(index.getSpecifics().get(SpannerCreateIndexFactory.SPANNER_IS_MANAGED)))
					.toList();
			assertTrue(!managed.isEmpty());
			final var target = original.clone();
			target.getTables().remove(target.getTables().get("Items"));
			final var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDropIfExists(true);
			final var operations = registry.createSql(original.diff(target));
			assertEquals(5, operations.size());
			assertEquals("DROP TABLE IF EXISTS `Sales`.`Items`", operations.getLast().getSqlText());
			for (final var backing : managed)
				assertTrue(
						operations.stream().noneMatch(operation -> operation.getSqlText().contains(backing.getName())));
			for (int repetition = 0; repetition < 2; repetition++)
				for (final var operation : operations)
					sql.execute(operation.getSqlText());
			final var remaining = reader.getAllFull(connection).stream()
					.filter(schema -> "Sales".equals(schema.getName())).findFirst().orElseThrow();
			assertNull(remaining.getTables().get("Items"));
			assertNotNull(remaining.getTables().get("Parents"));
		}
	}

	@Test
	void addsAndDropsInterleaveChainsThroughSchemaDifferences() throws Exception {
		try (Connection connection = createConnection("chain-diff-" + UUID.randomUUID().toString().substring(0, 8));
				Statement sql = connection.createStatement()) {
			final var dialect = DialectResolver.getInstance().getDialect(connection);
			final var registry = dialect.createSqlFactoryRegistry();
			final var empty = new Schema("Sales").setDialect(dialect);
			sql.execute(registry.createSql(empty, SqlType.CREATE).getFirst().getSqlText());
			final var populated = new Schema("Sales").setDialect(dialect);
			for (String name : List.of("ALeaf", "ZParent", "BChild")) {
				final var table = new Table(name);
				table.getColumns().add(new Column("id").setDataType(DataType.BIGINT).setNotNull(true));
				table.getConstraints().addPrimaryKeyConstraint("pk", table.getColumns().getFirst());
				if (!"ZParent".equals(name)) {
					table.getSpecifics().put(SpannerCreateTableFactory.PARENT_TABLE_NAME,
							"ALeaf".equals(name) ? "BChild" : "ZParent");
					table.getSpecifics().put(SpannerCreateTableFactory.INTERLEAVE_TYPE, "IN PARENT");
					table.getSpecifics().put(SpannerCreateTableFactory.ON_DELETE_ACTION, "CASCADE");
				}
				populated.getTables().add(table);
			}
			final var added = registry.createSql(empty.diff(populated));
			assertEquals(3, added.size());
			for (final var operation : added)
				sql.execute(operation.getSqlText());
			for (String name : List.of("ZParent", "BChild", "ALeaf"))
				sql.executeUpdate("INSERT INTO Sales." + name + " (id) VALUES (7)");
			try (final var rows = sql.executeQuery("SELECT id FROM Sales.ALeaf")) {
				assertTrue(rows.next());
				assertEquals(7L, rows.getLong(1));
			}
			final var reader = dialect.getCatalogReader().getSchemaReader();
			final var actual = reader.getAllFull(connection).stream().filter(schema -> "Sales".equals(schema.getName()))
					.findFirst().orElseThrow();
			final var deleted = registry.createSql(actual.diff(empty));
			assertEquals(3, deleted.size());
			assertTrue(deleted.getFirst().getSqlText().contains("`Sales`.`ALeaf`"));
			assertTrue(deleted.getLast().getSqlText().contains("`Sales`.`ZParent`"));
			for (final var operation : deleted)
				sql.execute(operation.getSqlText());
			final var remaining = reader.getAllFull(connection).stream()
					.filter(schema -> "Sales".equals(schema.getName())).findFirst().orElseThrow();
			assertTrue(remaining.getTables().isEmpty());
		}
	}

	@Test
	void ordersSchemaSynonymChangesAroundTableReplacements() throws Exception {
		try (Connection connection = createConnection("alias-order-" + UUID.randomUUID().toString().substring(0, 8));
				Statement sql = connection.createStatement()) {
			final var dialect = DialectResolver.getInstance().getDialect(connection);
			final var registry = dialect.createSqlFactoryRegistry();
			final var original = new Schema("Sales").setDialect(dialect);
			for (String name : List.of("Orders", "Archive")) {
				final var table = new Table(name);
				table.getColumns().add(new Column("id").setDataType(DataType.BIGINT).setNotNull(true));
				table.getConstraints().addPrimaryKeyConstraint("pk", table.getColumns().getFirst());
				original.getTables().add(table);
			}
			original.getSynonyms().add(new Synonym("FirstAlias").setObjectName("Orders"));
			original.getSynonyms().add(new Synonym("SecondAlias").setObjectName("Archive"));
			for (final var operation : registry.createSql(original, SqlType.CREATE))
				sql.execute(operation.getSqlText());
			sql.executeUpdate("INSERT INTO Sales.Orders (id) VALUES (7)");
			sql.executeUpdate("INSERT INTO Sales.Archive (id) VALUES (9)");
			final var swapped = original.clone().setDialect(dialect);
			swapped.getSynonyms().get("FirstAlias").setObjectName("Archive");
			swapped.getSynonyms().get("SecondAlias").setObjectName("Orders");
			final var swap = registry.createSql(original.diff(swapped));
			assertEquals(4, swap.size());
			for (final var operation : swap)
				sql.execute(operation.getSqlText());
			try (final var rows = sql
					.executeQuery("SELECT a.id, b.id FROM Sales.FirstAlias a " + "CROSS JOIN Sales.SecondAlias b")) {
				assertTrue(rows.next());
				assertEquals(9L, rows.getLong(1));
				assertEquals(7L, rows.getLong(2));
			}
			final var replacement = swapped.clone();
			replacement.getTables().remove(replacement.getTables().get("Orders"));
			final var future = new Table("FutureOrders");
			future.getColumns().add(new Column("id").setDataType(DataType.BIGINT).setNotNull(true));
			future.getConstraints().addPrimaryKeyConstraint("pk", future.getColumns().getFirst());
			replacement.getTables().add(future);
			replacement.getSynonyms().get("SecondAlias").setObjectName("FutureOrders");
			final Schema restored = SchemaUtils.readXml(new StringReader(replacement.asXml()));
			final var replace = registry.createSql(swapped.diff(restored));
			assertEquals(4, replace.size());
			for (final var operation : replace)
				sql.execute(operation.getSqlText());
			sql.executeUpdate("INSERT INTO Sales.FutureOrders (id) VALUES (11)");
			try (final var rows = sql
					.executeQuery("SELECT a.id, b.id FROM Sales.FirstAlias a " + "CROSS JOIN Sales.SecondAlias b")) {
				assertTrue(rows.next());
				assertEquals(9L, rows.getLong(1));
				assertEquals(11L, rows.getLong(2));
			}
			assertThrows(SQLException.class, () -> sql.executeQuery("SELECT id FROM Sales.Orders").close());
		}
	}

	@Test
	void changesModeledSynonymTargetsWithoutChangingTableData() throws Exception {
		try (Connection connection = createConnection("synonym-" + UUID.randomUUID().toString().substring(0, 8));
				Statement sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA Sales");
			sql.execute("CREATE TABLE Sales.CurrentOrders (id INT64 NOT NULL) PRIMARY KEY (id)");
			sql.execute("CREATE TABLE Sales.ArchivedOrders (id INT64 NOT NULL) PRIMARY KEY (id)");
			sql.executeUpdate("INSERT INTO Sales.CurrentOrders (id) VALUES (7)");
			sql.executeUpdate("INSERT INTO Sales.ArchivedOrders (id) VALUES (9)");
			final var dialect = DialectResolver.getInstance().getDialect(connection);
			final var registry = dialect.createSqlFactoryRegistry();
			final var original = new Synonym("Select").setSchemaName("Sales").setObjectName("CurrentOrders")
					.setDialect(dialect);
			sql.execute(registry.createSql(original, SqlType.CREATE).getFirst().getSqlText());
			try (final var rows = sql.executeQuery("SELECT id FROM Sales.`Select`")) {
				assertTrue(rows.next());
				assertEquals(7L, rows.getLong(1));
			}
			final Synonym target = SchemaUtils
					.readXml(new StringReader(original.clone().setObjectName("ArchivedOrders").asXml()));
			final var operations = registry.createSql(original.diff(target));
			assertEquals(2, operations.size());
			for (final var operation : operations)
				sql.execute(operation.getSqlText());
			try (final var rows = sql.executeQuery("SELECT id FROM Sales.`Select`")) {
				assertTrue(rows.next());
				assertEquals(9L, rows.getLong(1));
			}
			for (final var operation : registry.createSql(target.diff(target.clone().setName("LegacyOrders"))))
				sql.execute(operation.getSqlText());
			assertThrows(SQLException.class, () -> sql.executeQuery("SELECT id FROM Sales.`Select`").close());
			try (final var rows = sql.executeQuery(
					"SELECT a.id, c.id FROM Sales.LegacyOrders a " + "CROSS JOIN Sales.CurrentOrders c")) {
				assertTrue(rows.next());
				assertEquals(9L, rows.getLong(1));
				assertEquals(7L, rows.getLong(2));
			}
		}
	}

	@Test
	void renamesNamedTablesWithoutRecreatingTheirDataOrIndexes() throws Exception {
		try (Connection connection = createConnection("rename-" + UUID.randomUUID().toString().substring(0, 8));
				Statement sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA Sales");
			sql.execute("CREATE TABLE Sales.Original (id INT64 NOT NULL) PRIMARY KEY (id)");
			sql.execute("CREATE INDEX Sales.ById ON Sales.Original (id)");
			sql.execute("CREATE TABLE Sales.Child (id INT64 NOT NULL, child_id INT64 NOT NULL) "
					+ "PRIMARY KEY (id, child_id), INTERLEAVE IN PARENT Sales.Original ON DELETE CASCADE");
			sql.executeUpdate("INSERT INTO Sales.Original (id) VALUES (7)");
			sql.executeUpdate("INSERT INTO Sales.Child (id, child_id) VALUES (7, 1)");
			final var dialect = DialectResolver.getInstance().getDialect(connection);
			final var reader = dialect.getCatalogReader().getSchemaReader();
			final var original = reader.getAllFull(connection).stream()
					.filter(schema -> "Sales".equals(schema.getName())).findFirst().orElseThrow().getTables()
					.get("Original");
			final Table target = SchemaUtils.readXml(new StringReader(original.clone().setName("Order").asXml()));
			final var operations = dialect.createSqlFactoryRegistry().createSql(original.diff(target));
			assertEquals(1, operations.size());
			sql.execute(operations.getFirst().getSqlText());
			try (final var rows = sql.executeQuery("SELECT id FROM Sales.`Order`")) {
				assertTrue(rows.next());
				assertEquals(7L, rows.getLong(1));
			}
			assertThrows(SQLException.class, () -> sql.executeQuery("SELECT id FROM Sales.Original").close());
			final var schema = reader.getAllFull(connection).stream().filter(value -> "Sales".equals(value.getName()))
					.findFirst().orElseThrow();
			assertNull(schema.getTables().get("Original"));
			assertNotNull(schema.getTables().get("Order").getIndexes().get("ById"));
			assertEquals("Order",
					schema.getTables().get("Child").getSpecifics().get(SpannerCreateTableFactory.PARENT_TABLE_NAME));
			assertEquals("CASCADE",
					schema.getTables().get("Child").getSpecifics().get(SpannerCreateTableFactory.ON_DELETE_ACTION));
			try (final var rows = sql.executeQuery("SELECT child_id FROM Sales.Child WHERE id = 7")) {
				assertTrue(rows.next());
				assertEquals(1L, rows.getLong(1));
			}
			sql.executeUpdate("INSERT INTO Sales.Child (id, child_id) VALUES (7, 2)");
			assertThrows(SQLException.class,
					() -> sql.executeUpdate("INSERT INTO Sales.Child (id, child_id) VALUES (999, 1)"));
		}
	}

	@Test
	void retainsForeignKeyReferencesWhenRenamingTheirTarget() throws Exception {
		try (Connection connection = createConnection("rename-fk-" + UUID.randomUUID().toString().substring(0, 8));
				Statement sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA Sales");
			sql.execute("CREATE TABLE Sales.Original (id INT64 NOT NULL) PRIMARY KEY (id)");
			sql.execute("CREATE TABLE Sales.Referrer (id INT64 NOT NULL, original_id INT64, "
					+ "CONSTRAINT RefOriginal FOREIGN KEY (original_id) REFERENCES Sales.Original (id)) PRIMARY KEY (id)");
			sql.executeUpdate("INSERT INTO Sales.Original (id) VALUES (7)");
			sql.executeUpdate("INSERT INTO Sales.Referrer (id, original_id) VALUES (1, 7)");
			final var dialect = DialectResolver.getInstance().getDialect(connection);
			final var reader = dialect.getCatalogReader().getSchemaReader();
			final var original = reader.getAllFull(connection).stream()
					.filter(schema -> "Sales".equals(schema.getName())).findFirst().orElseThrow().getTables()
					.get("Original");
			final var operations = dialect.createSqlFactoryRegistry()
					.createSql(original.diff(original.clone().setName("Renamed")));
			assertEquals(1, operations.size());
			try {
				sql.execute(operations.getFirst().getSqlText());
			} catch (SQLException exception) {
				final String message = exception.getMessage();
				org.junit.jupiter.api.Assumptions.assumeFalse(
						message != null && message.contains("GOOGLESQL_RET_CHECK failure")
								&& message.contains("foreign_key_validator.cc")
								&& message.contains("Sales.Renamed vs. Sales.Original"),
						"Local emulator rejects renaming a referenced table in its foreign key validator");
				throw exception;
			}
			final var schema = reader.getAllFull(connection).stream().filter(value -> "Sales".equals(value.getName()))
					.findFirst().orElseThrow();
			final var reference = (ForeignKeyConstraint) schema.getTables().get("Referrer").getConstraints()
					.get("RefOriginal");
			assertNotNull(reference);
			assertEquals("Renamed", reference.getRelatedTableName());
			assertEquals("Sales", reference.getRelatedTableSchemaName());
			try (final var rows = sql.executeQuery("SELECT original_id FROM Sales.Referrer WHERE id = 1")) {
				assertTrue(rows.next());
				assertEquals(7L, rows.getLong(1));
			}
			sql.executeUpdate("INSERT INTO Sales.Renamed (id) VALUES (8)");
			sql.executeUpdate("INSERT INTO Sales.Referrer (id, original_id) VALUES (2, 8)");
			assertThrows(SQLException.class,
					() -> sql.executeUpdate("INSERT INTO Sales.Referrer (id, original_id) VALUES (3, 999)"));
		}
	}

	@Test
	void fencesJdbcJobLeaseOwnersAcrossConnections() throws Exception {
		try (Connection first = createConnection(); Connection second = createConnection()) {
			BulkMigrationJobAssertions.assertJdbcLeaseOwnerFencing(first, second);
		}
	}

	@Test
	void readsJdbcCheckpointWithoutMutatingItsTable() throws Exception {
		try (Connection connection = createConnection()) {
			BulkMigrationTransactionAssertions.assertJdbcCheckpointReadOnly(connection);
			BulkMigrationTransactionAssertions.assertJdbcMaintenanceReadOnly(connection);
		}
	}

	@Test
	void readsRepresentativeSchemaObjectsFromCurrentEmulator() throws Exception {
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			statement.execute(
					"ALTER DATABASE `metadata-db` SET OPTIONS " + "(default_sequence_kind='bit_reversed_positive')");
			statement.execute("""
					CREATE SEQUENCE metadata_sequence OPTIONS (
					 sequence_kind='bit_reversed_positive', start_with_counter=1000,
					 skip_range_min=100, skip_range_max=199)
					""");
			statement.execute("""
					CREATE TABLE metadata_parent (
					 id INT64 NOT NULL GENERATED BY DEFAULT AS IDENTITY
					  (BIT_REVERSED_POSITIVE START COUNTER WITH 2000
					   SKIP RANGE 200, 299),
					 code STRING(30) NOT NULL,
					 amount NUMERIC,
					 normalized_code STRING(30) AS (LOWER(code)) STORED,
					 created_at TIMESTAMP DEFAULT (PENDING_COMMIT_TIMESTAMP())
					  OPTIONS (allow_commit_timestamp=true),
					 updated_at TIMESTAMP DEFAULT (PENDING_COMMIT_TIMESTAMP())
					  ON UPDATE (PENDING_COMMIT_TIMESTAMP())
					  OPTIONS (allow_commit_timestamp=true),
					 tokenized_code TOKENLIST AS (TOKENIZE_FULLTEXT(code)) HIDDEN,
					 embedding ARRAY<FLOAT32>(vector_length=>3)
					 ,CONSTRAINT ck_metadata_parent_amount
					  CHECK (amount IS NULL OR amount >= 0)
					) PRIMARY KEY (id)
					""");
			statement.execute("CREATE UNIQUE INDEX uq_metadata_parent_code " + "ON metadata_parent(code)");
			statement
					.execute("CREATE SEARCH INDEX search_metadata_parent_code " + "ON metadata_parent(tokenized_code)");
			statement.execute("CREATE VECTOR INDEX vector_metadata_parent_embedding "
					+ "ON metadata_parent(embedding) WHERE embedding IS NOT NULL " + "OPTIONS(distance_type='COSINE')");
			statement.execute("""
					CREATE TABLE metadata_composite_parent (
					 tenant_id INT64 NOT NULL,
					 parent_id INT64 NOT NULL
					) PRIMARY KEY (tenant_id, parent_id)
					""");
			statement.execute("""
					CREATE TABLE metadata_child (
					 id INT64 NOT NULL,
					 parent_id INT64 NOT NULL,
					 tenant_id INT64,
					 composite_parent_id INT64,
					 code STRING(30),
					 CONSTRAINT fk_metadata_child_parent FOREIGN KEY (parent_id)
					  REFERENCES metadata_parent (id) NOT ENFORCED,
					 CONSTRAINT fk_metadata_child_composite
					  FOREIGN KEY (tenant_id, composite_parent_id)
					  REFERENCES metadata_composite_parent (tenant_id, parent_id)
					  ON DELETE CASCADE
					) PRIMARY KEY (id)
					""");
			statement.execute("CREATE INDEX idx_metadata_child_code " + "ON metadata_child(code DESC)");
			statement.execute("CREATE NULL_FILTERED INDEX idx_metadata_child_filtered "
					+ "ON metadata_child(code) STORING (parent_id)");
			statement.execute("""
					CREATE TABLE metadata_interleaved_child (
					 id INT64 NOT NULL,
					 child_id INT64 NOT NULL,
					 payload STRING(30)
					) PRIMARY KEY (id, child_id DESC),
					INTERLEAVE IN PARENT metadata_parent ON DELETE CASCADE
					""");
			statement.execute("CREATE VIEW metadata_view SQL SECURITY INVOKER AS "
					+ "SELECT p.id, p.code FROM metadata_parent AS p");

			var dialect = DialectResolver.getInstance().getDialect(connection);
			assertInstanceOf(Spanner.class, dialect);
			var schemas = dialect.getCatalogReader().getSchemaReader().getAllFull(connection);
			var schema = schemas.stream().filter(s -> s.getTables().get("metadata_parent") != null).findFirst()
					.orElseThrow();
			var parent = schema.getTables().get("metadata_parent");
			var identity = parent.getColumns().get("id");
			assertTrue(identity.isIdentity());
			assertEquals(2000L, identity.getIdentityStartValue().longValue());
			assertEquals(Boolean.TRUE,
					identity.getSpecifics().get(SpannerSqlBuilder.IDENTITY_BIT_REVERSED_POSITIVE, Boolean.class));
			assertEquals(200L,
					identity.getSpecifics().get(SpannerSqlBuilder.IDENTITY_SKIP_RANGE_MIN, Long.class).longValue());
			assertEquals(299L,
					identity.getSpecifics().get(SpannerSqlBuilder.IDENTITY_SKIP_RANGE_MAX, Long.class).longValue());
			var normalizedCode = parent.getColumns().get("normalized_code");
			assertEquals("LOWER(code)", normalizedCode.getFormula());
			assertTrue(normalizedCode.isFormulaPersisted());
			var createdAt = parent.getColumns().get("created_at");
			assertTrue(createdAt.getDefaultValue().contains("PENDING_COMMIT_TIMESTAMP"));
			assertEquals("TRUE", createdAt.getSpecifics().get("allow_commit_timestamp"));
			var updatedAt = parent.getColumns().get("updated_at");
			assertTrue(updatedAt.getOnUpdate().contains("PENDING_COMMIT_TIMESTAMP"));
			assertEquals("TRUE", updatedAt.getSpecifics().get("allow_commit_timestamp"));
			assertTrue(parent.getColumns().get("tokenized_code").isHidden());
			assertNotNull(parent.getColumns().get("embedding"));
			var primaryKey = parent.getConstraints().stream().filter(UniqueConstraint.class::isInstance)
					.map(UniqueConstraint.class::cast).filter(UniqueConstraint::isPrimaryKey).findFirst().orElseThrow();
			assertEquals("id", primaryKey.getColumns().get(0).getName());
			assertTrue(parent.getIndexes().get("uq_metadata_parent_code").isUnique());
			var searchIndex = parent.getIndexes().get("search_metadata_parent_code");
			assertNotNull(searchIndex);
			assertEquals(IndexType.FullText, searchIndex.getIndexType());
			assertEquals("tokenized_code", searchIndex.getColumns().get(0).getName());
			var vectorIndex = parent.getIndexes().get("vector_metadata_parent_embedding");
			assertNotNull(vectorIndex);
			assertEquals(IndexType.Vector, vectorIndex.getIndexType());
			assertEquals("embedding", vectorIndex.getColumns().get(0).getName());
			var amountCheck = parent.getConstraints().stream().filter(CheckConstraint.class::isInstance)
					.map(CheckConstraint.class::cast)
					.filter(constraint -> "ck_metadata_parent_amount".equals(constraint.getName())).findFirst()
					.orElseThrow();
			assertTrue(amountCheck.getExpression().contains("amount >= 0"));
			var child = schema.getTables().get("metadata_child");
			var orderedIndex = child.getIndexes().get("idx_metadata_child_code");
			assertNotNull(orderedIndex);
			assertEquals(Order.Desc, orderedIndex.getColumns().get(0).getOrder());
			var filteredIndex = child.getIndexes().get("idx_metadata_child_filtered");
			assertNotNull(filteredIndex);
			assertEquals(Boolean.TRUE, filteredIndex.getSpecifics().get("is_null_filtered", Boolean.class));
			assertEquals("parent_id", filteredIndex.getIncludes().get(0).getName());
			var foreignKey = child.getConstraints().stream().filter(ForeignKeyConstraint.class::isInstance)
					.map(ForeignKeyConstraint.class::cast)
					.filter(constraint -> "fk_metadata_child_parent".equals(constraint.getName())).findFirst()
					.orElseThrow();
			assertEquals("parent_id", foreignKey.getColumns().get(0).getName());
			assertEquals("id", foreignKey.getRelatedColumns().get(0).getName());
			assertTrue(!foreignKey.isEnable());
			assertNotNull(foreignKey.getSpecifics().get("spanner_state"));
			var compositeForeignKey = child.getConstraints().stream().filter(ForeignKeyConstraint.class::isInstance)
					.map(ForeignKeyConstraint.class::cast)
					.filter(constraint -> "fk_metadata_child_composite".equals(constraint.getName())).findFirst()
					.orElseThrow();
			assertEquals("tenant_id", compositeForeignKey.getColumns().get(0).getName());
			assertEquals("composite_parent_id", compositeForeignKey.getColumns().get(1).getName());
			assertEquals("tenant_id", compositeForeignKey.getRelatedColumns().get(0).getName());
			assertEquals("parent_id", compositeForeignKey.getRelatedColumns().get(1).getName());
			assertEquals(CascadeRule.Cascade, compositeForeignKey.getDeleteRule());
			assertTrue(compositeForeignKey.isEnable());
			var interleaved = schema.getTables().get("metadata_interleaved_child");
			assertNotNull(interleaved);
			assertEquals("metadata_parent", interleaved.getSpecifics().get("parent_table_name"));
			assertEquals("CASCADE", interleaved.getSpecifics().get("on_delete_action"));
			var interleavedPrimaryKey = interleaved.getConstraints().stream().filter(UniqueConstraint.class::isInstance)
					.map(UniqueConstraint.class::cast).filter(UniqueConstraint::isPrimaryKey).findFirst().orElseThrow();
			assertEquals("id", interleavedPrimaryKey.getColumns().get(0).getName());
			assertEquals("child_id", interleavedPrimaryKey.getColumns().get(1).getName());
			assertEquals(Order.Desc, interleavedPrimaryKey.getColumns().get(1).getOrder());
			var sequence = schema.getSequences().get("metadata_sequence");
			assertNotNull(sequence);
			assertEquals(1000L, sequence.getStartValue().longValue());
			assertEquals(100L,
					sequence.getSpecifics().get(SpannerCreateSequenceFactory.SKIP_RANGE_MIN, Long.class).longValue());
			assertEquals(199L,
					sequence.getSpecifics().get(SpannerCreateSequenceFactory.SKIP_RANGE_MAX, Long.class).longValue());
			var view = schema.getViews().get("metadata_view");
			assertNotNull(view);
			String viewStatement = String.join("\n", view.getStatement()).toLowerCase();
			assertTrue(viewStatement.contains("select p.id, p.code"), viewStatement);
			assertTrue(viewStatement.contains("from metadata_parent as p"), viewStatement);
			assertEquals(2, view.getColumns().size());
			assertEquals("id", view.getColumns().get(0).getName());
			assertEquals("code", view.getColumns().get(1).getName());
		}
	}

	@Test
	void setsAndDropsCommitTimestampOnUpdate() throws Exception {
		final String suffix = UUID.randomUUID().toString().substring(0, 8);
		try (Connection connection = createConnection("on-update-" + suffix);
				Statement sql = connection.createStatement()) {
			sql.execute("""
					CREATE TABLE update_fixture (
					  id INT64 NOT NULL,
					  changed_at TIMESTAMP NOT NULL DEFAULT (PENDING_COMMIT_TIMESTAMP())
					    OPTIONS (allow_commit_timestamp=true)
					) PRIMARY KEY (id)
					""");
			final var dialect = DialectResolver.getInstance().getDialect(connection);
			final var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
			final var registry = dialect.createSqlFactoryRegistry();
			final Table original = reader.getAllFull(connection).stream()
					.filter(table -> "update_fixture".equals(table.getName())).findFirst().orElseThrow();
			final var target = original.clone();
			target.getColumns().get("changed_at").setOnUpdate("PENDING_COMMIT_TIMESTAMP()");
			final var add = registry.createSql(original.diff(target), SqlType.ALTER);
			assertEquals(1, add.size());
			sql.execute(add.getFirst().getSqlText());
			final Table changed = reader.getAllFull(connection).stream()
					.filter(table -> "update_fixture".equals(table.getName())).findFirst().orElseThrow();
			assertEquals("PENDING_COMMIT_TIMESTAMP()", changed.getColumns().get("changed_at").getOnUpdate());

			final Table withoutExpressions = changed.clone();
			withoutExpressions.getColumns().get("changed_at").setOnUpdate(null).setDefaultValue(null);
			final var dropBoth = registry.createSql(changed.diff(withoutExpressions), SqlType.ALTER);
			assertEquals(1, dropBoth.size());
			sql.execute(dropBoth.getFirst().getSqlText());
			final Table cleared = reader.getAllFull(connection).stream()
					.filter(table -> "update_fixture".equals(table.getName())).findFirst().orElseThrow();
			final var clearedColumn = cleared.getColumns().get("changed_at");
			assertNull(clearedColumn.getDefaultValue());
			assertNull(clearedColumn.getOnUpdate());
			assertTrue(clearedColumn.isNotNull());
			assertEquals(Boolean.TRUE, SpannerSqlBuilder.allowCommitTimestamp(clearedColumn));

			final var addBoth = registry.createSql(cleared.diff(changed), SqlType.ALTER);
			assertEquals(1, addBoth.size());
			sql.execute(addBoth.getFirst().getSqlText());
			final Table withExpressions = reader.getAllFull(connection).stream()
					.filter(table -> "update_fixture".equals(table.getName())).findFirst().orElseThrow();
			final var updated = withExpressions.getColumns().get("changed_at");
			assertEquals("PENDING_COMMIT_TIMESTAMP()", updated.getDefaultValue());
			assertEquals(updated.getDefaultValue(), updated.getOnUpdate());

			final var drop = registry.createSql(withExpressions.diff(original), SqlType.ALTER);
			assertEquals(1, drop.size());
			sql.execute(drop.getFirst().getSqlText());
			final Table restored = reader.getAllFull(connection).stream()
					.filter(table -> "update_fixture".equals(table.getName())).findFirst().orElseThrow();
			assertNull(restored.getColumns().get("changed_at").getOnUpdate());
			assertEquals(original.getColumns().get("changed_at").getDefaultValue(),
					restored.getColumns().get("changed_at").getDefaultValue());
		}
	}

	@Test
	void changesIdentitySkipRangeOnAnExistingPrimaryKey() throws Exception {
		final String suffix = UUID.randomUUID().toString().substring(0, 8);
		try (Connection connection = createConnection("identity-skip-" + suffix);
				Statement sql = connection.createStatement()) {
			sql.execute("""
					CREATE TABLE identity_fixture (
					  id INT64 NOT NULL GENERATED BY DEFAULT AS IDENTITY
					    (BIT_REVERSED_POSITIVE START COUNTER WITH 321)
					) PRIMARY KEY (id)
					""");
			final var dialect = DialectResolver.getInstance().getDialect(connection);
			final var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
			final var registry = dialect.createSqlFactoryRegistry();
			final Table original = reader.getAllFull(connection).stream()
					.filter(table -> "identity_fixture".equals(table.getName())).findFirst().orElseThrow();
			final var target = original.clone();
			final var identity = target.getColumns().get("id");
			identity.getSpecifics().put(SpannerSqlBuilder.IDENTITY_SKIP_RANGE_MIN, 100L);
			identity.getSpecifics().put(SpannerSqlBuilder.IDENTITY_SKIP_RANGE_MAX, 200L);
			final var add = registry.createSql(original.diff(target), SqlType.ALTER);
			assertEquals(1, add.size());
			sql.execute(add.getFirst().getSqlText());
			final Table changed = reader.getAllFull(connection).stream()
					.filter(table -> "identity_fixture".equals(table.getName())).findFirst().orElseThrow();
			final var changedIdentity = changed.getColumns().get("id");
			assertEquals("100", changedIdentity.getSpecifics().get(SpannerSqlBuilder.IDENTITY_SKIP_RANGE_MIN));
			assertEquals("200", changedIdentity.getSpecifics().get(SpannerSqlBuilder.IDENTITY_SKIP_RANGE_MAX));
			assertEquals(321L, changedIdentity.getIdentityStartValue());

			final var drop = registry.createSql(changed.diff(original), SqlType.ALTER);
			assertEquals(1, drop.size());
			sql.execute(drop.getFirst().getSqlText());
			final Table restored = reader.getAllFull(connection).stream()
					.filter(table -> "identity_fixture".equals(table.getName())).findFirst().orElseThrow();
			final var restoredIdentity = restored.getColumns().get("id");
			assertNull(restoredIdentity.getSpecifics().get(SpannerSqlBuilder.IDENTITY_SKIP_RANGE_MIN));
			assertNull(restoredIdentity.getSpecifics().get(SpannerSqlBuilder.IDENTITY_SKIP_RANGE_MAX));
			assertEquals(321L, restoredIdentity.getIdentityStartValue());
		}
	}

	@Test
	void changesSequenceSkipRangesUsingTheOriginalDdlStyle() throws Exception {
		final String suffix = UUID.randomUUID().toString().substring(0, 8);
		try (Connection connection = createConnection("sequence-style-" + suffix);
				Statement sql = connection.createStatement()) {
			sql.execute("CREATE SEQUENCE clause_seq BIT_REVERSED_POSITIVE START COUNTER WITH 321");
			sql.execute(
					"CREATE SEQUENCE option_seq OPTIONS (sequence_kind='bit_reversed_positive', start_with_counter=321)");
			final var dialect = DialectResolver.getInstance().getDialect(connection);
			final var reader = dialect.getCatalogReader().getSchemaReader().getSequenceReader();
			final var registry = dialect.createSqlFactoryRegistry();
			final var sequences = reader.getAll(connection);
			assertEquals(2, sequences.size());
			for (final var original : sequences) {
				final var target = original.clone();
				final String style = "clause_seq".equals(original.getName()) ? "CLAUSES" : "OPTIONS";
				target.getSpecifics().put(SpannerAlterSequenceFactory.SEQUENCE_DDL_STYLE, style);
				target.getSpecifics().put(SpannerCreateSequenceFactory.SKIP_RANGE_MIN, 100L);
				target.getSpecifics().put(SpannerCreateSequenceFactory.SKIP_RANGE_MAX, 200L);
				final var add = registry.createSql(original.diff(target));
				assertEquals(1, add.size());
				sql.execute(add.getFirst().getSqlText());
				final var changed = reader.getAll(connection).stream()
						.filter(sequence -> original.getName().equals(sequence.getName())).findFirst().orElseThrow();
				assertEquals("100", changed.getSpecifics().get(SpannerCreateSequenceFactory.SKIP_RANGE_MIN));
				assertEquals("200", changed.getSpecifics().get(SpannerCreateSequenceFactory.SKIP_RANGE_MAX));
				assertEquals(original.getStartValue(), changed.getStartValue());

				changed.getSpecifics().put(SpannerAlterSequenceFactory.SEQUENCE_DDL_STYLE, style);
				final var drop = registry.createSql(changed.diff(original));
				assertEquals(1, drop.size());
				sql.execute(drop.getFirst().getSqlText());
				final var restored = reader.getAll(connection).stream()
						.filter(sequence -> original.getName().equals(sequence.getName())).findFirst().orElseThrow();
				assertNull(restored.getSpecifics().get(SpannerCreateSequenceFactory.SKIP_RANGE_MIN));
				assertNull(restored.getSpecifics().get(SpannerCreateSequenceFactory.SKIP_RANGE_MAX));
				assertEquals(original.getStartValue(), restored.getStartValue());
				registry.getOptions().setDropIfExists(true);
				final String dropSequence = registry.createSql(restored, SqlType.DROP).getFirst().getSqlText();
				sql.execute(dropSequence);
				sql.execute(dropSequence);
			}
			assertTrue(reader.getAll(connection).isEmpty());
		}
	}

	@Test
	void recreatesInterleaveTtlAndIndexesFromSchemaXml() throws Exception {
		final String suffix = UUID.randomUUID().toString().substring(0, 8);
		try (Connection source = createConnection("roundtrip-source-" + suffix);
				Connection target = createConnection("roundtrip-target-" + suffix);
				Statement sourceSql = source.createStatement();
				Statement targetSql = target.createStatement()) {
			sourceSql.execute("CREATE TABLE roundtrip_parent (id INT64 NOT NULL) PRIMARY KEY (id)");
			sourceSql.execute("""
					CREATE TABLE roundtrip_child (
					  id INT64 NOT NULL, child_id INT64 NOT NULL, expires_at TIMESTAMP, label STRING(30),
					  tokens TOKENLIST AS (TOKENIZE_FULLTEXT(label)) HIDDEN,
					  embedding ARRAY<FLOAT32>(vector_length=>3)
					) PRIMARY KEY (id, child_id DESC),
					  INTERLEAVE IN roundtrip_parent,
					  ROW DELETION POLICY (OLDER_THAN(expires_at, INTERVAL 7 DAY))
					""");
			sourceSql.execute("CREATE INDEX roundtrip_label ON roundtrip_child(label) WHERE label IS NOT NULL");
			sourceSql.execute("CREATE SEARCH INDEX roundtrip_search ON roundtrip_child(tokens) "
					+ "PARTITION BY id ORDER BY child_id DESC");
			sourceSql.execute("CREATE VECTOR INDEX roundtrip_vector ON roundtrip_child(embedding) "
					+ "STORING (label) WHERE embedding IS NOT NULL "
					+ "OPTIONS (distance_type='DOT_PRODUCT', tree_depth=2, num_leaves=10)");

			final var dialect = DialectResolver.getInstance().getDialect(source);
			final Schema original = readRoundTripSchema(source);
			final var registry = dialect.createSqlFactoryRegistry();
			final var vector = original.getTables().get("roundtrip_child").getIndexes().get("roundtrip_vector");
			if (vector.getVectorDistanceType() == null) {
				final var error = assertThrows(IllegalArgumentException.class,
						() -> registry.createSql(vector, SqlType.CREATE));
				assertTrue(error.getMessage().contains("requires an explicit distance type"), error.getMessage());
				// Supply the known source setting when the emulator lacks INDEX_OPTIONS.
				vector.setVectorDistanceType(VectorDistanceType.DotProduct);
			}
			vector.getSpecifics().putIfAbsent(SpannerCreateIndexFactory.TREE_DEPTH, "2");
			vector.getSpecifics().putIfAbsent(SpannerCreateIndexFactory.NUM_LEAVES, "10");
			final Schema restored = SchemaUtils.readXml(new StringReader(original.asXml()));
			final var ddl = registry.createSql(restored, SqlType.CREATE);
			assertTrue(ddl.getFirst().getSqlText().startsWith("CREATE TABLE"));
			for (final var operation : ddl)
				targetSql.execute(operation.getSqlText());

			final Schema recreated = readRoundTripSchema(target);
			final Table recreatedChild = recreated.getTables().get("roundtrip_child");
			assertEquals("IN", recreatedChild.getSpecifics().get(SpannerCreateTableFactory.INTERLEAVE_TYPE));
			assertEquals("roundtrip_parent",
					recreatedChild.getSpecifics().get(SpannerCreateTableFactory.PARENT_TABLE_NAME));
			assertEquals("OLDER_THAN(expires_at, INTERVAL 7 DAY)",
					recreatedChild.getSpecifics().get(SpannerCreateTableFactory.ROW_DELETION_POLICY_EXPRESSION));
			assertTrue(recreatedChild.getIndexes().get("roundtrip_label").getWhere().contains("IS NOT NULL"));
			final var key = recreatedChild.getConstraints().getPrimaryKeyConstraint();
			assertEquals("id", key.getColumns().get(0).getName());
			assertEquals(Order.Asc, key.getColumns().get(0).getOrder());
			assertEquals("child_id", key.getColumns().get(1).getName());
			assertEquals(Order.Desc, key.getColumns().get(1).getOrder());
			final var search = recreatedChild.getIndexes().get("roundtrip_search");
			assertEquals(IndexType.FullText, search.getIndexType());
			assertEquals("tokens", search.getColumns().get(0).getName());
			assertEquals("id", search.getPartitioning().getPartitioningColumns().get(0).getName());
			assertEquals("child_id", search.getSpecifics().get("SEARCH_ORDER_BY_COLUMN"));
			assertEquals("DESC", search.getSpecifics().get("SEARCH_ORDER_BY_DIRECTION"));
			assertEquals(Integer.valueOf(3), recreatedChild.getColumns().get("embedding").getSpecifics()
					.get(SpannerSqlBuilder.VECTOR_LENGTH, Integer.class));
			final var recreatedVector = recreatedChild.getIndexes().get("roundtrip_vector");
			assertEquals(IndexType.Vector, recreatedVector.getIndexType());
			assertEquals("label", recreatedVector.getIncludes().get(0).getName());
			assertTrue(recreatedVector.getWhere().contains("IS NOT NULL"));
			final var jdbc = target.unwrap(CloudSpannerJdbcConnection.class);
			final String vectorDdl = jdbc.getSpanner().getDatabaseAdminClient()
					.getDatabaseDdl("test-instance", jdbc.getDatabaseId().getDatabase()).stream()
					.filter(sql -> sql.startsWith("CREATE VECTOR INDEX ") && sql.contains("roundtrip_vector"))
					.findFirst().orElseThrow();
			final String options = vectorDdl.toLowerCase(Locale.ROOT).replaceAll("[\\s'\"\\x60]", "");
			assertTrue(options.contains("distance_type=dot_product") && options.contains("tree_depth=2")
					&& options.contains("num_leaves=10"), vectorDdl);

			final Table withStoredColumn = recreatedChild.clone();
			withStoredColumn.getColumns().add(new Column("stored_note").setDataType(DataType.BIGINT));
			withStoredColumn.getIndexes().stream().filter(index -> index.getIndexType() != IndexType.FullText)
					.forEach(index -> index.getIncludes().add("stored_note"));
			final var addStored = registry.createSql(recreatedChild.diff(withStoredColumn), SqlType.ALTER);
			assertEquals(3, addStored.size());
			for (final var operation : addStored)
				targetSql.execute(operation.getSqlText());
			final Table withStoredMetadata = readRoundTripSchema(target).getTables().get("roundtrip_child");
			assertEquals(2, withStoredMetadata.getIndexes().stream()
					.filter(index -> index.getIncludes().get("stored_note") != null).count());
			final var dropStored = registry.createSql(withStoredMetadata.diff(recreatedChild), SqlType.ALTER);
			assertEquals(3, dropStored.size());
			for (final var operation : dropStored)
				targetSql.execute(operation.getSqlText());
			assertNull(readRoundTripSchema(target).getTables().get("roundtrip_child").getColumns().get("stored_note"));

			final Table enforced = recreatedChild.clone();
			enforced.getSpecifics().put(SpannerCreateTableFactory.INTERLEAVE_TYPE, "IN PARENT");
			enforced.getSpecifics().put(SpannerCreateTableFactory.ON_DELETE_ACTION, "CASCADE");
			final var alters = registry.createSql(recreatedChild.diff(enforced), SqlType.ALTER);
			assertEquals(2, alters.size());
			for (final var operation : alters)
				targetSql.execute(operation.getSqlText());
			final Table migrated = readRoundTripSchema(target).getTables().get("roundtrip_child");
			assertEquals("IN PARENT", migrated.getSpecifics().get(SpannerCreateTableFactory.INTERLEAVE_TYPE));
			assertEquals("CASCADE", migrated.getSpecifics().get(SpannerCreateTableFactory.ON_DELETE_ACTION));

			final Table noTtl = migrated.clone();
			noTtl.getSpecifics().remove(SpannerCreateTableFactory.ROW_DELETION_POLICY_EXPRESSION);
			noTtl.getColumns().remove("expires_at");
			final var removeTtl = registry.createSql(migrated.diff(noTtl), SqlType.ALTER);
			assertEquals(2, removeTtl.size());
			for (final var operation : removeTtl)
				targetSql.execute(operation.getSqlText());
			final Table withoutTtl = readRoundTripSchema(target).getTables().get("roundtrip_child");
			assertNull(withoutTtl.getColumns().get("expires_at"));
			assertNull(withoutTtl.getSpecifics().get(SpannerCreateTableFactory.ROW_DELETION_POLICY_EXPRESSION));
			final var restoreTtl = registry.createSql(withoutTtl.diff(migrated), SqlType.ALTER);
			assertEquals(2, restoreTtl.size());
			for (final var operation : restoreTtl)
				targetSql.execute(operation.getSqlText());
			final Table withTtl = readRoundTripSchema(target).getTables().get("roundtrip_child");
			assertNotNull(withTtl.getColumns().get("expires_at"));
			assertEquals("OLDER_THAN(expires_at, INTERVAL 7 DAY)",
					withTtl.getSpecifics().get(SpannerCreateTableFactory.ROW_DELETION_POLICY_EXPRESSION));
			final Table newPolicy = withTtl.clone();
			newPolicy.getColumns().add(new Column("retention_at").setDataType(DataType.TIMESTAMP));
			newPolicy.getSpecifics().put(SpannerCreateTableFactory.ROW_DELETION_POLICY_EXPRESSION,
					"OLDER_THAN(retention_at, INTERVAL 14 DAY)");
			for (final var operation : registry.createSql(withTtl.diff(newPolicy), SqlType.ALTER))
				targetSql.execute(operation.getSqlText());
			final Table replaced = readRoundTripSchema(target).getTables().get("roundtrip_child");
			assertEquals("OLDER_THAN(retention_at, INTERVAL 14 DAY)",
					replaced.getSpecifics().get(SpannerCreateTableFactory.ROW_DELETION_POLICY_EXPRESSION));
			assertNotNull(replaced.getColumns().get("expires_at"));
		}
	}

	@Test
	void recreatesSameNamedTablesAndIndexesInSeparateSchemas() throws Exception {
		final String suffix = UUID.randomUUID().toString().substring(0, 8);
		try (Connection source = createConnection("named-source-" + suffix);
				Connection target = createConnection("named-target-" + suffix);
				Statement sourceSql = source.createStatement();
				Statement targetSql = target.createStatement()) {
			for (String name : List.of("Sales", "Archive")) {
				sourceSql.execute("CREATE SCHEMA " + name);
				sourceSql.execute("CREATE TABLE " + name + ".Parent (id INT64 NOT NULL) PRIMARY KEY (id)");
				sourceSql.execute("""
						CREATE TABLE %1$s.Child (id INT64 NOT NULL, seq INT64 NOT NULL, label STRING(30))
						PRIMARY KEY (id, seq), INTERLEAVE IN PARENT %1$s.Parent ON DELETE %2$s
						""".formatted(name, name.equals("Sales") ? "CASCADE" : "NO ACTION"));
				sourceSql.execute("CREATE INDEX " + name + ".ByLabel ON " + name + ".Child(label"
						+ (name.equals("Sales") ? " DESC) WHERE label IS NOT NULL" : ")"));
			}
			final var dialect = DialectResolver.getInstance().getDialect(source);
			final var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			int recreatedSchemas = 0;
			for (final Schema schema : dialect.getCatalogReader().getSchemaReader().getAllFull(source)) {
				if (!List.of("Sales", "Archive").contains(schema.getName()))
					continue;
				final Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
				restored.getSynonyms().add(new Synonym("LegacyParent").setObjectName("Parent"));
				final var operations = registry.createSql(restored, SqlType.CREATE);
				assertEquals(5, operations.size());
				assertEquals("CREATE SCHEMA `" + schema.getName() + "`", operations.getFirst().getSqlText());
				for (final var operation : operations)
					targetSql.execute(operation.getSqlText());
				targetSql.executeUpdate("INSERT INTO " + schema.getName() + ".Parent (id) VALUES (7)");
				try (final var rows = targetSql.executeQuery("SELECT id FROM " + schema.getName() + ".LegacyParent")) {
					assertTrue(rows.next());
					assertEquals(7L, rows.getLong(1));
				}
				final var drop = registry.createSql(restored.getSynonyms().getFirst(), SqlType.DROP);
				assertEquals(1, drop.size());
				targetSql.execute(drop.getFirst().getSqlText());
				assertThrows(SQLException.class,
						() -> targetSql.executeQuery("SELECT id FROM " + schema.getName() + ".LegacyParent").close());
				try (final var rows = targetSql.executeQuery("SELECT id FROM " + schema.getName() + ".Parent")) {
					assertTrue(rows.next());
					assertEquals(7L, rows.getLong(1));
				}
				recreatedSchemas++;
			}
			assertEquals(2, recreatedSchemas);
			final var disposableSchema = new Schema("DisposableSchema");
			targetSql.execute(registry.createSql(disposableSchema, SqlType.CREATE).getFirst().getSqlText());
			registry.getOptions().setDropIfExists(true);
			final String dropSchema = registry.createSql(disposableSchema, SqlType.DROP).getFirst().getSqlText();
			targetSql.execute(dropSchema);
			targetSql.execute(dropSchema);

			final var recreated = dialect.getCatalogReader().getSchemaReader().getAllFull(target);
			assertTrue(recreated.stream().noneMatch(schema -> "DisposableSchema".equals(schema.getName())));
			for (String name : List.of("Sales", "Archive")) {
				final var schema = recreated.stream().filter(value -> name.equals(value.getName())).findFirst()
						.orElseThrow();
				final var child = schema.getTables().get("Child");
				final var index = child.getIndexes().get("ByLabel");
				assertEquals(name, index.getSchemaName());
				assertEquals("Parent", child.getSpecifics().get(SpannerCreateTableFactory.PARENT_TABLE_NAME));
				assertEquals(name.equals("Sales") ? "CASCADE" : "NO ACTION",
						child.getSpecifics().get(SpannerCreateTableFactory.ON_DELETE_ACTION));
				assertEquals(name.equals("Sales") ? Order.Desc : Order.Asc, index.getColumns().get(0).getOrder());
				assertEquals(name.equals("Sales"), index.getWhere() != null);
			}
		}
	}

	private Schema readRoundTripSchema(final Connection connection) throws Exception {
		return DialectResolver.getInstance().getDialect(connection).getCatalogReader().getSchemaReader()
				.getAllFull(connection).stream().filter(schema -> schema.getTables().get("roundtrip_child") != null)
				.findFirst().orElseThrow();
	}

	private Connection createConnection() throws Exception {
		return createConnection("metadata-db");
	}

	private Connection createConnection(final String database) throws Exception {
		String url = "jdbc:cloudspanner://localhost:" + SPANNER.getMappedPort(9010)
				+ "/projects/test-project/instances/test-instance/databases/" + database
				+ ";usePlainText=true;autoConfigEmulator=true";
		return DriverManager.getConnection(url);
	}
}
