/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.*;
import com.sqlapp.data.db.dialect.edb.oracle.resolver.EdbOracleDialectResolver;
import com.sqlapp.data.db.dialect.postgres.bulk.*;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.*;

class EdbOracleDialectTest {
	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> api, java.lang.reflect.InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] {api}, handler);
	}
	private DatabaseMetaData metadata(String product, String version, int major, String banner, List<String> queries, SQLException failure) {
		var connection = proxy(Connection.class, (p,m,a) -> {
			if (!m.getName().equals("createStatement")) throw new AssertionError("Connection mutated: " + m.getName());
			return proxy(Statement.class, (s,method,args) -> {
				if (method.getName().equals("close")) return null;
				if (!method.getName().equals("executeQuery")) throw new AssertionError(method.getName());
				queries.add((String) args[0]);
				if (failure != null) throw failure;
				boolean[] next = {true};
				return proxy(ResultSet.class, (r,rm,ra) -> switch(rm.getName()) {
				case "next" -> {boolean value = next[0]; next[0] = false; yield value;}
				case "getString" -> banner;
				case "close" -> null;
				default -> throw new AssertionError(rm.getName());
				});
			});
		});
		return proxy(DatabaseMetaData.class, (p,m,a) -> switch(m.getName()) {
		case "getDatabaseProductName" -> product;
		case "getDatabaseProductVersion" -> version;
		case "getDatabaseMajorVersion" -> major;
		case "getDatabaseMinorVersion" -> 0;
		case "getConnection" -> connection;
		default -> throw new AssertionError(m.getName());
		});
	}
	@Test void resolvesAllSupportedEngineVersionsThroughServiceLoader() {
		var resolver = DialectResolver.getInstance();
		for (String product : List.of("EDB Postgres Advanced Server", "EnterpriseDB", "EPAS", "edb-oracle")) {
			for (int major = 14; major <= 18; major++) {
				var dialect = resolver.getDialect(product, major, 0, null);
				assertInstanceOf(EdbOracle.class, dialect);
				assertEquals("EdbOracle" + major, dialect.getClass().getSimpleName());
				assertEquals("edb-oracle", dialect.getSimpleName());
				assertNotNull(dialect.getCatalogReader().getSchemaReader());
				assertInstanceOf(com.sqlapp.data.db.dialect.edb.oracle.metadata.EdbOracleFunctionReader.class,
						dialect.getCatalogReader().getSchemaReader().getFunctionReader());
			}
		}
	}
	@Test void rejectsOtherEdbProductsAndUnsupportedVersions() {
		var resolver = new EdbOracleDialectResolver();
		for (String product : List.of("PostgreSQL", "EDB Postgres Extended Server", "EDB", "Oracle", "EDB Postgres Distributed")) {
			assertNull(resolver.getDialect(product, 17, 0, null));
		}
		for (int version : new int[] {13,19,2026}) {
			assertThrows(IllegalArgumentException.class, () -> resolver.getDialect("edb-oracle", version, 0, null));
		}
	}
	@Test void identifiesBothDriverNamesWithoutMutatingConnections() {
		var resolver = new EdbOracleDialectResolver();
		var queries = new ArrayList<String>();
		assertInstanceOf(EdbOracle17.class, resolver.resolveDatabaseMetaData(metadata("EnterpriseDB", "17.6",17,null,queries,null)));
		assertTrue(queries.isEmpty());
		assertInstanceOf(EdbOracle16.class, resolver.resolveDatabaseMetaData(metadata("PostgreSQL", "16.10 (EnterpriseDB 16.10)",16,null,queries,null)));
		assertTrue(queries.isEmpty());
		assertInstanceOf(EdbOracle18.class, resolver.resolveDatabaseMetaData(metadata("PostgreSQL", "18.1",18,"EnterpriseDB 18.1.0 on x86_64",queries,null)));
		assertEquals(List.of("SELECT pg_catalog.version()"), queries);
	}
	@Test void doesNotClaimOtherPostgresProductsOrPolluteOfflineCache() {
		var resolver = new EdbOracleDialectResolver();
		for (String banner : List.of("PostgreSQL 17.6 on Linux", "EDB Postgres Extended Server 17.6", "PostgreSQL 17.6 by EnterpriseDB")) {
			var queries = new ArrayList<String>();
			assertNull(resolver.resolveDatabaseMetaData(metadata("PostgreSQL","17.6",17,banner,queries,null)));
			assertEquals(1,queries.size());
		}
		assertFalse(DialectResolver.getInstance().getDialect("PostgreSQL",17,0,null) instanceof EdbOracle);
		for (String banner : List.of("15.2-YB-2026.1", "CockroachDB CCL v25.4")) {
			var queries = new ArrayList<String>();
			assertNull(resolver.resolveDatabaseMetaData(metadata("PostgreSQL",banner,15,null,queries,null)));
			assertTrue(queries.isEmpty());
		}
	}
	@Test void propagatesProbeFailuresWithTheOriginalCause() {
		var failure = new SQLException("connection failure","08006");
		var e = assertThrows(IllegalStateException.class, () -> new EdbOracleDialectResolver().resolveDatabaseMetaData(
				metadata("PostgreSQL","17",17,null,new ArrayList<>(),failure)));
		assertSame(failure,e.getCause());
		assertThrows(IllegalStateException.class, () -> new EdbOracleDialectResolver().resolveDatabaseMetaData(
				metadata("PostgreSQL","17",17,"",new ArrayList<>(),null)));
	}
	@Test void reusesBulkProvidersAndVersionAppropriateMerge() {
		var table = new Table("target");
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		table.getColumns().add("value", c -> c.setDataType(DataType.LONGVARCHAR));
		table.setPrimaryKey("pk_target", table.getColumns().get("id"));
		for (int major = 14; major <= 18; major++) {
			var dialect = DialectResolver.getInstance().getDialect("edb-oracle",major,0,null);
			assertInstanceOf(PostgresBulkInsertExecutor.class, BulkInsertResolver.resolve(dialect));
			assertInstanceOf(PostgresBulkUpsertExecutor.class, BulkUpsertResolver.resolve(dialect));
			assertInstanceOf(PostgresSetBasedMigrationSnapshotExecutor.class, SetBasedMigrationSnapshotResolver.resolve(dialect));
			var sql = dialect.createSqlFactoryRegistry().createSql(table,SqlType.MERGE).getFirst().getSqlText();
			assertTrue(sql.contains(major == 14 ? "ON CONFLICT" : "MERGE INTO"),sql);
		}
	}
}
