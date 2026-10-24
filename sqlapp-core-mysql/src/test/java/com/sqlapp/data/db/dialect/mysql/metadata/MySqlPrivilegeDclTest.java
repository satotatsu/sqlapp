/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.ObjectPrivilege;
import com.sqlapp.data.schemas.ColumnPrivilege;
import com.sqlapp.data.schemas.UserPrivilege;
import com.sqlapp.data.schemas.PrivilegeState;
import com.sqlapp.jdbc.ExResultSet;

class MySqlPrivilegeDclTest {
	@ParameterizedTest
	@CsvSource({"5,5", "5,7", "8,0", "8,4", "9,0"})
	void metadataRoundTripsThroughAllThreeNativeScopes(int major, int minor) throws Exception {
		var dialect = dialect(major, minor);
		try (var rows = rows()) {
			var rs = new ExResultSet(rows);
			var table = new MySqlObjectPrivilegeReader(dialect).createPrivilege(rs);
			var column = new MySqlColumnPrivilegeReader(dialect).createColumnPrivilege(rs);
			var global = new MySqlUserPrivilegeReader(dialect).createPrivilege(rs);
			assertEquals("GRANT SELECT ON `app``db`.`select` TO `O'Reilly``reader`@`10.0.%` WITH GRANT OPTION", sql(dialect, table, SqlType.GRANT));
			assertEquals("REVOKE SELECT ON `app``db`.`select` FROM `O'Reilly``reader`@`10.0.%`", sql(dialect, table, SqlType.REVOKE));
			assertEquals("GRANT SELECT (`order``id`) ON `app``db`.`select` TO `O'Reilly``reader`@`10.0.%` WITH GRANT OPTION", sql(dialect, column, SqlType.GRANT));
			assertEquals("REVOKE SELECT (`order``id`) ON `app``db`.`select` FROM `O'Reilly``reader`@`10.0.%`", sql(dialect, column, SqlType.REVOKE));
			assertEquals("GRANT SELECT ON *.* TO `O'Reilly``reader`@`10.0.%` WITH GRANT OPTION", sql(dialect, global, SqlType.GRANT));
			assertEquals("REVOKE SELECT ON *.* FROM `O'Reilly``reader`@`10.0.%`", sql(dialect, global, SqlType.REVOKE));
		}
	}

	@Test
	void validatesScopeAndRequiresCompleteIdentifiersAndAccounts() {
		var dialect = dialect(8, 4);
		var object = object();
		for (String privilege : List.of("ALTER", "CREATE", "CREATE VIEW", "DELETE", "DROP", "GRANT OPTION", "INDEX", "INSERT", "REFERENCES", "SELECT", "SHOW VIEW", "TRIGGER", "UPDATE")) {
			object.setPrivilege(privilege);
			assertTrue(sql(dialect, object, SqlType.GRANT).startsWith("GRANT " + privilege + " ON"));
		}
		object.setPrivilege("FILE");
		assertThrows(IllegalArgumentException.class, () -> sql(dialect, object, SqlType.GRANT));
		object.setPrivilege("SELECT; GRANT ALL");
		assertThrows(IllegalArgumentException.class, () -> sql(dialect, object, SqlType.GRANT));
		object.setPrivilege("SELECT");
		object.setSchemaName(null);
		assertThrows(IllegalArgumentException.class, () -> sql(dialect, object, SqlType.GRANT));
		object.setSchemaName("app");
		object.setGranteeName("reader");
		assertThrows(IllegalArgumentException.class, () -> sql(dialect, object, SqlType.GRANT));
		object.setGranteeName("'reader'@'localhost'");
		object.setState(PrivilegeState.Revoke);
		assertThrows(IllegalArgumentException.class, () -> sql(dialect, object, SqlType.GRANT));
		object.setState(PrivilegeState.Grant);
		assertThrows(IllegalArgumentException.class, () -> sql(dialect, object, SqlType.REVOKE));
		object.setState((PrivilegeState) null);
		object.setObjectName("*");
		assertTrue(sql(dialect, object, SqlType.GRANT).contains("ON `app`.`*`"));
		var column = new ColumnPrivilege();
		column.setSchemaName("app"); column.setObjectName("t"); column.setGranteeName("'reader'@'%'");
		column.setColumnName("col");
		for (String privilege : List.of("SELECT", "INSERT", "UPDATE", "REFERENCES")) {
			column.setPrivilege(privilege);
			assertTrue(sql(dialect, column, SqlType.GRANT).contains(privilege + " (`col`)"));
		}
		column.setPrivilege("DELETE");
		assertThrows(IllegalArgumentException.class, () -> sql(dialect, column, SqlType.GRANT));
		column.setPrivilege("SELECT"); column.setColumnName(null);
		assertThrows(IllegalArgumentException.class, () -> sql(dialect, column, SqlType.GRANT));
	}

	@Test
	void dynamicGlobalNamesRemainExplicitAndVersionBounded() {
		var global = new UserPrivilege();
		global.setGranteeName("'reader'@'%'");
		global.setPrivilege("binlog_admin");
		assertEquals("GRANT BINLOG_ADMIN ON *.* TO `reader`@`%`", sql(dialect(8, 4), global, SqlType.GRANT));
		global.setGrantable(true);
		assertTrue(sql(dialect(8, 4), global, SqlType.GRANT).endsWith("WITH GRANT OPTION"));
		assertEquals("REVOKE BINLOG_ADMIN ON *.* FROM `reader`@`%`", sql(dialect(8, 4), global, SqlType.REVOKE));
		assertThrows(IllegalArgumentException.class, () -> sql(dialect(5, 7), global, SqlType.GRANT));
		global.setPrivilege("CUSTOM_PLUGIN_ADMIN");
		assertTrue(sql(dialect(8, 4), global, SqlType.GRANT).startsWith("GRANT CUSTOM_PLUGIN_ADMIN"));
		for (String invalid : List.of("SELECT ON *.* TO other", "PROXY", "ALL", "BINLOG_ADMIN, SELECT")) {
			global.setPrivilege(invalid);
			assertThrows(IllegalArgumentException.class, () -> sql(dialect(8, 4), global, SqlType.GRANT));
		}
		global.setPrivilege("SELECT"); global.setAdmin(true);
		assertThrows(IllegalArgumentException.class, () -> sql(dialect(8, 4), global, SqlType.GRANT));
	}

	private ObjectPrivilege object() {
		var object = new ObjectPrivilege();
		object.setSchemaName("app"); object.setObjectName("t");
		object.setGranteeName("'reader'@'localhost'"); object.setPrivilege("SELECT");
		return object;
	}
	private Dialect dialect(int major, int minor) { return DialectResolver.getInstance().getDialect("MySql", major, minor, 0); }
	private <T> String sql(Dialect dialect, T object, SqlType type) {
		SqlFactory<T> factory = dialect.createSqlFactoryRegistry().getSqlFactory(object, type);
		return factory.createSql(object).get(0).getSqlText();
	}
	private CachedRowSet rows() throws SQLException {
		Map<String, Object> values = new LinkedHashMap<>();
		String[] pairs = {"TABLE_CATALOG", "def", "TABLE_SCHEMA", "app`db", "TABLE_NAME", "select", "COLUMN_NAME", "order`id",
			"GRANTEE", "'O'Reilly`reader'@'10.0.%'", "PRIVILEGE_TYPE", "SELECT", "IS_GRANTABLE", "YES"};
		for (int i = 0; i < pairs.length; i += 2) values.put(pairs[i], pairs[i + 1]);
		RowSetMetaDataImpl metadata = new RowSetMetaDataImpl(); metadata.setColumnCount(values.size());
		int i = 0;
		for (String key : values.keySet()) { metadata.setColumnName(++i, key); metadata.setColumnType(i, Types.VARCHAR); }
		CachedRowSet rows = RowSetProvider.newFactory().createCachedRowSet(); rows.setMetaData(metadata);
		rows.moveToInsertRow(); i = 0;
		for (Object value : values.values()) rows.updateObject(++i, value);
		rows.insertRow(); rows.moveToCurrentRow(); rows.beforeFirst(); rows.next();
		return rows;
	}
}
