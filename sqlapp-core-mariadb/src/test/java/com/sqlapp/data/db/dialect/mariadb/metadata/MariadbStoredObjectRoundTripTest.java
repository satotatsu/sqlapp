/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.mysql.metadata.*;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;

class MariadbStoredObjectRoundTripTest {
	private final Dialect dialect = DialectResolver.getInstance().getDialect("MariaDB", 11, 5, 0);
	private final ProductVersionInfo version = new ProductVersionInfo().setMajorVersion(11).setMinorVersion(5)
			.setRevision(0);

	private ExResultSet row(Map<String, String> values) {
		return new ExResultSet(null) {
			@Override
			public String getString(String name) {
				return values.get(name.toUpperCase(Locale.ROOT));
			}

			@Override
			public java.sql.Timestamp getTimestamp(String name) {
				return null;
			}

			@Override
			public long getLong(String name) {
				return 0;
			}

			@Override
			public int getInt(String name) {
				return 0;
			}

			@Override
			public boolean wasNull() {
				return true;
			}

			@Override
			public java.sql.ResultSetMetaData getMetaData() {
				return (java.sql.ResultSetMetaData) java.lang.reflect.Proxy.newProxyInstance(
						getClass().getClassLoader(), new Class<?>[] { java.sql.ResultSetMetaData.class },
						(proxy, method, args) -> method.getName().equals("getColumnCount") ? 0 : null);
			}
		};
	}

	private class ViewReader extends MySqlViewReader {
		ViewReader() {
			super(dialect);
		}

		View read(ExResultSet row) throws Exception {
			return (View) createTable(row);
		}
	}

	private class ProcedureReader extends MySqlProcedure553Reader {
		ProcedureReader() {
			super(dialect);
		}

		Procedure read(ExResultSet row) throws Exception {
			return createProcedure(row, version);
		}
	}

	private class FunctionReader extends MySqlFunction553Reader {
		FunctionReader() {
			super(dialect);
		}

		Function read(ExResultSet row) throws Exception {
			return createFunction(row, version);
		}
	}

	private class ArgumentReader extends MySqlProcedureArgument553Reader {
		ArgumentReader() {
			super(dialect);
		}

		NamedArgument read(ExResultSet row) throws Exception {
			return createNamedArgument(row);
		}
	}

	private Map<String, String> routine(String body) {
		return Map.of("ROUTINE_NAME", "order routine", "ROUTINE_SCHEMA", "app", "ROUTINE_DEFINITION", body,
				"IS_DETERMINISTIC", "YES", "SQL_DATA_ACCESS", "NO SQL", "SECURITY_TYPE", "INVOKER", "DEFINER",
				"owner@localhost", "DTD_IDENTIFIER", "decimal(12,3)");
	}

	private String ddl(AbstractSchemaObject<?> object) {
		return dialect.createSqlFactoryRegistry().createSql(object, SqlType.CREATE).get(0).getSqlText();
	}

	@Test
	void viewRetainsAlgorithmSecurityCheckAndOutputNames() throws Exception {
		View view = new ViewReader().read(row(Map.of("TABLE_NAME", "order view", "TABLE_SCHEMA", "app",
				"VIEW_DEFINITION", "SELECT id FROM orders", "IS_UPDATABLE", "YES", "CHECK_OPTION", "CASCADED",
				"SECURITY_TYPE", "INVOKER", "ALGORITHM", "MERGE", "DEFINER", "owner@localhost")));
		view.getColumns().add("order id", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
		View restored = SchemaUtils.readXml(new java.io.StringReader(view.asXml()));
		String sql = ddl(restored);
		assertTrue(sql.contains("ALGORITHM = MERGE"), sql);
		assertTrue(sql.contains("SQL SECURITY INVOKER VIEW"), sql);
		assertTrue(sql.contains("`order id`"), sql);
		assertTrue(sql.contains("WITH CASCADED CHECK OPTION"), sql);
		assertTrue(sql.contains("SELECT id FROM orders"), sql);
		assertTrue(sql.contains("`owner`@`localhost`"), sql);
	}

	@Test
	void procedureRetainsBodyAndEnumArgumentThroughXml() throws Exception {
		Procedure procedure = new ProcedureReader().read(row(routine("BEGIN SELECT 1; END")));
		procedure.getArguments().add(new ArgumentReader().read(row(Map.of("PARAMETER_NAME", "order value",
				"PARAMETER_MODE", "INOUT", "DATA_TYPE", "enum", "DTD_IDENTIFIER", "enum('a, b','done')"))));
		Procedure restored = SchemaUtils.readXml(new java.io.StringReader(procedure.asXml()));
		String sql = ddl(restored);
		assertTrue(sql.contains("INOUT"), sql);
		assertTrue(sql.contains("`order value`"), sql);
		assertTrue(sql.contains("'a, b'"), sql);
		assertTrue(sql.contains("SQL SECURITY INVOKER"), sql);
		assertTrue(sql.contains("BEGIN SELECT 1; END"), sql);
	}

	@Test
	void functionRetainsDecimalReturnAndBodyThroughXml() throws Exception {
		Function function = new FunctionReader().read(row(routine("RETURN 1.250")));
		Function restored = SchemaUtils.readXml(new java.io.StringReader(function.asXml()));
		String sql = ddl(restored);
		assertTrue(sql.contains("DECIMAL(12,3)"), sql);
		assertTrue(sql.contains("SQL SECURITY INVOKER"), sql);
		assertTrue(sql.contains("RETURN 1.250"), sql);
	}
}
