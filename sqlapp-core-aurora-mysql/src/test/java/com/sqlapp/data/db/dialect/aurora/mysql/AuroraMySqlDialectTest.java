package com.sqlapp.data.db.dialect.aurora.mysql;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.*;
import com.sqlapp.data.db.dialect.aurora.mysql.resolver.AuroraMySqlDialectResolver;
import com.sqlapp.data.db.dialect.mysql.bulk.*;
import com.sqlapp.jdbc.bulk.*;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;

class AuroraMySqlDialectTest {
	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
	}

	private DatabaseMetaData metadata(String product, int minor, String release, List<String> queries,
			SQLException failure) {
		Connection connection = proxy(Connection.class, (p, m, a) -> {
			if (!m.getName().equals("createStatement"))
				throw new AssertionError("Connection mutated: " + m.getName());
			return proxy(Statement.class, (s, method, args) -> {
				if (method.getName().equals("close"))
					return null;
				if (!method.getName().equals("executeQuery"))
					throw new AssertionError(method.getName());
				queries.add((String) args[0]);
				if (failure != null)
					throw failure;
				int[] index = { 0 };
				return proxy(ResultSet.class, (r, rm, ra) -> switch (rm.getName()) {
				case "next" -> release != null && index[0]++ == 0;
				case "getString" -> {
					assertEquals(2, ra[0]);
					yield "<null>".equals(release) ? null : release;
				}
				case "close" -> null;
				default -> throw new AssertionError(rm.getName());
				});
			});
		});
		return proxy(DatabaseMetaData.class, (p, m, a) -> switch (m.getName()) {
		case "getDatabaseProductName" -> product;
		case "getDatabaseProductVersion" -> "8." + minor + ".32";
		case "getDatabaseMajorVersion" -> 8;
		case "getDatabaseMinorVersion" -> minor;
		case "getConnection" -> connection;
		default -> throw new AssertionError(m.getName());
		});
	}

	@Test
	void resolvesAliasesAndVersionBoundaries() {
		var resolver = DialectResolver.getInstance();
		for (String alias : List.of("Aurora MySQL", "Amazon Aurora MySQL", "aurora-mysql")) {
			assertInstanceOf(AuroraMySql80.class, resolver.getDialect(alias, 8, 0, null));
			assertInstanceOf(AuroraMySql84.class, resolver.getDialect(alias, 8, 4, null));
		}
		var aurora = new AuroraMySqlDialectResolver();
		for (int[] version : new int[][] { { 3, 0 }, { 5, 7 }, { 8, 1 }, { 8, 5 }, { 9, 0 } }) {
			assertThrows(IllegalArgumentException.class,
					() -> aurora.getDialect("Aurora MySQL", version[0], version[1], null));
		}
		assertNull(aurora.getDialect("Aurora PostgreSQL", 8, 0, null));
		assertNull(aurora.getDialect("Aurora DSQL", 8, 0, null));
	}

	@Test
	void identifiesConnectionWithoutPollutingMySqlCache() {
		var queries = new ArrayList<String>();
		var resolver = DialectResolver.getInstance();
		assertInstanceOf(AuroraMySql80.class, resolver.getDialect(metadata("MySQL", 0, "3.05.2", queries, null)));
		assertEquals(List.of("SHOW VARIABLES WHERE Variable_name = 'aurora_version'"), queries);
		assertFalse(resolver.getDialect("MySQL", 8, 0, null) instanceof AuroraMySQL);
		queries.clear();
		assertInstanceOf(AuroraMySql84.class, resolver.getDialect(metadata("MySQL", 4, "8.4.7", queries, null)));
		assertEquals(1, queries.size());
	}

	@Test
	void preservesFallbackAndExplicitIdentity() {
		var resolver = new AuroraMySqlDialectResolver();
		var queries = new ArrayList<String>();
		assertNull(resolver.resolveDatabaseMetaData(metadata("MySQL", 0, null, queries, null)));
		assertEquals(1, queries.size());
		queries.clear();
		assertNull(resolver.resolveDatabaseMetaData(metadata("MariaDB", 0, "3.05.2", queries, null)));
		assertInstanceOf(AuroraMySql84.class,
				resolver.resolveDatabaseMetaData(metadata("Aurora MySQL", 4, null, queries, null)));
		assertTrue(queries.isEmpty());
	}

	@Test
	void propagatesFailuresAndRejectsInconsistentReleases() {
		var resolver = new AuroraMySqlDialectResolver();
		var cause = new SQLException("denied");
		assertSame(cause,
				assertThrows(IllegalStateException.class,
						() -> resolver.resolveDatabaseMetaData(metadata("MySQL", 0, null, new ArrayList<>(), cause)))
						.getCause());
		for (String release : List.of("", "<null>", "2.11.2", "8.4.7", "garbage")) {
			assertThrows(IllegalStateException.class,
					() -> resolver.resolveDatabaseMetaData(metadata("MySQL", 0, release, new ArrayList<>(), null)));
		}
		assertThrows(IllegalStateException.class,
				() -> resolver.resolveDatabaseMetaData(metadata("MySQL", 4, "3.05.2", new ArrayList<>(), null)));
	}

	@Test
	void rejectsMissingConnectionInsteadOfGuessing() {
		DatabaseMetaData metadata = proxy(DatabaseMetaData.class, (p, m, a) -> switch (m.getName()) {
		case "getDatabaseProductName" -> "MySQL";
		case "getConnection" -> null;
		default -> throw new AssertionError(m.getName());
		});
		assertThrows(IllegalStateException.class,
				() -> new AuroraMySqlDialectResolver().resolveDatabaseMetaData(metadata));
	}

	@Test
	void inheritsMetadataQuotedDdlAndBulkServices() {
		for (Dialect dialect : List.of(new AuroraMySql80(), new AuroraMySql84())) {
			assertEquals("Aurora MySQL", dialect.getProductName());
			assertEquals("aurora-mysql", dialect.getSimpleName());
			assertNotNull(dialect.getCatalogReader());
			assertInstanceOf(MySqlBulkInsertExecutor.class, BulkInsertResolver.resolve(dialect));
			assertInstanceOf(MySqlBulkUpsertExecutor.class, BulkUpsertResolver.resolve(dialect));
			assertInstanceOf(MySqlSetBasedMigrationSnapshotExecutor.class,
					SetBasedMigrationSnapshotResolver.resolve(dialect));
			Table table = new Table("Order");
			table.getColumns().add("Id", c -> c.setDataType(DataType.INT));
			table.setPrimaryKey("pk", table.getColumns().get("Id"));
			com.sqlapp.data.db.sql.SqlFactory<Table> create = dialect.createSqlFactoryRegistry().getSqlFactory(table,
					SqlType.CREATE);
			String sql = create.createSql(table).getFirst().getSqlText();
			assertTrue(sql.contains("CREATE TABLE"), sql);
			assertTrue(sql.contains("`Order`"), sql);
			assertTrue(sql.contains("`Id`"), sql);
		}
	}
}
