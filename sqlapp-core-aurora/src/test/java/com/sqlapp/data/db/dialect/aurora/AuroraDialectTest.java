/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.aurora;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.*;
import com.sqlapp.data.db.dialect.aurora.resolver.AuroraDialectResolver;
import com.sqlapp.data.db.dialect.postgres.bulk.*;
import com.sqlapp.jdbc.bulk.*;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;

class AuroraDialectTest {
	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
	}
	private DatabaseMetaData metadata(String product, String version, int major, List<List<String>> results,
			List<String> queries, SQLException failure) {
		Connection connection = proxy(Connection.class, (p, m, a) -> {
			if (!m.getName().equals("createStatement")) { throw new AssertionError("Connection mutated: " + m.getName()); }
			return proxy(Statement.class, (s, method, args) -> {
				if (method.getName().equals("close")) { return null; }
				if (!method.getName().equals("executeQuery")) { throw new AssertionError(method.getName()); }
				queries.add((String) args[0]);
				if (failure != null) { throw failure; }
				var values = results.get(queries.size() - 1);
				int[] index = {-1};
				return proxy(ResultSet.class, (r, rm, ra) -> switch (rm.getName()) {
				case "next" -> ++index[0] < values.size();
				case "getString" -> values.get(index[0]);
				case "close" -> null;
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
	@Test void resolvesAliasesAndRejectsOtherProductsAndVersions() {
		var resolver = DialectResolver.getInstance();
		for (String alias : List.of("Aurora PostgreSQL", "Amazon Aurora PostgreSQL", "aurora-postgresql")) {
			for (int major = 14; major <= 17; major++) {
				assertInstanceOf(AuroraPostgreSQL.class, resolver.getDialect(alias, major, 0, null));
			}
		}
		var aurora = new AuroraDialectResolver();
		assertNull(aurora.getDialect("Aurora MySQL", 14, 0, null));
		assertNull(aurora.getDialect("Aurora DSQL", 14, 0, null));
		assertNull(aurora.getDialect("PostgreSQL", 14, 0, null));
		for (int major : new int[] {13,18,2026}) {
			assertThrows(IllegalArgumentException.class, () -> aurora.getDialect("Aurora PostgreSQL",major,0,null));
		}
	}
	@Test void identifiesAuroraUsingEngineMajorWithoutPollutingPostgresCache() {
		var resolver = DialectResolver.getInstance();
		var queries = new ArrayList<String>();
		assertInstanceOf(AuroraPostgres16.class, resolver.getDialect(metadata("PostgreSQL", "16.13",16,
				List.of(List.of("1"),List.of("pg_catalog"),List.of("16.13.1")),queries,null)));
		assertEquals(3,queries.size());
		assertEquals("SELECT pg_catalog.aurora_version()", queries.get(2));
		assertFalse(resolver.getDialect("PostgreSQL",16,13,null) instanceof AuroraPostgreSQL);
		queries.clear();
		assertNull(new AuroraDialectResolver().resolveDatabaseMetaData(metadata("PostgreSQL","16.13",16,
				List.of(List.of()),queries,null)));
		assertEquals(1,queries.size());
		assertFalse(queries.get(0).contains("aurora_version("));
	}
	@Test void skipsOtherFamiliesWithoutQueries() {
		for (String version : List.of("15.2-YB-2026.1", "CockroachDB CCL v25.4")) {
			var queries = new ArrayList<String>();
			assertNull(new AuroraDialectResolver().resolveDatabaseMetaData(metadata("PostgreSQL",version,15,List.of(),queries,null)));
			assertTrue(queries.isEmpty());
		}
	}
	@Test void surfacesProbeErrorsAndIncompleteAuroraIdentification() {
		var resolver = new AuroraDialectResolver();
		var cause = new SQLException("denied");
		assertSame(cause,assertThrows(IllegalStateException.class,()->resolver.resolveDatabaseMetaData(
				metadata("PostgreSQL","17",17,List.of(),new ArrayList<>(),cause))).getCause());
		assertThrows(IllegalStateException.class,()->resolver.resolveDatabaseMetaData(
				metadata("PostgreSQL","17",17,List.of(List.of("1"),List.of()),new ArrayList<>(),null)));
		assertThrows(IllegalStateException.class,()->resolver.resolveDatabaseMetaData(
				metadata("PostgreSQL","17",17,List.of(List.of("1"),List.of("public"),List.of("")),new ArrayList<>(),null)));
	}
	@Test void registersBulkServicesAndInheritsQuotedDdlAndMetadata() {
		for (Dialect dialect : List.of(new AuroraPostgres14(),new AuroraPostgres15(),new AuroraPostgres16(),new AuroraPostgres17())) {
			assertEquals("Aurora PostgreSQL",dialect.getProductName());
			assertNotNull(dialect.getCatalogReader());
			assertInstanceOf(PostgresBulkInsertExecutor.class,BulkInsertResolver.resolve(dialect));
			assertInstanceOf(PostgresBulkUpsertExecutor.class,BulkUpsertResolver.resolve(dialect));
			assertInstanceOf(PostgresSetBasedMigrationSnapshotExecutor.class,SetBasedMigrationSnapshotResolver.resolve(dialect));
			Table table = new Table("Order");
			table.getColumns().add("Id",c->c.setDataType(DataType.INT));
			table.setPrimaryKey("pk",table.getColumns().get("Id"));
			com.sqlapp.data.db.sql.SqlFactory<Table> create = dialect.createSqlFactoryRegistry().getSqlFactory(table,SqlType.CREATE);
			String sql = create.createSql(table).getFirst().getSqlText();
			assertTrue(sql.contains("CREATE TABLE"),sql);
			assertTrue(sql.contains("\"Order\""),sql);
			assertTrue(sql.contains("\"Id\""),sql);
		}
	}
	@Test void supportsPublicNamespaceAndExplicitOfflineIdentity() {
		var resolver = new AuroraDialectResolver();
		var queries = new ArrayList<String>();
		assertInstanceOf(AuroraPostgres14.class, resolver.resolveDatabaseMetaData(metadata("PostgreSQL", "14.22", 14,
				List.of(List.of("1"), List.of("public"), List.of("14.22.1")), queries, null)));
		assertEquals("SELECT public.aurora_version()", queries.getLast());
		queries.clear();
		assertInstanceOf(AuroraPostgres17.class, resolver.resolveDatabaseMetaData(metadata("Aurora PostgreSQL", "17.9.1", 17,
				List.of(), queries, null)));
		assertTrue(queries.isEmpty());
		assertNull(resolver.resolveDatabaseMetaData(metadata("MySQL", "8", 8, List.of(), queries, null)));
	}

	@Test void preservesPostgresMergeVersionBoundary() {
		Table table = new Table("target");
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		table.getColumns().add("value", c -> c.setDataType(DataType.LONGVARCHAR));
		table.setPrimaryKey("pk_target", table.getColumns().get("id"));
		com.sqlapp.data.db.sql.SqlFactory<Table> oldMerge = new AuroraPostgres14().createSqlFactoryRegistry().getSqlFactory(table, SqlType.MERGE);
		assertTrue(oldMerge.createSql(table).getFirst().getSqlText().contains("ON CONFLICT"));
		for (Dialect dialect : List.of(new AuroraPostgres15(), new AuroraPostgres16(), new AuroraPostgres17())) {
			com.sqlapp.data.db.sql.SqlFactory<Table> merge = dialect.createSqlFactoryRegistry().getSqlFactory(table, SqlType.MERGE);
			assertTrue(merge.createSql(table).getFirst().getSqlText().contains("MERGE INTO"));
		}
	}

}
