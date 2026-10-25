/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.mysql.sql.MySqlGrantSchemaPrivilegeFactory;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SchemaPrivilege;
import com.sqlapp.data.schemas.User;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.jdbc.sql.SqlParameterCollection;

class MySqlSchemaPrivilegeTest {
	@ParameterizedTest
	@CsvSource({ "5,5,3,false", "5,7,9,false", "8,0,15,false", "8,0,16,true", "8,4,0,true", "9,0,0,true" })
	void oneQueryRetainsScopeAndUsesOnlySupportedSystemVariables(int major, int minor, int revision, boolean modern) {
		var dialect = dialect(major, minor);
		int[] queries = { 0 };
		var reader = new MySqlSchemaPrivilegeReader(dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries[0]++;
				assertEquals(modern, node.toString().contains("@@GLOBAL.partial_revokes"));
				SqlParameterCollection sql = new SqlParameterCollection();
				node.eval(context, sql);
				assertTrue(sql.getSql().contains("s.TABLE_SCHEMA IN"), sql.getSql());
				assertFalse(sql.getSql().contains("scope'"), sql.getSql());
				for (int i = 0; i < 12; i++) {
					try (var rows = row("db" + i, modern ? "1" : "0", "SELECT", "YES")) {
						handler.handleResultSetNext(new ExResultSet(rows));
					} catch (SQLException e) {
						throw new IllegalStateException(e);
					}
				}
				return null;
			}
		};
		var context = new ParametersContext();
		context.put("schemaName", "scope'");
		context.put("catalogName", "def");
		var grants = reader.doGetAll(null, context, version(major, minor, revision));
		assertEquals(12, grants.size());
		assertEquals(1, queries[0]);
		for (SchemaPrivilege grant : grants) {
			assertEquals(Boolean.toString(modern),
					grant.getSpecifics().get(MySqlGrantSchemaPrivilegeFactory.PARTIAL_REVOKES));
			String target = "`" + grant.getSchemaName() + "`.*";
			assertEquals("GRANT SELECT ON " + target + " TO `O'Reilly``reader`@`localhost` WITH GRANT OPTION",
					sql(dialect, grant, SqlType.GRANT));
			assertEquals("REVOKE SELECT ON " + target + " FROM `O'Reilly``reader`@`localhost`",
					sql(dialect, grant, SqlType.REVOKE));
		}
		assertTrue(reader.doGetAll(null, context, version(4, 1, 0)).isEmpty());
		assertEquals(1, queries[0]);
	}

	@Test
	void catalogHookLoadsTheSharedCollectionAndLinksUsersWithoutCollapsingSchemas() throws Exception {
		var dialect = dialect(8, 4);
		List<SchemaPrivilege> grants;
		try (var a = row("app", "1", "SELECT", "NO"); var b = row("archive", "1", "SELECT", "NO")) {
			var reader = new MySqlSchemaPrivilegeReader(dialect);
			grants = List.of(reader.createPrivilege(new ExResultSet(a)), reader.createPrivilege(new ExResultSet(b)));
		}
		int[] loads = { 0 };
		var catalogReader = new MySqlCatalogReader(dialect) {
			@Override
			public MySqlSchemaPrivilegeReader getSchemaPrivilegeReader() {
				return new MySqlSchemaPrivilegeReader(dialect) {
					@Override
					public List<SchemaPrivilege> getAllFull(Connection connection) {
						loads[0]++;
						return grants;
					}
				};
			}
		};
		Catalog catalog = new Catalog();
		catalog.setDialect(dialect);
		User user = new User("'O''Reilly`reader'@'localhost'");
		catalog.getUsers().add(user);
		catalogReader.setCommonAfter(null, catalog);
		assertEquals(1, loads[0]);
		assertEquals(2, catalog.getSchemaPrivileges().size());
		for (SchemaPrivilege grant : catalog.getSchemaPrivileges())
			assertSame(user, grant.getGrantee());
		assertEquals(2, catalog.clone().getSchemaPrivileges().size());
		assertInstanceOf(MySqlSchemaPrivilegeReader.class,
				((MySqlCatalogReader) dialect.getCatalogReader()).getSchemaPrivilegeReader());
	}

	@Test
	void requiresModeForAmbiguousDatabasePatternsAndPreservesItWithoutTranslation() throws Exception {
		var dialect = dialect(8, 4);
		SchemaPrivilege grant = new SchemaPrivilege();
		grant.setPrivilege("SELECT");
		grant.setGranteeName("'reader'@'%'");
		for (String schema : List.of("app_db", "tenant%", "app\\_db")) {
			grant.setSchemaName(schema);
			grant.getSpecifics().clear();
			assertThrows(IllegalArgumentException.class, () -> sql(dialect, grant, SqlType.GRANT));
			for (String mode : List.of("true", "false")) {
				grant.getSpecifics().put(MySqlGrantSchemaPrivilegeFactory.PARTIAL_REVOKES, mode);
				assertEquals("GRANT SELECT ON `" + schema + "`.* TO `reader`@`%`", sql(dialect, grant, SqlType.GRANT));
			}
		}
		grant.setSchemaName("app");
		grant.getSpecifics().clear();
		assertTrue(sql(dialect, grant, SqlType.GRANT).contains("ON `app`.*"));
		grant.getSpecifics().put(MySqlGrantSchemaPrivilegeFactory.PARTIAL_REVOKES, "guess");
		assertThrows(IllegalArgumentException.class, () -> sql(dialect, grant, SqlType.GRANT));
		grant.getSpecifics().put(MySqlGrantSchemaPrivilegeFactory.PARTIAL_REVOKES, "true");
		assertThrows(IllegalArgumentException.class, () -> sql(dialect(5, 7), grant, SqlType.GRANT));
		grant.setPrivilege("BINLOG_ADMIN");
		assertThrows(IllegalArgumentException.class, () -> sql(dialect, grant, SqlType.GRANT));
		grant.setPrivilege("FILE");
		assertThrows(IllegalArgumentException.class, () -> sql(dialect, grant, SqlType.GRANT));
		try (var rows = row("odd`db", "0", "USAGE", "YES")) {
			var grantOnly = new MySqlSchemaPrivilegeReader(dialect).createPrivilege(new ExResultSet(rows));
			assertEquals("GRANT GRANT OPTION ON `odd``db`.* TO `O'Reilly``reader`@`localhost`",
					sql(dialect, grantOnly, SqlType.GRANT));
		}
	}

	@Test
	void failuresAndInvalidMetadataAreNotReportedAsEmptyGrants() throws Exception {
		var reader = new MySqlSchemaPrivilegeReader(dialect(8, 4)) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				throw new IllegalStateException("denied");
			}
		};
		assertThrows(IllegalStateException.class,
				() -> reader.doGetAll(null, new ParametersContext(), version(8, 4, 0)));
		try (var mode = row("app", null, "SELECT", "YES"); var grantable = row("app", "1", "SELECT", "UNKNOWN")) {
			assertThrows(SQLException.class, () -> reader.createPrivilege(new ExResultSet(mode)));
			assertThrows(SQLException.class, () -> reader.createPrivilege(new ExResultSet(grantable)));
		}
	}

	private String sql(Dialect dialect, SchemaPrivilege grant, SqlType type) {
		SqlFactory<SchemaPrivilege> factory = dialect.createSqlFactoryRegistry().getSqlFactory(grant, type);
		return factory.createSql(grant).get(0).getSqlText();
	}

	private Dialect dialect(int major, int minor) {
		return DialectResolver.getInstance().getDialect("MySql", major, minor, 0);
	}

	private ProductVersionInfo version(int major, int minor, int revision) {
		return new ProductVersionInfo().setMajorVersion(major).setMinorVersion(minor).setRevision(revision);
	}

	private CachedRowSet row(String schema, String mode, String privilege, String grantable) throws SQLException {
		String[] names = { "TABLE_CATALOG", "TABLE_SCHEMA", "GRANTEE", "PRIVILEGE_TYPE", "IS_GRANTABLE",
				"PARTIAL_REVOKES" };
		String[] values = { "def", schema, "'O'Reilly`reader'@'localhost'", privilege, grantable, mode };
		RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
		metadata.setColumnCount(names.length);
		for (int i = 0; i < names.length; i++) {
			metadata.setColumnName(i + 1, names[i]);
			metadata.setColumnType(i + 1, Types.VARCHAR);
		}
		CachedRowSet rows = RowSetProvider.newFactory().createCachedRowSet();
		rows.setMetaData(metadata);
		rows.moveToInsertRow();
		for (int i = 0; i < names.length; i++)
			rows.updateObject(i + 1, values[i]);
		rows.insertRow();
		rows.moveToCurrentRow();
		rows.beforeFirst();
		rows.next();
		return rows;
	}
}
