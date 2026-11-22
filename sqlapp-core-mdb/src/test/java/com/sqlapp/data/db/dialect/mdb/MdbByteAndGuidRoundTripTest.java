/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

import io.github.spannm.jackcess.ColumnBuilder;
import io.github.spannm.jackcess.Database;
import io.github.spannm.jackcess.DatabaseBuilder;
import io.github.spannm.jackcess.IndexBuilder;
import io.github.spannm.jackcess.TableBuilder;

/** Access Byte is unsigned and UCanAccess always creates GUID as AutoNumber. */
class MdbByteAndGuidRoundTripTest {

	private static final List<Integer> BYTES = List.of(0, 127, 128, 255);

	@TempDir
	Path directory;

	@ParameterizedTest
	@EnumSource(value = Database.FileFormat.class, names = { "V2000", "V2010" })
	void accessByteKeepsZeroTo255OnEveryPath(final Database.FileFormat format) throws Exception {
		final Path file = directory.resolve("byte" + format.getFileExtension());
		try (Database database = DatabaseBuilder.create(format, file.toFile())) {
			final var table = new TableBuilder("B")
					.addColumn(new ColumnBuilder("ID", io.github.spannm.jackcess.DataType.LONG))
					.addColumn(new ColumnBuilder("V", io.github.spannm.jackcess.DataType.BYTE))
					.addIndex(new IndexBuilder("PK_B").withColumns("ID").withPrimaryKey()).toTable(database);
			for (int i = 0; i < BYTES.size(); i++) {
				table.addRow(i, BYTES.get(i));
			}
		}
		// File loader: type and row values.
		final Table loaded = MdbFileLoader.loadTable(file, "B");
		final Column value = loaded.getColumns().get("V");
		assertEquals(DataType.UTINYINT, value.getDataType());
		final List<Integer> fileValues = new ArrayList<>();
		for (final Row row : loaded.getRows()) {
			fileValues.add(((Number) row.get("V")).intValue());
		}
		assertEquals(BYTES, fileValues);
		final var converter = Converters.getDefault().getConverter(value.getDataType().getDefaultClass());
		for (final Integer expected : BYTES) {
			assertEquals(expected.intValue(), ((Number) converter.convertObject(expected)).intValue());
		}
		// JDBC metadata and values.
		try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + file.toAbsolutePath())) {
			final var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setObjectName("B");
			assertEquals(DataType.UTINYINT,
					reader.getAllFull(connection).getFirst().getColumns().get("V").getDataType());
			final List<Integer> jdbcValues = new ArrayList<>();
			try (var statement = connection.createStatement();
					var resultSet = statement.executeQuery("SELECT V FROM B ORDER BY ID")) {
				while (resultSet.next()) {
					jdbcValues.add(resultSet.getInt(1));
				}
			}
			assertEquals(BYTES, jdbcValues);
		}
		// Jackcess rebuild keeps the native Byte type and values.
		final Schema schema = MdbFileLoader.loadSchema(file);
		final Path rebuilt = directory.resolve("rebuilt" + format.getFileExtension());
		MdbJackcessSupport.rebuild(file, rebuilt, schema);
		try (Database database = new DatabaseBuilder().withPath(rebuilt).withReadOnly(true).open()) {
			assertEquals(io.github.spannm.jackcess.DataType.BYTE, database.getTable("B").getColumn("V").getType());
		}
		final List<Integer> rebuiltValues = new ArrayList<>();
		for (final Row row : MdbFileLoader.loadTable(rebuilt, "B").getRows()) {
			rebuiltValues.add(((Number) row.get("V")).intValue());
		}
		assertEquals(BYTES, rebuiltValues);
	}

	@ParameterizedTest
	@EnumSource(value = Database.FileFormat.class, names = { "V2000", "V2010" })
	void sqlDdlCreatesByteForUnsignedAndShortForSignedTinyInt(final Database.FileFormat format) throws Exception {
		final Path file = directory.resolve("ddl" + format.getFileExtension());
		DatabaseBuilder.create(format, file.toFile()).close();
		final Table table = new Table("D");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("U").setDataType(DataType.UTINYINT));
		table.getColumns().add(new Column("S").setDataType(DataType.TINYINT));
		table.setPrimaryKey("PK_D", table.getColumns().get("ID"));
		try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + file.toAbsolutePath())) {
			for (final var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(table,
					SqlType.CREATE)) {
				try (var statement = connection.createStatement()) {
					statement.execute(operation.getSqlText());
				}
			}
			try (var statement = connection.createStatement()) {
				statement.executeUpdate("INSERT INTO D (ID, U, S) VALUES (1, 255, -128)");
			}
		}
		try (Database database = new DatabaseBuilder().withPath(file).withReadOnly(true).open()) {
			assertEquals(io.github.spannm.jackcess.DataType.BYTE, database.getTable("D").getColumn("U").getType());
			assertEquals(io.github.spannm.jackcess.DataType.INT, database.getTable("D").getColumn("S").getType());
		}
		final var rows = MdbFileLoader.loadTable(file, "D").getRows().iterator();
		assertTrue(rows.hasNext());
		final Row row = rows.next();
		assertEquals(255, ((Number) row.get("U")).intValue());
		assertEquals(-128, ((Number) row.get("S")).intValue());
	}

	@ParameterizedTest
	@EnumSource(value = Database.FileFormat.class, names = { "V2000", "V2010" })
	void autoNumberGuidSurvivesSqlRecreation(final Database.FileFormat format) throws Exception {
		final Path source = directory.resolve("guid" + format.getFileExtension());
		try (Database database = DatabaseBuilder.create(format, source.toFile())) {
			new TableBuilder("G")
					.addColumn(new ColumnBuilder("RID", io.github.spannm.jackcess.DataType.GUID).withAutoNumber(true))
					.addColumn(new ColumnBuilder("TXT", io.github.spannm.jackcess.DataType.TEXT).withLengthInUnits(10))
					.toTable(database);
		}
		final Table model = MdbFileLoader.loadTable(source, "G");
		assertTrue(model.getColumns().get("RID").isIdentity());
		final Path target = directory.resolve("guid-sql" + format.getFileExtension());
		DatabaseBuilder.create(format, target.toFile()).close();
		try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + target.toAbsolutePath())) {
			for (final var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(model,
					SqlType.CREATE)) {
				try (var statement = connection.createStatement()) {
					statement.execute(operation.getSqlText());
				}
			}
			final var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setObjectName("G");
			final Column rid = reader.getAllFull(connection).getFirst().getColumns().get("RID");
			assertEquals(DataType.UUID, rid.getDataType());
			assertTrue(rid.isIdentity());
		}
		try (Database database = new DatabaseBuilder().withPath(target).withReadOnly(true).open()) {
			final var rid = database.getTable("G").getColumn("RID");
			assertEquals(io.github.spannm.jackcess.DataType.GUID, rid.getType());
			assertTrue(rid.isAutoNumber());
		}
	}

	@ParameterizedTest
	@EnumSource(value = Database.FileFormat.class, names = { "V2000", "V2010" })
	void plainGuidIsRejectedBySqlAndKeptByJackcess(final Database.FileFormat format) throws Exception {
		final Path source = directory.resolve("plain" + format.getFileExtension());
		try (Database database = DatabaseBuilder.create(format, source.toFile())) {
			new TableBuilder("P").addColumn(new ColumnBuilder("ID", io.github.spannm.jackcess.DataType.LONG))
					.addColumn(new ColumnBuilder("TOKEN", io.github.spannm.jackcess.DataType.GUID))
					.addIndex(new IndexBuilder("PK_P").withColumns("ID").withPrimaryKey()).toTable(database)
					.addRow(1, "{12345678-1234-1234-1234-1234567890AB}");
		}
		final Schema schema = MdbFileLoader.loadSchema(source);
		final Table model = schema.getTables().get("P");
		assertFalse(model.getColumns().get("TOKEN").isIdentity());
		assertThrows(UnsupportedOperationException.class,
				() -> DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(model, SqlType.CREATE));

		final Path rebuilt = directory.resolve("plain-rebuilt" + format.getFileExtension());
		MdbJackcessSupport.rebuild(source, rebuilt, schema);
		try (Database database = new DatabaseBuilder().withPath(rebuilt).withReadOnly(true).open()) {
			final var token = database.getTable("P").getColumn("TOKEN");
			assertEquals(io.github.spannm.jackcess.DataType.GUID, token.getType());
			assertFalse(token.isAutoNumber());
			assertEquals("{12345678-1234-1234-1234-1234567890AB}",
					database.getTable("P").iterator().next().get("TOKEN"));
		}
		try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + rebuilt.toAbsolutePath())) {
			final var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setObjectName("P");
			final Column token = reader.getAllFull(connection).getFirst().getColumns().get("TOKEN");
			assertEquals(DataType.UUID, token.getDataType());
			assertFalse(token.isIdentity());
		}
	}

	/** Expected Access type for every DataType registered by the Mdb dialect. */
	private static final java.util.Map<DataType, io.github.spannm.jackcess.DataType> REGISTERED = new java.util.EnumMap<>(
			java.util.Map.ofEntries(java.util.Map.entry(DataType.CHAR, io.github.spannm.jackcess.DataType.TEXT),
					java.util.Map.entry(DataType.VARCHAR, io.github.spannm.jackcess.DataType.TEXT),
					java.util.Map.entry(DataType.LONGVARCHAR, io.github.spannm.jackcess.DataType.MEMO),
					java.util.Map.entry(DataType.NCHAR, io.github.spannm.jackcess.DataType.TEXT),
					java.util.Map.entry(DataType.NVARCHAR, io.github.spannm.jackcess.DataType.TEXT),
					java.util.Map.entry(DataType.LONGNVARCHAR, io.github.spannm.jackcess.DataType.MEMO),
					java.util.Map.entry(DataType.NCLOB, io.github.spannm.jackcess.DataType.MEMO),
					java.util.Map.entry(DataType.VARBINARY, io.github.spannm.jackcess.DataType.BINARY),
					java.util.Map.entry(DataType.BLOB, io.github.spannm.jackcess.DataType.OLE),
					java.util.Map.entry(DataType.BOOLEAN, io.github.spannm.jackcess.DataType.BOOLEAN),
					java.util.Map.entry(DataType.UTINYINT, io.github.spannm.jackcess.DataType.BYTE),
					java.util.Map.entry(DataType.SMALLINT, io.github.spannm.jackcess.DataType.INT),
					java.util.Map.entry(DataType.INT, io.github.spannm.jackcess.DataType.LONG),
					java.util.Map.entry(DataType.BIGINT, io.github.spannm.jackcess.DataType.NUMERIC),
					java.util.Map.entry(DataType.SERIAL, io.github.spannm.jackcess.DataType.LONG),
					java.util.Map.entry(DataType.UUID, io.github.spannm.jackcess.DataType.GUID),
					java.util.Map.entry(DataType.REAL, io.github.spannm.jackcess.DataType.FLOAT),
					java.util.Map.entry(DataType.DOUBLE, io.github.spannm.jackcess.DataType.DOUBLE),
					java.util.Map.entry(DataType.DATETIME, io.github.spannm.jackcess.DataType.SHORT_DATE_TIME),
					java.util.Map.entry(DataType.TIME, io.github.spannm.jackcess.DataType.SHORT_DATE_TIME),
					java.util.Map.entry(DataType.MONEY, io.github.spannm.jackcess.DataType.MONEY),
					java.util.Map.entry(DataType.DECIMAL, io.github.spannm.jackcess.DataType.NUMERIC),
					java.util.Map.entry(DataType.NUMERIC, io.github.spannm.jackcess.DataType.NUMERIC)));

	/** Unregistered types that must reach a safe Access type through surrogates. */
	private static final java.util.Map<DataType, io.github.spannm.jackcess.DataType> FALLBACK = new java.util.EnumMap<>(
			java.util.Map.of(DataType.TINYINT, io.github.spannm.jackcess.DataType.INT, DataType.DATE,
					io.github.spannm.jackcess.DataType.SHORT_DATE_TIME, DataType.TIMESTAMP,
					io.github.spannm.jackcess.DataType.SHORT_DATE_TIME));

	@Test
	void expectationsCoverEveryRegisteredType() {
		final var types = DialectHolder.defaultDialect.getDbDataTypes();
		final java.util.Set<DataType> registered = java.util.EnumSet.noneOf(DataType.class);
		for (final DataType type : DataType.values()) {
			if (types.getDbTypeStrict(type) != null || types.getDbTypeStrict(type, 1L) != null) {
				registered.add(type);
			}
		}
		// A new registration must add its expected Access type to REGISTERED.
		assertEquals(registered, REGISTERED.keySet());
		for (final DataType type : FALLBACK.keySet()) {
			assertFalse(registered.contains(type), type.toString());
		}
	}

	@ParameterizedTest
	@EnumSource(value = Database.FileFormat.class, names = { "V2000", "V2010" })
	void everyRegisteredTypeCreatesTheExpectedAccessType(final Database.FileFormat format) throws Exception {
		final Path file = directory.resolve("all" + format.getFileExtension());
		DatabaseBuilder.create(format, file.toFile()).close();
		final java.util.Map<DataType, io.github.spannm.jackcess.DataType> expected = new java.util.EnumMap<>(
				REGISTERED);
		expected.putAll(FALLBACK);
		try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + file.toAbsolutePath())) {
			for (final DataType type : expected.keySet()) {
				// One table per type: Access allows only one AutoNumber per table.
				final Table table = new Table("T_" + type.name());
				final Column column = new Column("C").setDataType(type);
				switch (type) {
				case CHAR, VARCHAR, NCHAR, NVARCHAR -> column.setLength(10L);
				case VARBINARY -> column.setLength(8L);
				case DECIMAL, NUMERIC -> column.setLength(10L).setScale(2);
				case UUID, SERIAL -> column.setIdentity(true);
				default -> {
				}
				}
				table.getColumns().add(column);
				for (final var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(table,
						SqlType.CREATE)) {
					try (var statement = connection.createStatement()) {
						statement.execute(operation.getSqlText());
					} catch (final java.sql.SQLException e) {
						throw new AssertionError(type + ": " + operation.getSqlText(), e);
					}
				}
			}
		}
		try (Database database = new DatabaseBuilder().withPath(file).withReadOnly(true).open()) {
			for (final var entry : expected.entrySet()) {
				final var column = database.getTable("T_" + entry.getKey().name()).getColumn("C");
				assertEquals(entry.getValue(), column.getType(), entry.getKey().toString());
			}
			assertTrue(database.getTable("T_UUID").getColumn("C").isAutoNumber());
			assertTrue(database.getTable("T_SERIAL").getColumn("C").isAutoNumber());
			for (final DataType type : new DataType[] { DataType.DECIMAL, DataType.NUMERIC }) {
				final var column = database.getTable("T_" + type.name()).getColumn("C");
				assertEquals(10, column.getPrecision(), type.toString());
				assertEquals(2, column.getScale(), type.toString());
			}
		}
	}
}
