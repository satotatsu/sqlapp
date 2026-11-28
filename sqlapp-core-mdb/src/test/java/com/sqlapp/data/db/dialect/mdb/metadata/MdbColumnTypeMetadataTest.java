/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.DriverManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.sqlapp.data.db.dialect.mdb.DialectHolder;
import com.sqlapp.data.schemas.Table;

import io.github.spannm.jackcess.ColumnBuilder;
import io.github.spannm.jackcess.DataType;
import io.github.spannm.jackcess.Database;
import io.github.spannm.jackcess.DatabaseBuilder;
import io.github.spannm.jackcess.IndexBuilder;
import io.github.spannm.jackcess.TableBuilder;

/**
 * JDBC metadata must report the physical Access type, not UCanAccess's mirror
 * type.
 */
class MdbColumnTypeMetadataTest {

	@TempDir
	Path directory;

	@ParameterizedTest
	@EnumSource(value = Database.FileFormat.class, names = { "V2000", "V2010" })
	void jdbcMetadataUsesPhysicalAccessTypes(final Database.FileFormat format) throws Exception {
		final Path path = directory.resolve("types" + format.getFileExtension());
		try (Database database = DatabaseBuilder.create(format, path.toFile())) {
			new TableBuilder("T").addColumn(new ColumnBuilder("ID", DataType.LONG).withAutoNumber(true))
					.addColumn(new ColumnBuilder("DEC", DataType.NUMERIC).withPrecision(23).withScale(7))
					.addColumn(new ColumnBuilder("CASH", DataType.MONEY))
					.addColumn(new ColumnBuilder("DBL", DataType.DOUBLE))
					.addColumn(new ColumnBuilder("TOKEN", DataType.GUID))
					.addColumn(new ColumnBuilder("BYTES", DataType.BINARY).withLength(8))
					.addIndex(new IndexBuilder("PK_T").withColumns("ID").withPrimaryKey()).toTable(database);
			new TableBuilder("R").addColumn(new ColumnBuilder("RID", DataType.GUID).withAutoNumber(true))
					.toTable(database);
		}
		try (var connection = DriverManager.getConnection("jdbc:ucanaccess://" + path.toAbsolutePath())) {
			final var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setObjectName("T");
			final Table table = reader.getAllFull(connection).getFirst();
			final var columns = table.getColumns();
			assertEquals(com.sqlapp.data.db.datatype.DataType.DECIMAL, columns.get("DEC").getDataType());
			assertEquals(23L, columns.get("DEC").getLength());
			assertEquals(7, columns.get("DEC").getScale());
			assertEquals(com.sqlapp.data.db.datatype.DataType.MONEY, columns.get("CASH").getDataType());
			assertEquals(com.sqlapp.data.db.datatype.DataType.DOUBLE, columns.get("DBL").getDataType());
			assertEquals(com.sqlapp.data.db.datatype.DataType.UUID, columns.get("TOKEN").getDataType());
			assertEquals(com.sqlapp.data.db.datatype.DataType.VARBINARY, columns.get("BYTES").getDataType());
			assertEquals(8L, columns.get("BYTES").getLength());
			assertTrue(columns.get("ID").isIdentity());

			reader.setObjectName("R");
			final var rid = reader.getAllFull(connection).getFirst().getColumns().get("RID");
			assertEquals(com.sqlapp.data.db.datatype.DataType.UUID, rid.getDataType());
			assertTrue(rid.isIdentity());
			final String sql = DialectHolder.defaultDialect.createSqlFactoryRegistry()
					.createSql(reader.getAllFull(connection).getFirst(), com.sqlapp.data.db.sql.SqlType.CREATE)
					.getFirst().getSqlText();
			assertTrue(sql.contains("RID GUID"), sql);
		}
	}

	@Test
	void calculatedColumnsAreEnrichedBeforeGeneratedColumnValidation() throws Exception {
		final Path path = directory.resolve("calculated.accdb");
		try (Database database = DatabaseBuilder.create(Database.FileFormat.V2010, path.toFile())) {
			new TableBuilder("CALC").addColumn(new ColumnBuilder("ID", DataType.LONG))
					.addColumn(new ColumnBuilder("RESULT", DataType.LONG).withCalculatedInfo("[ID]+1"))
					.toTable(database).addRow(10, null);
		}
		try (var connection = DriverManager.getConnection("jdbc:ucanaccess://" + path.toAbsolutePath())) {
			final var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setObjectName("CALC");
			Table table = reader.getAllFull(connection).getFirst();
			assertEquals("[ID]+1", table.getColumns().get("RESULT").getFormula());
			Table restored = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			assertEquals("[ID]+1", restored.getColumns().get("RESULT").getFormula());
			try (var statement = connection.createStatement();
					var result = statement.executeQuery("SELECT RESULT FROM CALC")) {
				assertTrue(result.next());
				assertEquals(11, result.getInt(1));
			}
		}
	}

	@Test
	void autoNumberDdlFollowsColumnType() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final Table guid = new Table("G");
		guid.getColumns().add(new com.sqlapp.data.schemas.Column("ID")
				.setDataType(com.sqlapp.data.db.datatype.DataType.UUID).setIdentity(true));
		final String sql = registry.createSql(guid, com.sqlapp.data.db.sql.SqlType.CREATE).getFirst().getSqlText();
		assertTrue(sql.contains("ID GUID"), sql);

		final Table big = new Table("B");
		big.getColumns().add(new com.sqlapp.data.schemas.Column("ID")
				.setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT).setIdentity(true));
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(big, com.sqlapp.data.db.sql.SqlType.CREATE));
	}
}
