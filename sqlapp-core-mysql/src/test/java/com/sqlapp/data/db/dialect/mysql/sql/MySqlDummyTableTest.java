package com.sqlapp.data.db.dialect.mysql.sql;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.SqlParser;

class MySqlDummyTableTest {
	@ParameterizedTest
	@CsvSource({ "5,1,0", "5,6,4", "5,6,5", "5,7,0", "8,0,0", "8,0,1", "8,4,0", "9,0,0" })
	void generatesExecutableDummyTableAndRowTemplates(int major, int minor, int revision) {
		var dialect = DialectResolver.getInstance().getDialect("mysql", major, minor, revision);
		var table = new Table("target_table");
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		table.setPrimaryKey(table.getColumns().get("id"));
		table.getRows().add(r -> r.put("id", 1));
		table.getRows().add(r -> r.put("id", 2));
		SqlFactory<Table> insert = dialect.createSqlFactoryRegistry().getSqlFactory(table,
				SqlType.INSERT_SELECT_NOT_EXISTS);
		String sql = insert.createSql(table).getFirst().getSqlText();
		assertTrue(sql.contains("FROM DUAL"), sql);
		assertFalse(sql.contains("(VALUES(0))"), sql);
		SqlFactory<Table> rows = dialect.createSqlFactoryRegistry().getSqlFactory(table, SqlType.INSERT_ROWS);
		var parsed = SqlParser.getInstance().parse(dialect, rows.createSql(table).getFirst().getSqlText()).eval(table);
		assertEquals(2, parsed.getParameterSize());
		assertFalse(parsed.getSql().contains("(VALUES(0))"), parsed.getSql());
		if (dialect.supportsValues()) {
			assertTrue(parsed.getSql().contains("VALUES"), parsed.getSql());
		} else {
			assertTrue(parsed.getSql().contains("FROM DUAL"), parsed.getSql());
			assertTrue(parsed.getSql().contains("UNION ALL"), parsed.getSql());
		}
	}
}
