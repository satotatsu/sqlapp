/* Copyright (C) 2026 Tatsuo Satoh */
package com.sqlapp.data.db.command.export;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.rowiterator.DataFormat;
import com.sqlapp.data.schemas.rowiterator.FileRowIteratorFactory;
import com.sqlapp.data.schemas.rowiterator.ParquetTableIO;

class ParquetCommandsTest {
	@TempDir Path directory;

	@Test void convertsJsonToParquetAndBack() throws Exception {
		Path json = Files.writeString(directory.resolve("items.json"), "[{\"ID\":42,\"TEXT\":\"日本語\"}]");
		var command = new ConvertDataCommand();
		command.setFiles(List.of(json.toFile())); command.setOutputFileType("PARQUET"); command.run();
		Path parquet = directory.resolve("items.parquet");
		assertTrue(Files.exists(parquet));
		Files.delete(json);
		command = new ConvertDataCommand(); command.setFiles(List.of(parquet.toFile()));
		command.setOutputFileType(DataFormat.JSON); command.run();
		String output = Files.readString(json);
		assertTrue(output.contains("42")); assertTrue(output.contains("日本語"));
		Files.delete(parquet);
	}

	@Test void importsTypedParquetIntoEmbeddedDatabase() throws Exception {
		Table source = table();
		var row = source.newRow(); row.put("ID", 42); row.put("TEXT", "日本語"); source.getRows().add(row);
		Path parquet = directory.resolve("ITEMS.parquet"); ParquetTableIO.write(parquet, source);
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER, TEXT VARCHAR(100))");
			connection.setAutoCommit(false);
			var command = new ImportDataCommand(); command.setSqlType(SqlType.INSERT);
			assertEquals(1, command.executeImport(connection, DialectResolver.getInstance().getDialect(connection),
					table(), List.of(parquet.toFile())));
			try (var result = statement.executeQuery("SELECT ID, TEXT FROM ITEMS")) {
				assertTrue(result.next()); assertEquals(42, result.getInt(1)); assertEquals("日本語", result.getString(2));
				assertFalse(result.next());
			}
			connection.rollback();
		}
		Files.delete(parquet);
	}

	@Test void rejectsLateColumnsWithoutReplacingOutputOrRemovingInput() throws Exception {
		Path json = Files.writeString(directory.resolve("items.jsonl"), "{\"ID\":1}\n{\"ID\":2,\"LATE\":3}");
		Path parquet = Files.writeString(directory.resolve("items.parquet"), "previous");
		var command = new ConvertDataCommand(); command.setFiles(List.of(json.toFile()));
		command.setOutputFileType("PARQUET"); command.setRemoveOriginalFile(true);
		assertThrows(RuntimeException.class, command::run);
		assertEquals("previous", Files.readString(parquet));
		assertTrue(Files.exists(json));
		try (var files = Files.list(directory)) { assertEquals(2, files.count()); }
	}

	@Test void rejectsFractionalValueBeforeTextReaderCanTruncateIt() throws Exception {
		Path json = Files.writeString(directory.resolve("items.jsonl"), "{\"ID\":1}\n{\"ID\":1.5}");
		var command = new ConvertDataCommand(); command.setFiles(List.of(json.toFile()));
		command.setOutputFileType("PARQUET");
		assertThrows(RuntimeException.class, command::run);
		assertFalse(Files.exists(directory.resolve("items.parquet")));
		Files.delete(json);
	}

	@Test void closesParquetInputWhenJsonOutputFails() throws Exception {
		Table source = table(); var row = source.newRow(); row.put("ID", 42); source.getRows().add(row);
		Path parquet = directory.resolve("items.parquet"); ParquetTableIO.write(parquet, source);
		Path json = Files.writeString(directory.resolve("items.json"), "previous");
		var command = new ConvertDataCommand(); command.setFiles(List.of(parquet.toFile()));
		command.setOutputFileType(DataFormat.JSON); command.setRemoveOriginalFile(true);
		command.setJsonConverter(new com.sqlapp.util.JsonConverter() {
			@Override public String toJsonString(Object value) { throw new IllegalStateException("output failed"); }
		});
		assertThrows(RuntimeException.class, command::run);
		assertEquals("previous", Files.readString(json));
		assertTrue(Files.exists(parquet)); Files.delete(parquet);
		try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
	}

	@Test void exportsEmbeddedDatabaseToParquet() throws Exception {
		try (var dataSource = new com.zaxxer.hikari.HikariDataSource()) {
			dataSource.setJdbcUrl("jdbc:hsqldb:mem:" + UUID.randomUUID());
			dataSource.setUsername("SA"); dataSource.setPassword("");
			try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE ITEMS (ID INTEGER, TEXT VARCHAR(100))");
				statement.execute("INSERT INTO ITEMS VALUES (42, '日本語')");
			}
			var command = new ExportDataCommand(); command.setDataSource(dataSource);
			command.setCloseDataSource(false); command.setUseSchemaNameDirectory(false);
			command.setIncludeSchemas("PUBLIC"); command.setIncludeTables("ITEMS"); command.setOnlyCurrentSchema(false);
			command.setOutputDirectory(directory.toFile()); command.setOutputFileType("PARQUET"); command.run();
			Path file = directory.resolve("ITEMS.parquet");
			Table result = new Table("ITEMS"); result.setRowIteratorHandler(FileRowIteratorFactory.create(file));
			var iterator = result.getRows().iterator(); assertTrue(iterator.hasNext());
			assertEquals(42, ((Number) iterator.next().get("ID")).intValue()); assertFalse(iterator.hasNext());
			Files.delete(file);
		}
	}

	private Table table() {
		Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		table.getColumns().add(new Column("TEXT").setDataType(DataType.VARCHAR).setLength(100));
		return table;
	}
}
