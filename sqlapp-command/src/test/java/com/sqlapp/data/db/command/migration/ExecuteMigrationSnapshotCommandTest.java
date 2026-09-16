/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.MigrationSnapshotExecutionResult;

class ExecuteMigrationSnapshotCommandTest {
	@TempDir Path directory;

	@Test
	void executesYamlSnapshotEndToEnd() throws Exception {
		final JDBCDataSource source = dataSource("snapshot_command_source");
		final JDBCDataSource target = dataSource("snapshot_command_target");
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE CUSTOMER(ID INTEGER, NAME VARCHAR(20))");
			statement.execute("INSERT INTO CUSTOMER VALUES(1,'same'),(2,'new'),(4,'added')");
		}
		try (var connection = target.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE CUSTOMER_HISTORY(ID INTEGER, NAME VARCHAR(20), VALID_FROM TIMESTAMP NOT NULL, VALID_TO TIMESTAMP, IS_CURRENT BOOLEAN NOT NULL)");
			statement.execute("INSERT INTO CUSTOMER_HISTORY VALUES(1,'same',TIMESTAMP '2026-01-01 00:00:00',NULL,TRUE),(2,'old',TIMESTAMP '2026-01-01 00:00:00',NULL,TRUE),(3,'removed',TIMESTAMP '2026-01-01 00:00:00',NULL,TRUE)");
		}
		final Path yaml = configuration();
		final var command = new ExecuteMigrationSnapshotCommand();
		command.setSourceDataSource(source);
		command.setDataSource(target);
		command.setConfigurationFile(yaml.toFile());
		command.run();
		assertEquals(new MigrationSnapshotExecutionResult(2, 2, 1), command.getResult());
		try (var connection = target.getConnection(); var statement = connection.createStatement();
				var rs = statement.executeQuery("SELECT COUNT(*) FROM CUSTOMER_HISTORY WHERE IS_CURRENT")) {
			rs.next();
			assertEquals(3, rs.getInt(1));
		}
	}

	private Path configuration() throws Exception {
		final Schema schema = new Schema("PUBLIC");
		final Table source = new Table("CUSTOMER");
		source.getColumns().add(new Column("ID").setDataType(DataType.INT));
		source.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR));
		final Table target = new Table("CUSTOMER_HISTORY");
		target.getColumns().add(new Column("ID").setDataType(DataType.INT));
		target.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR));
		target.getColumns().add(new Column("VALID_FROM").setDataType(DataType.TIMESTAMP));
		target.getColumns().add(new Column("VALID_TO").setDataType(DataType.TIMESTAMP));
		target.getColumns().add(new Column("IS_CURRENT").setDataType(DataType.BOOLEAN));
		schema.getTables().add(source); schema.getTables().add(target);
		schema.writeXml(directory.resolve("schema.xml").toFile());
		final Path yaml = directory.resolve("snapshot.yaml");
		Files.writeString(yaml, "schemaFile: schema.xml\nsourceTable: CUSTOMER\ntargetTable: CUSTOMER_HISTORY\n"
				+ "keyColumns: [ID]\ntrackedColumns: [NAME]\nvalidFromColumn: VALID_FROM\n"
				+ "validToColumn: VALID_TO\ncurrentColumn: IS_CURRENT\nexpireMissingRows: true\n"
				+ "effectiveAt: 2026-09-16T00:00:00Z\nfetchSize: 2\nbatchSize: 2\n");
		return yaml;
	}

	private static JDBCDataSource dataSource(final String name) {
		final JDBCDataSource value = new JDBCDataSource();
		value.setUrl("jdbc:hsqldb:mem:" + name);
		value.setUser("SA");
		value.setPassword("");
		return value;
	}
}
