/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver;

import static com.sqlapp.util.CommonUtils.LEN_2GB;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlserver.resolver.SqlServerDialectResolver;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

/**
 * SQL Server Dialectのバージョン判定・機能フラグ・生成SQLのテスト
 */
class SqlServerDialectCapabilityTest {

	private static Dialect dialect(final int major) {
		return SqlServerDialectResolver.getInstance().getDialect(major, 0);
	}

	private static String text(final List<SqlOperation> operations) {
		return operations.stream().map(SqlOperation::getSqlText).collect(Collectors.joining(";"));
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(final Class<T> type, final java.lang.reflect.InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
	}

	private static DatabaseMetaData metadata(final String product, final int major, final Integer engineEdition,
			final List<String> queries) {
		final Connection connection = engineEdition == null ? null : proxy(Connection.class, (p, method, args) -> {
			if ("createStatement".equals(method.getName())) {
				return proxy(Statement.class, (s, m, a) -> {
					if ("executeQuery".equals(m.getName())) {
						queries.add((String) a[0]);
						final boolean[] read = { false };
						return proxy(ResultSet.class, (r, rm, ra) -> switch (rm.getName()) {
						case "next" -> {
							final boolean result = !read[0];
							read[0] = true;
							yield result;
						}
						case "getInt" -> engineEdition;
						case "close" -> null;
						default -> throw new UnsupportedOperationException(rm.getName());
						});
					}
					if ("close".equals(m.getName())) {
						return null;
					}
					throw new UnsupportedOperationException(m.getName());
				});
			}
			throw new UnsupportedOperationException(method.getName());
		});
		return proxy(DatabaseMetaData.class, (p, method, args) -> switch (method.getName()) {
		case "getDatabaseProductName" -> product;
		case "getDatabaseMajorVersion" -> major;
		case "getConnection" -> connection;
		default -> throw new UnsupportedOperationException(method.getName());
		});
	}

	@Test
	void sql2016Sp1IsResolvedByBuildNumber() {
		final var resolver = SqlServerDialectResolver.getInstance();
		assertEquals(SqlServer2016.class, resolver.getDialect(13, 0).getClass());
		assertEquals(SqlServer2016.class, resolver.getDialect(13, 0, 1601).getClass());
		assertEquals(SqlServer2016Sp1.class, resolver.getDialect(13, 0, 4001).getClass());
		assertEquals(SqlServer2016Sp1.class, resolver.getDialect(13, 0, 6300).getClass());
		assertEquals(SqlServer2017.class, resolver.getDialect(14, 0, 1000).getClass());
	}

	@Test
	void azureSqlIsResolvedToLatestDialect() {
		final var resolver = SqlServerDialectResolver.getInstance();
		final List<String> queries = new ArrayList<>();
		assertInstanceOf(SqlServer2022.class,
				resolver.resolveDatabaseMetaData(metadata("Microsoft SQL Server", 12, 5, queries)));
		assertTrue(queries.get(0).contains("SERVERPROPERTY('EngineEdition')"), queries.toString());
		assertInstanceOf(SqlServer2022.class,
				resolver.resolveDatabaseMetaData(metadata("Microsoft SQL Server", 12, 8, queries)));
		// オンプレミスのSQL Server 2014はバージョンによる判定に委ねる
		assertNull(resolver.resolveDatabaseMetaData(metadata("Microsoft SQL Server", 12, 3, queries)));
		// 接続が無い場合と12以外のバージョンはクエリしない
		queries.clear();
		assertNull(resolver.resolveDatabaseMetaData(metadata("Microsoft SQL Server", 12, null, queries)));
		assertNull(resolver.resolveDatabaseMetaData(metadata("Microsoft SQL Server", 16, 5, queries)));
		assertNull(resolver.resolveDatabaseMetaData(metadata("PostgreSQL", 12, 5, queries)));
		assertTrue(queries.isEmpty(), queries.toString());
	}

	@Test
	void dropTableHasNoCascadeConstraints() {
		for (int version : new int[] { 8, 9, 10, 11, 12, 13, 16 }) {
			final Dialect dialect = dialect(version);
			assertFalse(dialect.supportsDropCascade(), "version=" + version);
			final Table table = new Table("Orders").setSchemaName("dbo");
			table.getColumns().add(new Column("ID").setDataType(DataType.INT));
			final String sql = text(dialect.createSqlFactoryRegistry().createSql(table, SqlType.DROP));
			assertTrue(sql.contains("DROP TABLE"), "version=" + version + " " + sql);
			assertFalse(sql.contains("CASCADE"), "version=" + version + " " + sql);
		}
	}

	@Test
	void dropTableIfExistsBefore2016() {
		for (int version : new int[] { 8, 9, 10, 11, 12 }) {
			final SqlFactoryRegistry registry = dialect(version).createSqlFactoryRegistry();
			registry.getOptions().setDropIfExists(true);
			registry.getOptions().setDecorateSchemaName(true);
			final Table table = new Table("Order's").setSchemaName("dbo");
			table.getColumns().add(new Column("ID").setDataType(DataType.INT));
			final List<SqlOperation> operations = registry.createSql(table, SqlType.DROP);
			assertEquals(1, operations.size(), "version=" + version + " " + operations);
			final String sql = text(operations);
			assertTrue(sql.startsWith("IF OBJECT_ID(N'"), sql);
			assertTrue(sql.contains("Order''s"), sql);
			assertTrue(sql.contains(", N'U') IS NOT NULL"), sql);
			assertTrue(sql.contains("DROP TABLE"), sql);
			assertFalse(sql.contains("PROCEDURE"), sql);
		}
	}

	@Test
	void sequenceIsSupportedFrom2012() {
		assertFalse(dialect(10).supportsSequence());
		for (int version : new int[] { 11, 12, 13, 14, 15, 16 }) {
			assertTrue(dialect(version).supportsSequence(), "version=" + version);
		}
	}

	@Test
	void currentTimestampFunctionsFrom2008() {
		assertEquals("CURRENT_TIMESTAMP", dialect(9).getCurrentTimestampFunction());
		final Dialect dialect = dialect(10);
		assertEquals("SYSDATETIME()", dialect.getCurrentTimestampFunction());
		assertEquals("SYSDATETIMEOFFSET()", dialect.getCurrentTimestampWithTimeZoneFunction());
		assertEquals("SYSDATETIMEOFFSET()",
				dialect.getDbDataTypes().getDbType(DataType.TIMESTAMP_WITH_TIMEZONE).getDefaultValueLiteral());
		assertEquals("SYSDATETIME()", dialect.getDbDataTypes().getDbType(DataType.TIMESTAMP).getDefaultValueLiteral());
	}

	@Test
	void sqlServer2000ForeignKeyRules() {
		final Dialect dialect = dialect(8);
		assertTrue(dialect.supportsRuleOnDelete(CascadeRule.None));
		assertTrue(dialect.supportsRuleOnDelete(CascadeRule.Cascade));
		assertFalse(dialect.supportsRuleOnDelete(CascadeRule.SetNull));
		assertFalse(dialect.supportsRuleOnUpdate(CascadeRule.SetNull));
		assertTrue(dialect(9).supportsRuleOnDelete(CascadeRule.SetNull));
	}

	@Test
	void rowVersionIsOptimisticLockColumn() {
		final Dialect dialect = dialect(16);
		assertTrue(dialect.isOptimisticLockColumn(new Column("RowVer").setDataType(DataType.ROWVERSION)));
		assertFalse(dialect.isOptimisticLockColumn(new Column("Data").setDataType(DataType.VARBINARY)));
	}

	@Test
	void varbinaryOver8000BecomesMax() {
		final Column column = new Column("Data");
		dialect(9).setDbType(DataType.VARBINARY, "VARBINARY", 10000L, null, column);
		assertEquals(DataType.VARBINARY, column.getDataType());
		assertEquals(LEN_2GB - 1, column.getLength().longValue());
	}

	@Test
	void temporaryTableNameAlwaysStartsWithHash() {
		final Dialect dialect = dialect(16);
		final Table table = new Table("Orders");
		assertTrue(dialect.getTemporaryTableName(table, null, null, false).contains("#Orders"));
		final String prefixed = dialect.getTemporaryTableName(table, "tmp_", "_1", false);
		assertTrue(prefixed.contains("#tmp_Orders_1"), prefixed);
		final String global = dialect.getTemporaryTableName(table, "##", null, false);
		assertTrue(global.contains("##Orders"), global);
		assertFalse(global.contains("###"), global);
	}

	@Test
	void decimalDefaultsMatchSqlServer() {
		for (DataType dataType : new DataType[] { DataType.DECIMAL, DataType.NUMERIC }) {
			final var type = (com.sqlapp.data.db.datatype.DecimalType) dialect(16).getDbDataTypes().getDbType(dataType);
			assertEquals(18, type.getDefaultPrecision(), dataType.toString());
			assertEquals(0, type.getDefaultScale(), dataType.toString());
			assertEquals(38, type.getMaxPrecision(), dataType.toString());
		}
	}

	@Test
	void dialectsAreEqualOnlyToSameVersion() {
		final int[] versions = { 8, 9, 10, 11, 12, 13, 14, 15, 16 };
		for (int a : versions) {
			assertEquals(dialect(a), dialect(a));
			assertEquals(dialect(a).hashCode(), dialect(a).hashCode());
			for (int b : versions) {
				if (a != b) {
					assertNotEquals(dialect(a), dialect(b), a + " vs " + b);
				}
			}
		}
		final var resolver = SqlServerDialectResolver.getInstance();
		assertNotEquals(resolver.getDialect(13, 0), resolver.getDialect(13, 0, 4001));
		assertNotEquals(resolver.getDialect(10, 0), resolver.getDialect(10, 50));
	}

	@Test
	void maxTypesUseActualUpperLimit() {
		final Dialect dialect = dialect(9);
		final Column varchar = new Column("A");
		dialect.setDbType("varchar", -1L, null, varchar);
		assertEquals(LEN_2GB - 1, varchar.getLength().longValue());
		final Column nvarchar = new Column("B");
		dialect.setDbType("nvarchar", -1L, null, nvarchar);
		assertEquals(com.sqlapp.util.CommonUtils.LEN_1GB - 1, nvarchar.getLength().longValue());
		final Column varbinary = new Column("C");
		dialect.setDbType("varbinary", -1L, null, varbinary);
		assertEquals(LEN_2GB - 1, varbinary.getLength().longValue());
		final Table table = new Table("T");
		table.getColumns().add(varchar);
		table.getColumns().add(nvarchar);
		table.getColumns().add(varbinary);
		table.getColumns().add(new Column("D").setDataType(DataType.VARCHAR).setLength(9000));
		table.getColumns().add(new Column("E").setDataType(DataType.NVARCHAR).setLength(5000));
		table.getColumns().add(new Column("F").setDataType(DataType.VARCHAR).setLength(8000));
		final String sql = text(dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
		assertTrue(sql.contains("A VARCHAR(MAX)"), sql);
		assertTrue(sql.contains("B NVARCHAR(MAX)"), sql);
		assertTrue(sql.contains("C VARBINARY(MAX)"), sql);
		assertTrue(sql.contains("D VARCHAR(MAX)"), sql);
		assertTrue(sql.contains("E NVARCHAR(MAX)"), sql);
		assertTrue(sql.contains("F VARCHAR(8000)"), sql);
	}

	@Test
	void generatedKeyCaptureFindsValuesOutsideIdentifiers() {
		String sql = "INSERT INTO t ([VALUES], \"values\", a) VALUES (?, ?, ?)";
		assertEquals(sql.indexOf(") VALUES") + 2, SqlServer2005.findValuesClause(sql));
		sql = "INSERT INTO t /* VALUES */ -- VALUES\n DEFAULT VALUES";
		assertEquals(sql.indexOf("DEFAULT VALUES"), SqlServer2005.findValuesClause(sql));
		assertEquals(-1, SqlServer2005.findValuesClause("INSERT INTO t (MyValues) SELECT 'VALUES'"));
		final SqlServer2005 dialect = (SqlServer2005) dialect(9);
		final Table table = new Table("t");
		final Column id = new Column("ID").setDataType(DataType.INT);
		table.getColumns().add(id);
		final String handled = dialect.handleBatchExecuteGeneratedKeysSql(table, id,
				"INSERT INTO t ([VALUES]) VALUES (?)");
		assertTrue(handled.contains("([VALUES])  OUTPUT INSERTED."), handled);
		assertThrows(IllegalArgumentException.class,
				() -> dialect.handleBatchExecuteGeneratedKeysSql(table, id, "INSERT INTO t SELECT 1"));
	}
}
