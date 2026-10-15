/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.edb.oracle;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.edb.oracle.EdbOracle;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.db.dialect.test.postgres.PostgresMetadataRegressionAssertions;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.*;

/**
 * Upstream PostgreSQL control only; does not emulate EPAS or Oracle
 * compatibility.
 */
class EdbOraclePostgresCompatibilityTest {
	private static final PostgreSQLContainer POSTGRES = ReusableTestcontainers
			.configure(new PostgreSQLContainer(System.getProperty("sqlapp.test.edb.postgresImage", "postgres:17.9")));

	@BeforeAll
	static void start() {
		ReusableTestcontainers.start(POSTGRES);
	}

	@AfterAll
	static void stop() {
		ReusableTestcontainers.stop(POSTGRES);
	}

	@Test
	void readsAndRecreatesInheritedSequenceAndIndexMetadata() throws Exception {
		try (var connection = POSTGRES.createConnection("")) {
			var dialect = DialectResolver.getInstance().getDialect("edb-oracle",
					connection.getMetaData().getDatabaseMajorVersion(), 0, null);
			PostgresMetadataRegressionAssertions.verify(connection, dialect);
		}
	}

	@Test
	void preservesCallerTransactionWhenIdentifyingOrdinaryPostgres() throws Exception {
		try (var connection = POSTGRES.createConnection("")) {
			connection.setAutoCommit(false);
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE identification_transaction(id integer)");
				assertFalse(DialectResolver.getInstance().getDialect(connection) instanceof EdbOracle);
				assertFalse(connection.getAutoCommit());
				statement.execute("INSERT INTO identification_transaction VALUES (1)");
				connection.rollback();
				try (var rows = statement.executeQuery("SELECT to_regclass('identification_transaction')")) {
					rows.next();
					assertNull(rows.getString(1));
				}
				connection.rollback();
			}
		}
	}

	@Test
	void copiesRowsAndRollsBackUpsertWithTheCaller() throws Exception {
		try (var connection = POSTGRES.createConnection(""); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE edb_copy_control(id integer PRIMARY KEY, value text)");
			try {
				var dialect = DialectResolver.getInstance().getDialect("edb-oracle",
						connection.getMetaData().getDatabaseMajorVersion(), 0, null);
				var table = new Table("edb_copy_control");
				table.getColumns().add("id", c -> c.setDataType(DataType.INT));
				table.getColumns().add("value", c -> c.setDataType(DataType.LONGVARCHAR));
				table.setPrimaryKey("pk_control", table.getColumns().get("id"));
				table.getRows().add(r -> {
					r.put("id", 1);
					r.put("value", "comma,quote\"\n日本語");
				});
				assertEquals(1, BulkInsertResolver.resolve(dialect).execute(connection, table, BulkOption.defaults()));
				table.getRows().clear();
				table.getRows().add(r -> {
					r.put("id", 1);
					r.put("value", "updated");
				});
				table.getRows().add(r -> {
					r.put("id", 2);
					r.put("value", null);
				});
				connection.setAutoCommit(false);
				BulkUpsertResolver.resolve(dialect).execute(connection, table, BulkUpsertOption.defaults());
				try (var rows = statement.executeQuery("SELECT count(*) FROM edb_copy_control")) {
					rows.next();
					assertEquals(2, rows.getInt(1));
				}
				connection.rollback();
				try (var rows = statement.executeQuery("SELECT value FROM edb_copy_control")) {
					rows.next();
					assertEquals("comma,quote\"\n日本語", rows.getString(1));
					assertFalse(rows.next());
				}
				connection.rollback();
				connection.setAutoCommit(true);
			} finally {
				if (!connection.getAutoCommit())
					connection.rollback();
				connection.setAutoCommit(true);
				statement.execute("DROP TABLE edb_copy_control");
			}
		}
	}

	@Test
	void readsSimulatedOracleCatalogsWithBoundedQueries() throws Exception {
		try (var connection = POSTGRES.createConnection(""); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA sys");
			try {
				statement.execute(
						"""
								CREATE VIEW sys.all_source AS
								SELECT 'public'::text schema_name,'p'::text name,'PACKAGE'::text type,1 line,'PACKAGE p AS'::text text
								UNION ALL SELECT 'public','p','PACKAGE',2,'PROCEDURE run;'
								UNION ALL SELECT 'public','p','PACKAGE',3,'END;'
								UNION ALL SELECT 'public','p','PACKAGE BODY',1,'PACKAGE BODY p AS'
								UNION ALL SELECT 'public','p','PACKAGE BODY',2,'PROCEDURE run IS BEGIN NULL; END;'
								UNION ALL SELECT 'public','p','PACKAGE BODY',3,'END;'
								""");
				statement.execute("""
						CREATE VIEW sys.all_synonyms AS
						SELECT * FROM (VALUES
						 ('role_owner','public','local_alias','target_role','target.schema','Order',NULL::text),
						 ('role_owner','public','remote_alias','target_role','target.schema','Order','Remote.Link'),
						 ('PUBLIC','public','public_alias','target_role','target.schema','Order',NULL::text)
						) AS s(owner,schema_name,synonym_name,table_owner,table_schema_name,table_name,db_link)
						""");
				statement.execute("""
						CREATE VIEW sys.all_db_links AS
						SELECT * FROM (VALUES
						 ('public','private_link','EDB','remote_user','host=localhost dbname=example'),
						 ('PUBLIC','public_link','REDWOOD','remote_user','//localhost/example')
						) AS l(owner,db_link,type,username,host)
						""");
				var dialect = DialectResolver.getInstance().getDialect("edb-oracle",
						connection.getMetaData().getDatabaseMajorVersion(), 0, null);
				var catalog = dialect.getCatalogReader();
				var schema = catalog.getSchemaReader();
				schema.setSchemaName("public");
				var count = new java.util.concurrent.atomic.AtomicInteger();
				var counted = com.sqlapp.data.db.dialect.test.CountingJdbc.wrap(connection,
						sql -> sql.contains("pg_catalog.pg_class") || sql.contains(".all_source")
								|| sql.contains(".all_synonyms") || sql.contains(".all_db_links"),
						count);
				var packages = schema.getPackageReader().getAll(counted);
				assertEquals(1, packages.size());
				assertEquals(2, count.get());
				assertTrue(String.join("\n", packages.getFirst().getDefinition()).contains("PROCEDURE run;"));
				count.set(0);
				assertEquals(1, schema.getPackageBodyReader().getAll(counted).size());
				assertEquals(2, count.get());
				count.set(0);
				var synonyms = schema.getSynonymReader().getAll(counted);
				assertEquals(2, synonyms.size());
				assertEquals(2, count.get());
				assertEquals("target.schema", synonyms.getFirst().getObjectSchemaName());
				assertTrue(synonyms.stream().anyMatch(x -> "Remote.Link".equals(x.getDbLinkName())));
				count.set(0);
				var links = schema.getDbLinkReader().getAll(counted);
				assertEquals(1, links.size());
				assertEquals(2, count.get());
				assertNull(links.getFirst().getPassword());
				count.set(0);
				assertEquals(1, catalog.getPublicSynonymReader().getAll(counted).size());
				assertEquals(2, count.get());
				count.set(0);
				assertEquals(1, catalog.getPublicDbLinkReader().getAll(counted).size());
				assertEquals(2, count.get());
			} finally {
				statement.execute("DROP SCHEMA sys CASCADE");
			}
		}
	}

	@Test
	void readsAndRecreatesStandalonePostgresRoutineWithVendorCatalogFilter() throws Exception {
		try (var connection = POSTGRES.createConnection(""); var statement = connection.createStatement()) {
			statement.execute(
					"CREATE FUNCTION public.edb_control_routine(n integer) RETURNS integer LANGUAGE sql AS 'SELECT n+1'");
			try {
				var dialect = DialectResolver.getInstance().getDialect("edb-oracle",
						connection.getMetaData().getDatabaseMajorVersion(), 0, null);
				var reader = dialect.getCatalogReader().getSchemaReader().getFunctionReader();
				reader.setSchemaName("public");
				reader.setObjectName("edb_control_routine");
				var functions = reader.getAllFull(connection);
				assertEquals(1, functions.size());
				var function = functions.getFirst();
				statement.execute("DROP FUNCTION public.edb_control_routine(integer)");
				for (var sql : dialect.createSqlFactoryRegistry().createSql(function,
						com.sqlapp.data.db.sql.SqlType.CREATE))
					statement.execute(sql.getSqlText());
				try (var rows = statement.executeQuery("SELECT public.edb_control_routine(1)")) {
					rows.next();
					assertEquals(2, rows.getInt(1));
				}
			} finally {
				statement.execute("DROP FUNCTION IF EXISTS public.edb_control_routine(integer)");
			}
		}
	}

}
