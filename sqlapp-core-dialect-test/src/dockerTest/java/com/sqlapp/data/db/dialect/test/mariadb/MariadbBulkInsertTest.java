/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.mariadb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mariadb.MariaDBContainer;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.db.dialect.test.BulkMigrationJobAssertions;
import com.sqlapp.data.db.dialect.test.BulkMigrationTransactionAssertions;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;
import com.sqlapp.jdbc.bulk.BulkMigrationRepairExecutor;
import com.sqlapp.jdbc.bulk.BulkMigrationRepairOption;
import com.sqlapp.jdbc.bulk.BulkMigrationVerifier;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationKeysetSource;

/** Exercises MariaDB Connector/J LOAD DATA LOCAL INFILE against MariaDB 11.8. */
class MariadbBulkInsertTest {
	private static final MariaDBContainer MARIADB = ReusableTestcontainers
			.configure(new MariaDBContainer("mariadb:11.8")
					.withCommand("--local-infile=1"));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(MARIADB);
	}

	@Test
	void fencesJdbcJobLeaseOwnersAcrossConnections() throws Exception {
		try (Connection first = MARIADB.createConnection("?allowLocalInfile=true");
				Connection second = MARIADB.createConnection("?allowLocalInfile=true")) {
			BulkMigrationJobAssertions.assertJdbcLeaseOwnerFencing(first, second);
		}
	}

	@Test
	void migratesParentBeforeChildAndAggregatesJdbcCheckpointStatus() throws Exception {
		try (Connection connection = MARIADB.createConnection("?allowLocalInfile=true");
				var statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS sqlapp_bulk_job_child_mariadb");
			statement.execute("DROP TABLE IF EXISTS sqlapp_bulk_job_parent_mariadb");
			statement.execute("CREATE TABLE sqlapp_bulk_job_parent_mariadb "
					+ "(id INT PRIMARY KEY, txt VARCHAR(100)) ENGINE=InnoDB");
			statement.execute("CREATE TABLE sqlapp_bulk_job_child_mariadb "
					+ "(id INT PRIMARY KEY, parent_id INT NOT NULL, txt VARCHAR(100), "
					+ "FOREIGN KEY (parent_id) REFERENCES sqlapp_bulk_job_parent_mariadb(id)) "
					+ "ENGINE=InnoDB");
			final Table parent = jobTable("sqlapp_bulk_job_parent_mariadb", false);
			parent.getRows().add(row -> { row.put("id", 1); row.put("txt", "parent"); });
			final Table child = jobTable("sqlapp_bulk_job_child_mariadb", true);
			child.getConstraints().addForeignKeyConstraint("fk_sqlapp_job_child_mariadb",
					new Column[] { child.getColumns().get("parent_id") },
					new Column[] { parent.getColumns().get("id") });
			child.getRows().add(row -> {
				row.put("id", 10); row.put("parent_id", 1); row.put("txt", "child");
			});

			BulkMigrationJobAssertions.assertDependencyOrderAndAggregatedStatus(
					connection, parent, child);
			try (var resultSet = statement.executeQuery(
					"SELECT COUNT(*) FROM sqlapp_bulk_job_child_mariadb c "
					+ "JOIN sqlapp_bulk_job_parent_mariadb p ON p.id = c.parent_id")) {
				resultSet.next();
				assertEquals(1, resultSet.getInt(1));
			}
		}
	}

	@Test
	void databaseCheckpointRollsBackWithTheChunk() throws Exception {
		try (Connection connection = MARIADB.createConnection("?allowLocalInfile=true");
				var statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS sqlapp_chunk_migration_mariadb");
			statement.execute("CREATE TABLE sqlapp_chunk_migration_mariadb "
					+ "(code VARCHAR(20) PRIMARY KEY, name VARCHAR(100)) ENGINE=InnoDB");
			final Table table = new Table("sqlapp_chunk_migration_mariadb");
			final Column code = new Column("code").setDataType(DataType.VARCHAR).setLength(20);
			table.getColumns().add(code);
			table.getColumns().add(new Column("name").setDataType(DataType.VARCHAR).setLength(100));
			table.setPrimaryKey("pk_sqlapp_chunk_migration_mariadb", code);
			BulkMigrationTransactionAssertions.assertDatabaseCheckpointAtomic(connection,
					table, "code", "name", "SELECT COUNT(*) FROM sqlapp_chunk_migration_mariadb");
		}
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(MARIADB);
	}

	@Test
	void loadsRowsAndPreservesDelimitedValues() throws Exception {
		try (Connection connection = MARIADB.createConnection("?allowLocalInfile=true");
				var statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS sqlapp_bulk_mariadb");
			statement.execute("CREATE TABLE sqlapp_bulk_mariadb ("
					+ "id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(200), "
					+ "nullable_value VARCHAR(20), empty_value VARCHAR(20), "
					+ "payload VARBINARY(20))");
			final Table table = new Table("sqlapp_bulk_mariadb");
			table.getColumns().add(new Column("id").setDataType(DataType.BIGINT)
					.setIdentity(true));
			table.getColumns().add(new Column("name").setDataType(DataType.VARCHAR));
			table.getColumns().add(new Column("nullable_value").setDataType(DataType.VARCHAR));
			table.getColumns().add(new Column("empty_value").setDataType(DataType.VARCHAR));
			table.getColumns().add(new Column("payload").setDataType(DataType.VARBINARY));
			table.getRows().add(row -> {
				row.put("name", "山田\nline\\path");
				row.put("nullable_value", null);
				row.put("empty_value", "");
				row.put("payload", new byte[] { 0, (byte) 0xff });
			});

			assertEquals(1, BulkInsertResolver.execute(connection, table,
					BulkOption.defaults()));
			try (var resultSet = statement.executeQuery(
					"SELECT id, name, nullable_value, empty_value, payload "
							+ "FROM sqlapp_bulk_mariadb")) {
				resultSet.next();
				assertEquals(1L, resultSet.getLong("id"));
				assertEquals("山田\nline\\path", resultSet.getString("name"));
				assertNull(resultSet.getString("nullable_value"));
				assertEquals("", resultSet.getString("empty_value"));
				assertArrayEquals(new byte[] { 0, (byte) 0xff }, resultSet.getBytes("payload"));
			}
		}
	}

	@Test
	void upsertsThroughLoadDataStaging() throws Exception {
		try(Connection connection=MARIADB.createConnection("?allowLocalInfile=true");var statement=connection.createStatement()){
			statement.execute("DROP TABLE IF EXISTS sqlapp_upsert_mariadb");
			statement.execute("CREATE TABLE sqlapp_upsert_mariadb (id BIGINT AUTO_INCREMENT UNIQUE, code VARCHAR(20) PRIMARY KEY, name VARCHAR(200), payload VARBINARY(20))");
			statement.execute("INSERT INTO sqlapp_upsert_mariadb(code,name) VALUES('A','old')");
			final Table table=upsertTable();
			table.getRows().add(r->{r.put("code","A");r.put("name","更新後\nline");r.put("payload",new byte[]{0,(byte)0xff});});
			table.getRows().add(r->{r.put("code","B");r.put("name",null);});
			table.getRows().add(r->{r.put("code","C");r.put("name","");r.put("payload",new byte[]{2});});
			BulkUpsertResolver.execute(connection,table,BulkUpsertOption.defaults());
			try(var rs=statement.executeQuery("SELECT id,code,name,payload FROM sqlapp_upsert_mariadb ORDER BY code")){
				rs.next();assertEquals(1L,rs.getLong("id"));assertEquals("更新後\nline",rs.getString("name"));assertArrayEquals(new byte[]{0,(byte)0xff},rs.getBytes("payload"));
				rs.next();assertEquals("B",rs.getString("code"));assertNull(rs.getString("name"));
				rs.next();assertEquals("C",rs.getString("code"));assertEquals("",rs.getString("name"));assertArrayEquals(new byte[]{2},rs.getBytes("payload"));
			}
		}
	}

	@Test
	void repairsJdbcKeysetMismatchIntoADifferentTargetAndReverifies() throws Exception {
		try (Connection connection = MARIADB.createConnection("?allowLocalInfile=true");
				var statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS sqlapp_bulk_repair_source_mariadb");
			statement.execute("DROP TABLE IF EXISTS sqlapp_bulk_repair_target_mariadb");
			statement.execute("CREATE TABLE sqlapp_bulk_repair_source_mariadb "
					+ "(id INT PRIMARY KEY, txt VARCHAR(100)) ENGINE=InnoDB");
			statement.execute("CREATE TABLE sqlapp_bulk_repair_target_mariadb "
					+ "(id INT PRIMARY KEY, txt VARCHAR(100)) ENGINE=InnoDB");
			statement.executeUpdate("INSERT INTO sqlapp_bulk_repair_source_mariadb VALUES "
					+ "(1,'value-1'),(2,'value-2'),(3,'value-3'),(4,'value-4')");
			statement.executeUpdate("INSERT INTO sqlapp_bulk_repair_target_mariadb VALUES "
					+ "(1,'value-1'),(2,'wrong'),(3,'value-3'),(4,'value-4')");
			final var expected = new JdbcBulkMigrationKeysetSource(connection,
					jobTable("sqlapp_bulk_repair_source_mariadb", false));
			final var actual = new JdbcBulkMigrationKeysetSource(connection,
					jobTable("sqlapp_bulk_repair_target_mariadb", false));
			final var verification = BulkMigrationVerifier.verify(expected, actual,
					List.of("id", "txt"), 1);

			final var repair = BulkMigrationRepairExecutor.execute(connection, expected,
					jobTable("sqlapp_bulk_repair_target_mariadb", false), verification,
					BulkMigrationRepairOption.defaults());

			assertEquals(1, repair.getReplayedChunks());
			assertEquals(1, repair.getReplayedRows());
			assertTrue(BulkMigrationVerifier.verify(expected,
					new JdbcBulkMigrationKeysetSource(connection,
							jobTable("sqlapp_bulk_repair_target_mariadb", false)),
					List.of("id", "txt"), 1).isMatch());
		}
	}

	private static Table upsertTable(){final Table t=new Table("sqlapp_upsert_mariadb");
		final Column id=new Column("id").setDataType(DataType.BIGINT).setIdentity(true);final Column code=new Column("code").setDataType(DataType.VARCHAR).setLength(20);
		t.getColumns().add(id);t.getColumns().add(code);t.getColumns().add(new Column("name").setDataType(DataType.VARCHAR).setLength(200));t.getColumns().add(new Column("payload").setDataType(DataType.VARBINARY).setLength(20));
		t.setPrimaryKey("pk_sqlapp_upsert_mariadb",code);return t;}

	private static Table jobTable(final String name, final boolean child) {
		final Table table = new Table(name);
		final Column id = new Column("id").setDataType(DataType.INT).setNotNull(true);
		table.getColumns().add(id);
		if (child) {
			table.getColumns().add(new Column("parent_id").setDataType(DataType.INT)
					.setNotNull(true));
		}
		table.getColumns().add(new Column("txt").setDataType(DataType.VARCHAR).setLength(100));
		table.setPrimaryKey("pk_" + name, id);
		return table;
	}
}
