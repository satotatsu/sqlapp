package com.sqlapp.data.db.dialect.aurora.mysql;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;

class AuroraMySqlStageOneRegressionTest {
	@ParameterizedTest
	@CsvSource({ "8,0", "8,4" })
	void inheritsStageOneFixes(int major, int minor) throws Exception {
		var dialect = DialectResolver.getInstance().getDialect("aurora-mysql", major, minor, 0);
		var table = new Table("target_table").setCatalogName("def").setSchemaName("app");
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		table.setPrimaryKey(table.getColumns().get("id"));
		SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(table,
				SqlType.INSERT_SELECT_NOT_EXISTS);
		var calls = new ArrayList<String>();
		var statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { PreparedStatement.class }, (p, m, a) -> {
					assertEquals("setBytes", m.getName());
					assertNull(a[1]);
					calls.add(m.getName());
					return null;
				});
		var sql = new ArrayList<String>();
		var stage = (Statement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Statement.class },
				(p, m, a) -> {
					if (m.getName().equals("close"))
						return null;
					assertEquals("execute", m.getName());
					sql.add((String) a[0]);
					throw new SQLException("stop after target resolution");
				});
		var connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { Connection.class }, (p, m, a) -> switch (m.getName()) {
				case "getAutoCommit" -> false;
				case "createStatement" -> stage;
				default -> throw new AssertionError(m.getName());
				});
		assertAll(() -> assertTrue(factory.createSql(table).getFirst().getSqlText().contains("FROM DUAL")), () -> {
			dialect.getDbDataTypes().getDbType(DataType.GEOMETRY).getJdbcTypeHandler().setObject(statement, 1, null);
			assertEquals(java.util.List.of("setBytes"), calls);
		}, () -> {
			var failure = assertThrows(SQLException.class,
					() -> BulkUpsertResolver.resolve(dialect).execute(connection, table, null));
			assertEquals("stop after target resolution", failure.getMessage());
			assertTrue(sql.getFirst().endsWith(" FROM app.target_table WHERE 1 = 0"), sql.toString());
		});
	}
}
