package com.sqlapp.data.db.dialect.mariadb.sql;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class MariadbInheritedSqlRegressionTest {
	@ParameterizedTest
	@CsvSource({ "10,0", "10,2", "10,3", "10,5", "11,4", "11,8", "12,1" })
	void inheritsCorrectedMysqlSql(int major, int minor) {
		var dialect = DialectResolver.getInstance().getDialect("mariadb", major, minor, 0);
		var registry = dialect.createSqlFactoryRegistry();
		var table = new Table("key table");
		table.getColumns().add("key id", c -> c.setDataType(DataType.INT));
		table.setPrimaryKey(table.getColumns().get("key id"));
		SqlFactory<Table> merge = registry.getSqlFactory(table, SqlType.MERGE);
		SqlFactory<Table> clear = registry.getSqlFactory(table, SqlType.TRUNCATE_TEMPORARY);
		var binary = new Table("binary_types");
		binary.getColumns().add(new Column("uuid_value").setDataType(DataType.UUID));
		var blob = new Column("blob_value");
		assertTrue(dialect.setDbType("tinyblob", 255L, null, blob));
		binary.getColumns().add(blob);
		SqlFactory<Table> create = registry.getSqlFactory(binary, SqlType.CREATE);
		String ddl = create.createSql(binary).getFirst().getSqlText();
		String mergeSql = merge.createSql(table).getFirst().getSqlText().replaceAll("\\s+", " ").trim();
		assertAll(() -> assertEquals("DELETE FROM `key table`", clear.createSql(table).getFirst().getSqlText()),
				() -> assertTrue(mergeSql.endsWith("UPDATE `key id` = `key id`"), mergeSql),
				() -> assertTrue(ddl.contains("uuid_value BINARY(16)"), ddl),
				() -> assertTrue(ddl.contains("blob_value TINYBLOB"), ddl));
	}
}
