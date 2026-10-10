/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.yugabyte.resolver;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.postgres.Postgres150;
import com.sqlapp.data.db.dialect.yugabyte.Yugabyte11;
import com.sqlapp.data.db.dialect.yugabyte.Yugabyte15;
import com.sqlapp.data.db.dialect.postgres.bulk.PostgresBulkInsertProvider;
import com.sqlapp.data.db.dialect.postgres.bulk.PostgresBulkUpsertProvider;
import com.sqlapp.data.db.dialect.postgres.bulk.PostgresSetBasedMigrationSnapshotProvider;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

class YugabyteDialectResolverTest {
	@Test
	void separatesProcedureAlterCapabilityByYsqlEngine() {
		var procedure = new com.sqlapp.data.schemas.Function("p");
		procedure.getSpecifics().put("ROUTINE_KIND", "PROCEDURE");
		var target = procedure.clone().setSqlSecurity(com.sqlapp.data.schemas.SqlSecurity.Definer);
		assertThrows(UnsupportedOperationException.class,
				() -> new Yugabyte11().createSqlFactoryRegistry().createSql(procedure.diff(target)));
		assertTrue(new Yugabyte15().createSqlFactoryRegistry().createSql(procedure.diff(target)).get(0).getSqlText()
				.startsWith("ALTER PROCEDURE "));
		assertTrue(new Yugabyte11().createSqlFactoryRegistry()
				.createSql(procedure.diff(procedure.clone().setRemarks("comment"))).get(0).getSqlText()
				.startsWith("COMMENT ON PROCEDURE "));
	}

	private DatabaseMetaData metadata(String name, String version, int major) {
		return (DatabaseMetaData) Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(),
				new Class<?>[] { DatabaseMetaData.class }, (proxy, method, args) -> switch (method.getName()) {
				case "getDatabaseProductName" -> name;
				case "getDatabaseProductVersion" -> version;
				case "getDatabaseMajorVersion" -> major;
				case "getDatabaseMinorVersion" -> 2;
				default -> throw new UnsupportedOperationException(method.getName());
				});
	}

	@Test
	void identifiesYsqlWithoutContaminatingPostgresNameCache() {
		var resolver = DialectResolver.getInstance();
		assertEquals(Postgres150.class, resolver.getDialect("PostgreSQL", 15, 2, null).getClass());
		assertInstanceOf(Yugabyte11.class, resolver.getDialect(metadata("PostgreSQL", "11.2-YB-2.20.1.1-b0", 11)));
		assertInstanceOf(Yugabyte15.class, resolver.getDialect(metadata("PostgreSQL", "15.2-YB-2025.1.0.0-b0", 15)));
		assertEquals(Postgres150.class, resolver.getDialect(metadata("PostgreSQL", "15.2", 15)).getClass());
		assertInstanceOf(Yugabyte11.class, resolver.getDialect(metadata("PostgreSQL", "11.2-YB-2024.2.0.0-b0", 11)));
	}

	@Test
	void rejectsUnknownEngineAndAmbiguousProductReleases() {
		var resolver = new YugabyteDialectResolver();
		assertInstanceOf(Yugabyte15.class, resolver.getDialect("YugabyteDB", 15, 2, null));
		assertInstanceOf(Yugabyte11.class, resolver.getDialect("YSQL", 11, 2, null));
		assertThrows(IllegalArgumentException.class, () -> resolver.getDialect("YugabyteDB", 2025, 1, null));
		assertThrows(IllegalArgumentException.class, () -> resolver.getDialect("YugabyteDB", 16, 0, null));
		assertThrows(IllegalArgumentException.class,
				() -> resolver.resolveDatabaseMetaData(metadata("PostgreSQL", "16.1-YB-2027.1.0.0-b0", 16)));
		assertThrows(IllegalArgumentException.class,
				() -> resolver.resolveDatabaseMetaData(metadata("YugabyteDB", "2025.1.0.0", 2025)));
		assertNull(resolver.resolveDatabaseMetaData(metadata("OtherDB", "15.2-YB-fake", 15)));
		assertNull(resolver.getDialect("PostgreSQL", 15, 2, null));
	}

	@Test
	void surfacesMetadataFailure() {
		DatabaseMetaData broken = (DatabaseMetaData) Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(),
				new Class<?>[] { DatabaseMetaData.class }, (proxy, method, args) -> {
					throw new SQLException("broken");
				});
		assertInstanceOf(SQLException.class, assertThrows(IllegalStateException.class,
				() -> new YugabyteDialectResolver().resolveDatabaseMetaData(broken)).getCause());
	}

	@Test
	void reusesSqlAndMetadataWithoutEnablingNativePostgresBulkProviders() {
		for (var dialect : java.util.List.of(new Yugabyte11(), new Yugabyte15())) {
			assertEquals("YugabyteDB", dialect.getProductName());
			assertNotNull(dialect.getCatalogReader());
			assertInstanceOf(com.sqlapp.data.db.dialect.yugabyte.bulk.YugabyteBulkInsertExecutor.class,
					com.sqlapp.jdbc.bulk.BulkInsertResolver.resolve(dialect));
			assertInstanceOf(com.sqlapp.data.db.dialect.postgres.bulk.PostgresBulkUpsertExecutor.class,
					com.sqlapp.jdbc.bulk.BulkUpsertResolver.resolve(dialect));
			assertTrue(dialect.getCatalogReader().getSchemaReader()
					.getSequenceReader() instanceof com.sqlapp.data.db.dialect.yugabyte.metadata.YugabyteSequenceReader);
			assertFalse(new PostgresBulkInsertProvider().supports(dialect));
			assertFalse(new PostgresBulkUpsertProvider().supports(dialect));
			assertFalse(new PostgresSetBasedMigrationSnapshotProvider().supports(dialect));
			Table table = new Table("Order");
			table.getColumns().add("Id", c -> c.setDataType(DataType.INT));
			table.getColumns().add("label", c -> c.setDataType(DataType.VARCHAR).setLength(20));
			table.setPrimaryKey("pk_order", table.getColumns().get("Id"));
			var registry = dialect.createSqlFactoryRegistry();
			com.sqlapp.data.db.sql.SqlFactory<Table> factory = registry.getSqlFactory(table, SqlType.CREATE);
			String sql = factory.createSql(table).get(0).getSqlText();
			assertTrue(sql.contains("CREATE TABLE"), sql);
			assertTrue(sql.contains("Order"), sql);
			assertTrue(sql.contains("Id"), sql);
			com.sqlapp.data.db.sql.SqlFactory<Table> merge = registry.getSqlFactory(table, SqlType.MERGE);
			assertTrue(merge.createSql(table).get(0).getSqlText().contains("ON CONFLICT"));
			table.getConstraints().clear();
			assertThrows(IllegalArgumentException.class, () -> merge.createSql(table));
		}
	}
}
