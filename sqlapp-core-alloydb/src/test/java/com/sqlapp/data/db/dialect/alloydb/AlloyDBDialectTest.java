/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.alloydb;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.alloydb.resolver.AlloyDBDialectResolver;
import com.sqlapp.data.db.dialect.postgres.bulk.*;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.*;

class AlloyDBDialectTest {
	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
	}

	private DatabaseMetaData metadata(String product, String version, int major, boolean marker,
			List<String> queries, List<String> closed, SQLException failure) {
		Connection connection = proxy(Connection.class, (p, m, a) -> {
			if (!m.getName().equals("createStatement")) {
				throw new AssertionError("Connection mutated: " + m.getName());
			}
			return proxy(Statement.class, (s, method, args) -> {
				if (method.getName().equals("close")) { closed.add("statement"); return null; }
				if (!method.getName().equals("executeQuery")) { throw new AssertionError(method.getName()); }
				queries.add((String) args[0]);
				if (failure != null) { throw failure; }
				return proxy(ResultSet.class, (r, rm, ra) -> switch (rm.getName()) {
				case "next" -> marker;
				case "close" -> { closed.add("result"); yield null; }
				default -> throw new AssertionError(rm.getName());
				});
			});
		});
		return proxy(DatabaseMetaData.class, (p, m, a) -> switch (m.getName()) {
		case "getDatabaseProductName" -> product;
		case "getDatabaseProductVersion" -> version;
		case "getDatabaseMajorVersion" -> major;
		case "getDatabaseMinorVersion" -> 0;
		case "getConnection" -> connection;
		default -> throw new AssertionError(m.getName());
		});
	}

	@Test void resolvesExplicitAliasesAndEngineBoundaries() {
		var resolver = DialectResolver.getInstance();
		for (String alias : List.of("AlloyDB", "Google AlloyDB", "AlloyDB for PostgreSQL", "AlloyDB Omni", "alloydb-omni")) {
			for (int major = 15; major <= 17; major++) {
				var dialect = resolver.getDialect(alias, major, 0, null);
				assertInstanceOf(AlloyDB.class, dialect);
				assertEquals("AlloyDB" + major, dialect.getClass().getSimpleName());
			}
		}
		var direct = new AlloyDBDialectResolver();
		for (String name : List.of("PostgreSQL", "Cloud SQL", "Aurora PostgreSQL", "AlloyDB Other")) {
			assertNull(direct.getDialect(name, 16, 0, null));
		}
		for (int major : new int[] { 14, 18, 2026 }) {
			assertThrows(IllegalArgumentException.class, () -> direct.getDialect("AlloyDB", major, 0, null));
		}
	}

	@Test void identifiesRegisteredFlagWithoutPollutingPostgresCacheAndClosesResources() {
		var resolver = DialectResolver.getInstance();
		var queries = new ArrayList<String>();
		var closed = new ArrayList<String>();
		assertInstanceOf(AlloyDB16.class, resolver.getDialect(metadata("PostgreSQL", "16.8", 16, true, queries, closed, null)));
		assertEquals(1, queries.size());
		assertTrue(queries.getFirst().contains("google_columnar_engine.enabled"));
		assertEquals(List.of("result", "statement"), closed);
		assertFalse(resolver.getDialect("PostgreSQL", 16, 8, null) instanceof AlloyDB);
		assertNull(new AlloyDBDialectResolver().resolveDatabaseMetaData(metadata("PostgreSQL", "16.8", 16,
				false, new ArrayList<>(), new ArrayList<>(), null)));
	}

	@Test void skipsOtherProductsAndKnownPostgresForksWithoutQueries() {
		var resolver = new AlloyDBDialectResolver();
		var queries = new ArrayList<String>();
		for (String version : List.of("15.2-YB-2026.1", "CockroachDB CCL v25.4")) {
			assertNull(resolver.resolveDatabaseMetaData(metadata("PostgreSQL", version, 15, true, queries, new ArrayList<>(), null)));
		}
		assertNull(resolver.resolveDatabaseMetaData(metadata("MySQL", "8", 8, true, queries, new ArrayList<>(), null)));
		assertInstanceOf(AlloyDB17.class, resolver.resolveDatabaseMetaData(metadata("AlloyDB Omni", "17.9.0", 17,
				false, queries, new ArrayList<>(), null)));
		assertTrue(queries.isEmpty());
	}

	@Test void propagatesIdentificationFailuresAndRejectsUnknownIdentifiedEngine() {
		var cause = new SQLException("catalog denied");
		var closed = new ArrayList<String>();
		var resolver = new AlloyDBDialectResolver();
		assertSame(cause, assertThrows(IllegalStateException.class, () -> resolver.resolveDatabaseMetaData(
				metadata("PostgreSQL", "16", 16, false, new ArrayList<>(), closed, cause))).getCause());
		assertEquals(List.of("statement"), closed);
		assertThrows(IllegalArgumentException.class, () -> resolver.resolveDatabaseMetaData(
				metadata("PostgreSQL", "19", 19, true, new ArrayList<>(), new ArrayList<>(), null)));
	}

	@Test void registersBulkAndSnapshotServicesWithoutClaimingOrdinaryPostgres() {
		for (Dialect dialect : List.of(new AlloyDB15(), new AlloyDB16(), new AlloyDB17())) {
			assertEquals("AlloyDB", dialect.getProductName());
			assertNotNull(dialect.getCatalogReader());
			assertInstanceOf(PostgresBulkInsertExecutor.class, BulkInsertResolver.resolve(dialect));
			assertInstanceOf(PostgresBulkUpsertExecutor.class, BulkUpsertResolver.resolve(dialect));
			assertInstanceOf(PostgresSetBasedMigrationSnapshotExecutor.class, SetBasedMigrationSnapshotResolver.resolve(dialect));
			assertFalse(new PostgresBulkInsertProvider().supports(dialect));
		}
	}

	@Test void inheritsQuotedDdlAndMerge() {
		Table table = new Table("Order");
		table.getColumns().add("Id", c -> c.setDataType(DataType.INT));
		table.getColumns().add("value", c -> c.setDataType(DataType.LONGVARCHAR));
		table.setPrimaryKey("pk_order", table.getColumns().get("Id"));
		for (Dialect dialect : List.of(new AlloyDB15(), new AlloyDB16(), new AlloyDB17())) {
			com.sqlapp.data.db.sql.SqlFactory<Table> create = dialect.createSqlFactoryRegistry().getSqlFactory(table, SqlType.CREATE);
			String sql = create.createSql(table).getFirst().getSqlText();
			assertTrue(sql.contains("\"Order\""), sql);
			assertTrue(sql.contains("\"Id\""), sql);
			com.sqlapp.data.db.sql.SqlFactory<Table> merge = dialect.createSqlFactoryRegistry().getSqlFactory(table, SqlType.MERGE);
			assertTrue(merge.createSql(table).getFirst().getSqlText().contains("MERGE INTO"));
		}
	}
	@Test void preservesPostgresNumericAndArrayTypeSemantics() {
		for (Dialect dialect : List.of(new AlloyDB15(), new AlloyDB16(), new AlloyDB17())) {
			var column = new com.sqlapp.data.schemas.Column("value");
			assertTrue(dialect.setDbType("numeric(2,-3)[]", null, null, column));
			assertEquals(2L, column.getLength());
			assertEquals(-3, column.getScale());
			assertEquals(1, column.getArrayDimension());
			var table = new Table("numeric_array");
			table.getColumns().add(column);
			com.sqlapp.data.db.sql.SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(table, SqlType.CREATE);
			String sql = factory.createSql(table).getFirst().getSqlText();
			assertTrue(sql.contains("NUMERIC(2,-3)[]"), sql);
		}
	}

}
