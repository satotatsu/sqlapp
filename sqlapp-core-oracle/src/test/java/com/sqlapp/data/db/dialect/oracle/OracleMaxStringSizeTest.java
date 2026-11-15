/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.LengthProperties;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.oracle.resolver.OracleDialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.CharacterSemantics;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.SqlappDataSource;
import com.sqlapp.util.CommonUtils;

class OracleMaxStringSizeTest {

	private static final List<Dialect> STANDARD = List.of(DialectHolder.oracle12cDialect,
			DialectHolder.oracle18cDialect, DialectHolder.oracle19cDialect, DialectHolder.oracle21cDialect,
			DialectHolder.oracle23aiDialect, DialectHolder.oracle26aiDialect);

	private static final List<Dialect> EXTENDED = List.of(DialectHolder.oracle12cExtendedDialect,
			DialectHolder.oracle18cExtendedDialect, DialectHolder.oracle19cExtendedDialect,
			DialectHolder.oracle21cExtendedDialect, DialectHolder.oracle23aiExtendedDialect,
			DialectHolder.oracle26aiExtendedDialect);

	@BeforeEach
	void clearCache() {
		OracleDialectResolver.clearDetectedMaxStringSizes();
	}

	@Test
	void standardIsTheDefaultAndExtendedRaisesTheLimits() {
		for (int i = 0; i < STANDARD.size(); i++) {
			Oracle standard = (Oracle) STANDARD.get(i);
			Oracle extended = (Oracle) EXTENDED.get(i);
			assertEquals(OracleMaxStringSize.STANDARD, standard.getMaxStringSize());
			assertEquals(OracleMaxStringSize.EXTENDED, extended.getMaxStringSize());
			assertEquals(4000L, maxLength(standard, DataType.VARCHAR, 4000));
			assertEquals(4000L, maxLength(standard, DataType.NVARCHAR, 4000));
			assertEquals(2000L, maxLength(standard, DataType.VARBINARY, 2000));
			assertNotEquals(DataType.VARCHAR,
					standard.getDbDataTypes().getDbType(DataType.VARCHAR, 4001L).getDataType());
			assertNotEquals(DataType.VARBINARY,
					standard.getDbDataTypes().getDbType(DataType.VARBINARY, 2001L).getDataType());
			assertEquals(32767L, maxLength(extended, DataType.VARCHAR, 32767));
			assertEquals(32767L, maxLength(extended, DataType.NVARCHAR, 32767));
			assertEquals(32767L, maxLength(extended, DataType.VARBINARY, 32767));
			assertEquals(2, extended.getDbDataTypes().getDbType(DataType.VARCHAR, 32767L).getSupportCharacterSemantics()
					.size());
			assertEquals(standard.getClass(), extended.getClass());
			assertNotEquals(standard, extended);
			assertNotEquals(standard.hashCode(), extended.hashCode());
			assertSame(extended, standard.withMaxStringSize(OracleMaxStringSize.EXTENDED));
			assertSame(standard, extended.withMaxStringSize(OracleMaxStringSize.STANDARD));
			assertSame(standard, standard.withMaxStringSize(OracleMaxStringSize.STANDARD));
		}
		for (Dialect dialect : List.of(DialectHolder.defaultDialect, DialectHolder.oracle10gDialect,
				DialectHolder.oracle11gDialect, DialectHolder.oracle11gR2Dialect)) {
			Oracle oracle = (Oracle) dialect;
			assertEquals(OracleMaxStringSize.STANDARD, oracle.getMaxStringSize());
			assertEquals(4000L, maxLength(oracle, DataType.VARCHAR, 4000));
			assertThrows(IllegalArgumentException.class, () -> oracle.withMaxStringSize(OracleMaxStringSize.EXTENDED));
		}
	}

	@Test
	void nextVersionStaysInTheSameMode() {
		assertSame(DialectHolder.oracle18cExtendedDialect,
				DialectHolder.oracle12cExtendedDialect.getNextVersionDialect());
		assertSame(DialectHolder.oracle26aiExtendedDialect,
				DialectHolder.oracle23aiExtendedDialect.getNextVersionDialect());
		assertSame(DialectHolder.oracle18cDialect, DialectHolder.oracle12cDialect.getNextVersionDialect());
	}

	@Test
	void ddlUsesVarchar2OnlyWithinTheLimit() {
		String extended = createTable(DialectHolder.oracle19cExtendedDialect, 5000);
		assertTrue(extended.contains("VARCHAR2(5000 CHAR)"), extended);
		String standard = createTable(DialectHolder.oracle19cDialect, 4000);
		assertTrue(standard.contains("VARCHAR2(4000 CHAR)"), standard);
		String tooLong = createTable(DialectHolder.oracle19cDialect, 5000);
		assertTrue(!tooLong.contains("VARCHAR2(5000"), tooLong);
	}

	@Test
	void detectsMaxStringSizeFromTheConnection() {
		AtomicInteger probes = new AtomicInteger();
		Dialect standard = DialectResolver.getInstance()
				.getDialect(
						metadata(19, "jdbc:oracle:thin:@//std/app",
								connection(probes,
										new SQLException("ORA-00910: specified length too long", "42000", 910))),
						Map.of());
		assertSame(DialectHolder.oracle19cDialect, standard);
		Dialect extended = DialectResolver.getInstance()
				.getDialect(metadata(23, "jdbc:oracle:thin:@//ext/app", connection(probes, null)), Map.of());
		assertSame(DialectHolder.oracle23aiExtendedDialect, extended);
		assertEquals(2, probes.get());
		// cached per JDBC URL
		DialectResolver.getInstance().getDialect(metadata(23, "jdbc:oracle:thin:@//ext/app", connection(probes, null)),
				Map.of());
		assertEquals(2, probes.get());
	}

	@Test
	void unexpectedProbeFailureIsReported() {
		AtomicInteger probes = new AtomicInteger();
		IllegalStateException e = assertThrows(IllegalStateException.class,
				() -> DialectResolver.getInstance().getDialect(
						metadata(19, "jdbc:oracle:thin:@//broken/app",
								connection(probes,
										new SQLException("ORA-01031: insufficient privileges", "42000", 1031))),
						Map.of()));
		assertTrue(e.getMessage().contains(OracleMaxStringSize.OPTION_NAME), e.getMessage());
	}

	@Test
	void explicitOptionOverridesDetection() {
		AtomicInteger probes = new AtomicInteger();
		Dialect dialect = DialectResolver.getInstance().getDialect(
				metadata(19, "jdbc:oracle:thin:@//ext2/app", connection(probes, null)),
				Map.of("oracle.maxStringSize", "standard"));
		assertSame(DialectHolder.oracle19cDialect, dialect);
		dialect = DialectResolver.getInstance().getDialect(metadata(19, "jdbc:oracle:thin:@//std2/app", null),
				Map.of("ORACLE.MAXSTRINGSIZE", "EXTENDED"));
		assertSame(DialectHolder.oracle19cExtendedDialect, dialect);
		assertEquals(0, probes.get());
	}

	@Test
	void noConnectionMeansStandard() {
		assertSame(DialectHolder.oracle21cDialect,
				DialectResolver.getInstance().getDialect(metadata(21, "jdbc:oracle:thin:@//none/app", null), Map.of()));
		assertSame(DialectHolder.oracle21cDialect,
				DialectResolver.getInstance().getDialect("Oracle", 21, 0, null, Map.of()));
		assertSame(DialectHolder.oracle21cExtendedDialect, DialectResolver.getInstance().getDialect("Oracle", 21, 0,
				null, Map.of(OracleMaxStringSize.OPTION_NAME, "EXTENDED")));
	}

	@Test
	void invalidOptionsAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> DialectResolver.getInstance()
				.getDialect(metadata(19, "jdbc:oracle:thin:@//x/app", null), Map.of("oracle.maxStringSize", "BIG")));
		assertThrows(IllegalArgumentException.class, () -> DialectResolver.getInstance()
				.getDialect(metadata(19, "jdbc:oracle:thin:@//x/app", null), Map.of("oracle.unknown", "1")));
		assertThrows(IllegalArgumentException.class,
				() -> DialectResolver.getInstance().getDialect(metadata(11, "jdbc:oracle:thin:@//x/app", null),
						Map.of("oracle.maxStringSize", "EXTENDED")));
		assertSame(DialectHolder.oracle11gDialect, DialectResolver.getInstance().getDialect(
				metadata(11, "jdbc:oracle:thin:@//x/app", null), Map.of("oracle.maxStringSize", "STANDARD")));
	}

	@Test
	void dataSourceOptionsReachDialectResolution() throws SQLException {
		AtomicInteger probes = new AtomicInteger();
		Connection raw = connection(probes, null);
		DatabaseMetaData metadata = metadata(19, "jdbc:oracle:thin:@//ds/app", raw);
		Connection rawWithMetadata = withMetadata(raw, metadata);
		DataSource dataSource = (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { DataSource.class }, (proxy, method, args) -> {
					if (method.getName().equals("getConnection")) {
						return rawWithMetadata;
					}
					throw new UnsupportedOperationException(method.getName());
				});
		SqlappDataSource sqlappDataSource = new SqlappDataSource(dataSource)
				.setDialectOptions(Map.of("oracle.maxStringSize", "STANDARD"));
		try (Connection connection = sqlappDataSource.getConnection()) {
			assertSame(DialectHolder.oracle19cDialect, DialectResolver.getInstance().getDialect(connection));
			assertSame(DialectHolder.oracle19cDialect,
					DialectResolver.getInstance().getDialect(connection.getMetaData()));
			try (Statement statement = connection.createStatement()) {
				assertSame(DialectHolder.oracle19cDialect,
						DialectResolver.getInstance().getDialect(statement.getConnection()));
			}
		}
		// nested wrappers keep the options
		try (Connection connection = new SqlappDataSource(sqlappDataSource).getConnection()) {
			assertSame(DialectHolder.oracle19cDialect, DialectResolver.getInstance().getDialect(connection));
		}
		assertEquals(0, probes.get());
		try (Connection connection = new SqlappDataSource(dataSource).getConnection()) {
			assertSame(DialectHolder.oracle19cExtendedDialect, DialectResolver.getInstance().getDialect(connection));
		}
		assertEquals(1, probes.get());
	}

	private static long maxLength(final Dialect dialect, final DataType dataType, final long length) {
		return ((LengthProperties<?>) dialect.getDbDataTypes().getDbType(dataType, length)).getMaxLength();
	}

	private static String createTable(final Dialect dialect, final long length) {
		Table table = new Table("T");
		table.getColumns().add(new Column("TEXT").setDataType(DataType.VARCHAR).setLength(length)
				.setCharacterSemantics(CharacterSemantics.Char));
		table.setDialect(dialect);
		final com.sqlapp.data.db.sql.SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(table,
				SqlType.CREATE);
		return CommonUtils.first(factory.createSql(table)).getSqlText().toUpperCase(Locale.ROOT);
	}

	private static Connection connection(final AtomicInteger probes, final SQLException failure) {
		return (Connection) Proxy.newProxyInstance(OracleMaxStringSizeTest.class.getClassLoader(),
				new Class<?>[] { Connection.class }, (proxy, method, args) -> {
					switch (method.getName()) {
					case "createStatement":
						return statement(probes, failure);
					case "close":
						return null;
					case "isClosed":
						return false;
					case "unwrap":
						return proxy;
					case "isWrapperFor":
						return false;
					case "hashCode":
						return System.identityHashCode(proxy);
					case "equals":
						return proxy == args[0];
					case "toString":
						return "connection";
					default:
						throw new UnsupportedOperationException(method.getName());
					}
				});
	}

	private static Connection withMetadata(final Connection connection, final DatabaseMetaData metadata) {
		return (Connection) Proxy.newProxyInstance(OracleMaxStringSizeTest.class.getClassLoader(),
				new Class<?>[] { Connection.class }, (proxy, method, args) -> {
					if (method.getName().equals("getMetaData")) {
						return metadata;
					}
					return method.invoke(connection, args);
				});
	}

	private static Statement statement(final AtomicInteger probes, final SQLException failure) {
		return (Statement) Proxy.newProxyInstance(OracleMaxStringSizeTest.class.getClassLoader(),
				new Class<?>[] { Statement.class }, (proxy, method, args) -> {
					switch (method.getName()) {
					case "executeQuery":
						probes.incrementAndGet();
						assertTrue(args[0].toString().contains("VARCHAR2(32767)"));
						if (failure != null) {
							throw failure;
						}
						return resultSet();
					case "close":
						return null;
					default:
						throw new UnsupportedOperationException(method.getName());
					}
				});
	}

	private static ResultSet resultSet() {
		return (ResultSet) Proxy.newProxyInstance(OracleMaxStringSizeTest.class.getClassLoader(),
				new Class<?>[] { ResultSet.class }, (proxy, method, args) -> {
					if (method.getName().equals("close")) {
						return null;
					}
					throw new UnsupportedOperationException(method.getName());
				});
	}

	private static DatabaseMetaData metadata(final int major, final String url, final Connection connection) {
		return (DatabaseMetaData) Proxy.newProxyInstance(OracleMaxStringSizeTest.class.getClassLoader(),
				new Class<?>[] { DatabaseMetaData.class }, (proxy, method, args) -> {
					switch (method.getName()) {
					case "getDatabaseProductName":
						return "Oracle";
					case "getDatabaseProductVersion":
						return "Oracle Database " + major + " Release " + major + ".0.0.0.0 - Production";
					case "getDatabaseMajorVersion":
						return major;
					case "getDatabaseMinorVersion":
						return 0;
					case "getURL":
						return url;
					case "getConnection":
						return connection;
					default:
						throw new UnsupportedOperationException(method.getName());
					}
				});
	}
}
