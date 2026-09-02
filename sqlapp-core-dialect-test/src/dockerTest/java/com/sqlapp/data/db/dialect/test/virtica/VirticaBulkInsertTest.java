/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.virtica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.command.migration.FileBulkMigrationCheckpointStore;
import com.sqlapp.data.db.dialect.test.BulkMigrationJobAssertions;
import com.sqlapp.data.db.dialect.test.BulkMigrationRepairAssertions;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.db.dialect.test.BulkMigrationTransactionAssertions;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointMode;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;

/** Exercises VerticaCopyStream against Vertica CE. */
class VirticaBulkInsertTest {
	private static final GenericContainer<?> VERTICA =
			ReusableTestcontainers.configure(new GenericContainer<>(
					DockerImageName.parse("ratiopbc/vertica-ce:v25.1.0-0"))
					.withExposedPorts(5433)
					.waitingFor(Wait.forLogMessage(
							".*Vertica is now running.*\\n", 1)
							.withStartupTimeout(Duration.ofMinutes(3))));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(VERTICA);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(VERTICA);
	}

	@Test
	void fencesJdbcJobLeaseOwnersAcrossConnections() throws Exception {
		try (Connection first = createConnection();
				Connection second = createConnection()) {
			BulkMigrationJobAssertions.assertJdbcLeaseOwnerFencing(first, second);
		}
	}

	@Test
	void migratesParentBeforeChildAndAggregatesFileCheckpointStatus(
			@TempDir final Path checkpointDirectory) throws Exception {
		try (Connection connection = createConnection(); var statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS sqlapp_bulk_job_child_vertica");
			statement.execute("DROP TABLE IF EXISTS sqlapp_bulk_job_parent_vertica");
			statement.execute("CREATE TABLE sqlapp_bulk_job_parent_vertica "
					+ "(id INT PRIMARY KEY, txt VARCHAR(100))");
			statement.execute("CREATE TABLE sqlapp_bulk_job_child_vertica "
					+ "(id INT PRIMARY KEY, parent_id INT NOT NULL, txt VARCHAR(100), "
					+ "FOREIGN KEY (parent_id) REFERENCES sqlapp_bulk_job_parent_vertica(id))");
			final Table parent = jobTable("sqlapp_bulk_job_parent_vertica", false);
			parent.getRows().add(row -> { row.put("id", 1); row.put("txt", "parent"); });
			final Table child = jobTable("sqlapp_bulk_job_child_vertica", true);
			child.getConstraints().addForeignKeyConstraint("fk_sqlapp_job_child_vertica",
					new Column[] { child.getColumns().get("parent_id") },
					new Column[] { parent.getColumns().get("id") });
			child.getRows().add(row -> {
				row.put("id", 10); row.put("parent_id", 1); row.put("txt", "child");
			});

			BulkMigrationJobAssertions.assertDependencyOrderAndAggregatedStatus(
					connection, parent, child,
					new FileBulkMigrationCheckpointStore(checkpointDirectory),
					BulkMigrationCheckpointMode.FILE);
			try (var resultSet = statement.executeQuery(
					"SELECT COUNT(*) FROM sqlapp_bulk_job_child_vertica c "
					+ "JOIN sqlapp_bulk_job_parent_vertica p ON p.id = c.parent_id")) {
				resultSet.next();
				assertEquals(1, resultSet.getInt(1));
			}
		}
	}

	@Test
	void databaseCheckpointRejectsTransactionBreakingStaging(
			@TempDir final Path checkpointDirectory) throws Exception {
		try (Connection connection = createConnection(); var statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS sqlapp_chunk_migration_vertica");
			statement.execute("CREATE TABLE sqlapp_chunk_migration_vertica "
					+ "(code VARCHAR(20) PRIMARY KEY, name VARCHAR(100))");
			final Table table = new Table("sqlapp_chunk_migration_vertica");
			final Column code = new Column("code").setDataType(DataType.VARCHAR).setLength(20);
			table.getColumns().add(code);
			table.getColumns().add(new Column("name").setDataType(DataType.VARCHAR).setLength(100));
			table.setPrimaryKey("pk_sqlapp_chunk_migration_vertica", code);
			BulkMigrationTransactionAssertions.assertDatabaseCheckpointRejected(connection,
					table, "code", "name", "SELECT COUNT(*) FROM sqlapp_chunk_migration_vertica");
			BulkMigrationTransactionAssertions.assertDatabaseCheckpointInsertRejected(connection,
					table, "code", "name", "SELECT COUNT(*) FROM sqlapp_chunk_migration_vertica");
			BulkMigrationTransactionAssertions.assertFileCheckpointCompletes(connection,
					table, "code", "name", "SELECT COUNT(*) FROM sqlapp_chunk_migration_vertica",
					checkpointDirectory);
		}
	}

	@Test
	void repairsJdbcKeysetMismatchIntoADifferentTargetAndReverifies() throws Exception {
		try (Connection sourceConnection = createConnection();
				Connection targetConnection = createConnection();
				var statement = targetConnection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS sqlapp_bulk_repair_source_vertica");
			statement.execute("DROP TABLE IF EXISTS sqlapp_bulk_repair_target_vertica");
			statement.execute("CREATE TABLE sqlapp_bulk_repair_source_vertica "
					+ "(id INT PRIMARY KEY, txt VARCHAR(100))");
			statement.execute("CREATE TABLE sqlapp_bulk_repair_target_vertica "
					+ "(id INT PRIMARY KEY, txt VARCHAR(100))");
			statement.execute("INSERT INTO sqlapp_bulk_repair_source_vertica VALUES "
					+ "(1, 'one'), (2, 'two'), (3, 'three'), (4, 'four')");
			statement.execute("INSERT INTO sqlapp_bulk_repair_target_vertica VALUES "
					+ "(1, 'one'), (2, 'wrong'), (3, 'three'), (4, 'four')");
			BulkMigrationRepairAssertions.assertDifferentTargetRepair(sourceConnection,
					targetConnection, jobTable("sqlapp_bulk_repair_source_vertica", false),
					jobTable("sqlapp_bulk_repair_target_vertica", false), List.of("id", "txt"),
					false);
		}
	}

	@Test
	void copiesRowsAndPreservesCsvSemantics() throws Exception {
		try (Connection connection = createConnection();
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE sqlapp_bulk_vertica (id IDENTITY, name VARCHAR(200), nullable_value VARCHAR(20), empty_value VARCHAR(20), amount NUMERIC(12,2))");
			final Table table = new Table("sqlapp_bulk_vertica");
			table.getColumns().add(new Column("id").setDataType(DataType.BIGINT)
					.setIdentity(true));
			table.getColumns().add(new Column("name").setDataType(DataType.VARCHAR));
			table.getColumns().add(new Column("nullable_value").setDataType(DataType.VARCHAR));
			table.getColumns().add(new Column("empty_value").setDataType(DataType.VARCHAR));
			table.getColumns().add(new Column("amount").setDataType(DataType.DECIMAL)
					.setLength(12).setScale(2));
			table.getRows().add(row -> {
				row.put("name", "山田,\"太郎\"\nline");
				row.put("nullable_value", null);
				row.put("empty_value", "");
				row.put("amount", new BigDecimal("123.45"));
			});

			assertEquals(1, BulkInsertResolver.execute(connection, table,
					BulkOption.defaults()));
			try (var resultSet = statement.executeQuery(
					"SELECT id, name, nullable_value, empty_value, amount FROM sqlapp_bulk_vertica")) {
				resultSet.next();
				assertEquals(1L, resultSet.getLong("id"));
				assertEquals("山田,\"太郎\"\nline", resultSet.getString("name"));
				assertNull(resultSet.getString("nullable_value"));
				assertEquals("", resultSet.getString("empty_value"));
				assertEquals(new BigDecimal("123.45"), resultSet.getBigDecimal("amount"));
			}
		}
	}

	@Test
	void upsertsThroughCopyStagingAndMerge() throws Exception {
		try(Connection connection=createConnection();var statement=connection.createStatement()){
			statement.execute("DROP TABLE IF EXISTS sqlapp_upsert_vertica");
			statement.execute("CREATE TABLE sqlapp_upsert_vertica (id IDENTITY UNIQUE, "
					+ "code VARCHAR(20) PRIMARY KEY, name VARCHAR(200), nullable_value VARCHAR(20), "
					+ "empty_value VARCHAR(20), amount NUMERIC(12,2))");
			statement.execute("INSERT INTO sqlapp_upsert_vertica(code,name,amount) VALUES('A','old',1.00)");
			final Table table=upsertTable();
			table.getRows().add(r->{r.put("code","A");r.put("name","更新後\nline");
				r.put("nullable_value",null);r.put("empty_value","");r.put("amount",new BigDecimal("12.34"));});
			table.getRows().add(r->{r.put("code","B");r.put("name",null);r.put("empty_value","");});
			table.getRows().add(r->{r.put("code","C");r.put("name","");r.put("amount",new BigDecimal("0.00"));});
			assertEquals(3,BulkUpsertResolver.execute(connection,table,BulkUpsertOption.defaults()));
			try(var rs=statement.executeQuery("SELECT id,code,name,nullable_value,empty_value,amount "
					+ "FROM sqlapp_upsert_vertica ORDER BY code")){
				rs.next();assertEquals(1L,rs.getLong("id"));assertEquals("更新後\nline",rs.getString("name"));
				assertNull(rs.getString("nullable_value"));assertEquals("",rs.getString("empty_value"));
				assertEquals(new BigDecimal("12.34"),rs.getBigDecimal("amount"));
				rs.next();assertEquals("B",rs.getString("code"));assertNull(rs.getString("name"));assertEquals("",rs.getString("empty_value"));
				rs.next();assertEquals("C",rs.getString("code"));assertEquals("",rs.getString("name"));
			}
		}
	}

	private static Table upsertTable(){final Table t=new Table("sqlapp_upsert_vertica");
		final Column id=new Column("id").setDataType(DataType.BIGINT).setIdentity(true);
		final Column code=new Column("code").setDataType(DataType.VARCHAR).setLength(20);
		t.getColumns().add(id);t.getColumns().add(code);
		t.getColumns().add(new Column("name").setDataType(DataType.VARCHAR).setLength(200));
		t.getColumns().add(new Column("nullable_value").setDataType(DataType.VARCHAR).setLength(20));
		t.getColumns().add(new Column("empty_value").setDataType(DataType.VARCHAR).setLength(20));
		t.getColumns().add(new Column("amount").setDataType(DataType.DECIMAL).setLength(12).setScale(2));
		t.setPrimaryKey("pk_sqlapp_upsert_vertica",code);return t;}

	private static Table jobTable(final String name, final boolean child) {
		final Table table = new Table(name);
		final Column id = new Column("id").setDataType(DataType.INT);
		table.getColumns().add(id);
		if (child) {
			table.getColumns().add(new Column("parent_id").setDataType(DataType.INT));
		}
		table.getColumns().add(new Column("txt").setDataType(DataType.VARCHAR).setLength(100));
		table.setPrimaryKey("pk_" + name, id);
		return table;
	}

	private static Connection createConnection() throws Exception {
		return DriverManager.getConnection("jdbc:vertica://localhost:"
				+ VERTICA.getMappedPort(5433) + "/VMart", "dbadmin", "");
	}
}
