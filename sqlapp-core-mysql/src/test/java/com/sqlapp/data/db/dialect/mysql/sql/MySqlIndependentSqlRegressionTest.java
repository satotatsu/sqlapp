package com.sqlapp.data.db.dialect.mysql.sql;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class MySqlIndependentSqlRegressionTest {

	@ParameterizedTest
	@CsvSource({ "5,1,0", "5,6,4", "5,6,5", "5,7,0", "8,0,0", "8,0,1", "8,4,0", "9,0,0" })
	void preservesBinaryTypeDdl(int major, int minor, int revision) {
		Dialect dialect = dialect(major, minor, revision);
		Table table = new Table("binary_types");
		table.getColumns().add(new Column("uuid_value").setDataType(DataType.UUID));
		Column blob = new Column("blob_value");
		assertTrue(dialect.setDbType("tinyblob", 255L, null, blob));
		table.getColumns().add(blob);
		String sql = sql(dialect, table, SqlType.CREATE);
		assertAll(() -> assertTrue(sql.contains("uuid_value BINARY(16)"), sql),
				() -> assertTrue(sql.contains("blob_value TINYBLOB"), sql));
		assertEquals(DataType.UUID, table.getColumns().get("uuid_value").getDataType());
	}

	@ParameterizedTest
	@CsvSource({ "5,1,0", "5,6,4", "5,6,5", "5,7,0", "8,0,0", "8,0,1", "8,4,0", "9,0,0" })
	void clearsTemporaryTableWithDelete(int major, int minor, int revision) {
		Dialect dialect = dialect(major, minor, revision);
		Table table = new Table("temporary table");
		assertEquals("DELETE FROM `temporary table`", sql(dialect, table, SqlType.TRUNCATE_TEMPORARY));
	}

	@ParameterizedTest
	@CsvSource({ "5,1,0", "5,6,4", "5,6,5", "5,7,0", "8,0,0", "8,0,1", "8,4,0", "9,0,0" })
	void mergesWithoutMutableColumns(int major, int minor, int revision) {
		Dialect dialect = dialect(major, minor, revision);
		Table table = new Table("key_table");
		table.getColumns().add("key id", c -> c.setDataType(DataType.INT));
		table.setPrimaryKey(table.getColumns().get("key id"));
		assertTrue(sql(dialect, table, SqlType.MERGE).endsWith("UPDATE `key id` = `key id`"));
		table.getColumns().add("second_key", c -> c.setDataType(DataType.INT));
		table.getConstraints().clear();
		table.setPrimaryKey(table.getColumns().get("key id"), table.getColumns().get("second_key"));
		assertTrue(sql(dialect, table, SqlType.MERGE).endsWith("UPDATE `key id` = `key id`"));
		if (dialect.supportsColumnFormula()) {
			table.getColumns().add("computed", c -> c.setDataType(DataType.INT).setFormula("second_key + 1"));
			String generatedSql = sql(dialect, table, SqlType.MERGE);
			assertTrue(generatedSql.endsWith("UPDATE `key id` = `key id`"), generatedSql);
			assertFalse(generatedSql.contains("computed"), generatedSql);
		}
	}

	@ParameterizedTest
	@CsvSource({ "5,1,0", "5,7,0", "8,4,0", "9,0,0" })
	void mergesWhenUpdatesAreExcluded(int major, int minor, int revision) {
		Dialect dialect = dialect(major, minor, revision);
		Table table = new Table("key_table");
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		table.getColumns().add("payload", c -> c.setDataType(DataType.INT));
		table.setPrimaryKey(table.getColumns().get("id"));
		SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(table, SqlType.MERGE);
		factory.getTableOptions().setUpdateableColumn(false);
		String sql = factory.createSql(table).getFirst().getSqlText().replaceAll("\\s+", " ").trim();
		assertTrue(sql.endsWith("UPDATE id = id"), sql);
	}

	private Dialect dialect(int major, int minor, int revision) {
		return DialectResolver.getInstance().getDialect("mysql", major, minor, revision);
	}

	private String sql(Dialect dialect, Table table, SqlType type) {
		SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(table, type);
		return factory.createSql(table).getFirst().getSqlText().replaceAll("\\s+", " ").trim();
	}
}
