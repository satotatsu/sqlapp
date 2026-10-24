/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.db2;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.sql.Connection;
import java.util.List;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

/**
 * Disposable-container checks, never run against an external database by unit
 * tests.
 */
final class Db2RecreationAssertions {
	private static final String SCHEMA = "SQLAPP_PARITY";

	private Db2RecreationAssertions() {
	}

	static void verify(Connection connection) throws Exception {
		var dialect = DialectResolver.getInstance().getDialect(connection);
		try (var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + SCHEMA);
			try {
				statement.execute("CREATE TABLE " + SCHEMA + ".T (ID INTEGER NOT NULL, PAYLOAD INTEGER)");
				statement.execute(
						"CREATE UNIQUE INDEX " + SCHEMA + ".IX ON " + SCHEMA + ".T (ID DESC) INCLUDE (PAYLOAD)");
				statement.execute("COMMENT ON TABLE " + SCHEMA + ".T IS 'Parity table'");
				statement.execute("COMMENT ON COLUMN " + SCHEMA + ".T.PAYLOAD IS 'Payload'");
				statement.execute("CREATE VIEW " + SCHEMA + ".V AS SELECT ID, PAYLOAD FROM " + SCHEMA + ".T");
				statement.execute("CREATE ALIAS " + SCHEMA + ".A FOR TABLE " + SCHEMA + ".T");
				statement.execute("CREATE FUNCTION " + SCHEMA + ".F() RETURNS INTEGER LANGUAGE SQL RETURN 1");
				statement.execute("CREATE PROCEDURE " + SCHEMA + ".P(OUT N INTEGER) LANGUAGE SQL BEGIN SET N=1; END");
				statement.execute("GRANT SELECT ON TABLE " + SCHEMA + ".T TO PUBLIC");
				statement.execute("GRANT UPDATE (PAYLOAD) ON TABLE " + SCHEMA + ".T TO PUBLIC");
				statement.execute("GRANT EXECUTE ON FUNCTION " + SCHEMA + ".F TO PUBLIC");
				statement.execute("CREATE SEQUENCE " + SCHEMA + ".IX AS BIGINT START WITH 41 INCREMENT BY 3");
				statement.execute("GRANT USAGE ON SEQUENCE " + SCHEMA + ".IX TO PUBLIC");
				statement.execute("GRANT CONTROL ON INDEX " + SCHEMA + ".IX TO PUBLIC");
				statement.execute("GRANT ALTERIN ON SCHEMA " + SCHEMA + " TO PUBLIC");
				if (dialect instanceof com.sqlapp.data.db.dialect.db2.Db2_1155)
					statement.execute("GRANT SELECTIN ON SCHEMA " + SCHEMA + " TO PUBLIC");
				statement.execute("CREATE TABLE " + SCHEMA + ".MQ_BASE (ID INTEGER NOT NULL)");
				statement.execute("CREATE UNIQUE INDEX " + SCHEMA + ".IX_DIFFERENT ON " + SCHEMA + ".MQ_BASE(ID)");
				statement.execute("ALTER TABLE " + SCHEMA + ".MQ_BASE ADD CONSTRAINT UK_BASE UNIQUE(ID)");
				statement.execute("CREATE TABLE " + SCHEMA + ".MQ AS (SELECT ID FROM " + SCHEMA
						+ ".MQ_BASE) DATA INITIALLY DEFERRED REFRESH DEFERRED");
				statement.execute("CREATE PERMISSION " + SCHEMA + ".ALLOW_POSITIVE ON " + SCHEMA
						+ ".T AS R FOR ROWS WHERE R.ID > 0 ENFORCED FOR ALL ACCESS ENABLE");
				statement.execute("CREATE MASK " + SCHEMA + ".HIDE_PAYLOAD ON " + SCHEMA
						+ ".T FOR COLUMN PAYLOAD RETURN CASE WHEN ID=1 THEN 0 ELSE PAYLOAD END ENABLE");
				statement.execute("ALTER TABLE " + SCHEMA + ".T ACTIVATE ROW ACCESS CONTROL");
				statement.execute("ALTER TABLE " + SCHEMA + ".T ACTIVATE COLUMN ACCESS CONTROL");

				var reader = dialect.getCatalogReader().getSchemaReader();
				reader.setSchemaName(SCHEMA);
				Schema original = reader.getAllFull(connection).stream().filter(s -> SCHEMA.equals(s.getName()))
						.findFirst().orElseThrow();
				assertEquals(2, original.getViews().get("V").getColumns().size());
				assertEquals(0, original.getFunctions().get("F").getArguments().size());
				assertEquals(com.sqlapp.data.db.datatype.DataType.INT,
						original.getFunctions().get("F").getReturning().getDataType());
				assertEquals("TABLE", original.getSynonyms().get("A").getSpecifics().get("ALIAS_TYPE"));
				assertTrue(original.getTables().get("T").getIndexes().get("IX").getColumns().get(1).isIncludedColumn());
				assertNotNull(original.getMviews().get("MQ"));
				assertNull(original.getViews().get("MQ"));
				assertNotNull(original.getMasks().get("HIDE_PAYLOAD"));
				assertEquals("IX_DIFFERENT",
						((UniqueConstraint) original.getTables().get("MQ_BASE").getConstraints().get("UK_BASE"))
								.getIndex().getName());
				assertEquals(1, com.sqlapp.data.db.dialect.db2.Db2RowPermissions.get(original).permissions().size());
				Schema restored = SchemaUtils.readXml(new StringReader(original.asXml()));
				var tablePrivileges = dialect.getCatalogReader().getObjectPrivilegeReader();
				tablePrivileges.setSchemaName(SCHEMA);
				tablePrivileges.setObjectName("T");
				var columnPrivileges = dialect.getCatalogReader().getColumnPrivilegeReader();
				columnPrivileges.setSchemaName(SCHEMA);
				columnPrivileges.setTableName("T");
				var routinePrivileges = dialect.getCatalogReader().getRoutinePrivilegeReader();
				routinePrivileges.setObjectName("F");
				Catalog grants = new Catalog();
				var extendedPrivileges = ((com.sqlapp.data.db.dialect.db2.metadata.Db2CatalogReader) dialect
						.getCatalogReader()).getExtendedObjectPrivilegeReader();
				extendedPrivileges.setSchemaName(SCHEMA);
				extendedPrivileges.setObjectName("IX");
				extendedPrivileges.loadFull(connection, grants);
				assertEquals(2, com.sqlapp.data.db.dialect.db2.Db2ObjectPrivileges.get(grants).grants().stream().filter(
						p -> p.granteeType() == com.sqlapp.data.db.dialect.db2.Db2ObjectPrivileges.GranteeType.PUBLIC)
						.count());
				var schemaPrivileges = ((com.sqlapp.data.db.dialect.db2.metadata.Db2CatalogReader) dialect
						.getCatalogReader()).getSchemaPrivilegeReader();
				schemaPrivileges.setSchemaName(SCHEMA);
				grants.getSchemaPrivileges().addAll(schemaPrivileges.getAllFull(connection));
				assertTrue(grants.getSchemaPrivileges().stream()
						.anyMatch(p -> "PUBLIC".equals(p.getGranteeName()) && "ALTERIN".equals(p.getPrivilege())));
				if (dialect instanceof com.sqlapp.data.db.dialect.db2.Db2_1155)
					assertTrue(grants.getSchemaPrivileges().stream()
							.anyMatch(p -> "PUBLIC".equals(p.getGranteeName()) && "SELECTIN".equals(p.getPrivilege())));
				grants.getObjectPrivileges().addAll(tablePrivileges.getAllFull(connection));
				grants.getColumnPrivileges().addAll(columnPrivileges.getAllFull(connection));
				routinePrivileges.getAllFull(connection).stream().filter(p -> SCHEMA.equals(p.getSchemaName()))
						.forEach(p -> grants.getRoutinePrivileges().add(p));
				assertTrue(grants.getObjectPrivileges().stream()
						.anyMatch(p -> "PUBLIC".equals(p.getGranteeName()) && "SELECT".equals(p.getPrivilege())));
				assertTrue(grants.getColumnPrivileges().stream()
						.anyMatch(p -> "PUBLIC".equals(p.getGranteeName()) && "PAYLOAD".equals(p.getColumnName())));
				assertTrue(grants.getRoutinePrivileges().stream().anyMatch(p -> "PUBLIC".equals(p.getGranteeName())));
				Catalog restoredGrants = SchemaUtils.readXml(new StringReader(grants.asXml()));
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				registry.getOptions().setCreateIfNotExists(false);
				for (String ddl : dropSql()) {
					statement.execute(ddl);
				}
				SqlFactory<Schema> schemaFactory = registry.getSqlFactory(restored, SqlType.CREATE);
				for (SqlOperation operation : schemaFactory.createSql(restored)) {
					if (!operation.getSqlText().stripLeading().startsWith("CREATE SCHEMA "))
						statement.execute(operation.getSqlText());
				}
				SqlFactory<Catalog> grantFactory = registry.getSqlFactory(restoredGrants, SqlType.CREATE);
				statement.execute("REVOKE ALTERIN ON SCHEMA " + SCHEMA + " FROM PUBLIC");
				if (dialect instanceof com.sqlapp.data.db.dialect.db2.Db2_1155)
					statement.execute("REVOKE SELECTIN ON SCHEMA " + SCHEMA + " FROM PUBLIC");
				for (SqlOperation operation : grantFactory.createSql(restoredGrants))
					statement.execute(operation.getSqlText());
				Catalog rereadExtended = new Catalog();
				extendedPrivileges.loadFull(connection, rereadExtended);
				assertEquals(2, com.sqlapp.data.db.dialect.db2.Db2ObjectPrivileges.get(rereadExtended).grants().stream()
						.filter(p -> p
								.granteeType() == com.sqlapp.data.db.dialect.db2.Db2ObjectPrivileges.GranteeType.PUBLIC)
						.count());
				Catalog withoutExtended = restoredGrants.clone();
				com.sqlapp.data.db.dialect.db2.Db2ObjectPrivileges.set(withoutExtended, List.of());
				SqlFactory<Catalog> alterGrants = registry.getSqlFactory(restoredGrants, SqlType.ALTER);
				for (SqlOperation operation : alterGrants.createDiffSql(restoredGrants.diff(withoutExtended)))
					statement.execute(operation.getSqlText());
				extendedPrivileges.loadFull(connection, rereadExtended);
				assertTrue(com.sqlapp.data.db.dialect.db2.Db2ObjectPrivileges.get(rereadExtended).grants().stream()
						.noneMatch(p -> p
								.granteeType() == com.sqlapp.data.db.dialect.db2.Db2ObjectPrivileges.GranteeType.PUBLIC));
				for (SqlOperation operation : alterGrants.createDiffSql(withoutExtended.diff(restoredGrants)))
					statement.execute(operation.getSqlText());
				assertTrue(schemaPrivileges.getAllFull(connection).stream()
						.anyMatch(p -> "PUBLIC".equals(p.getGranteeName()) && "ALTERIN".equals(p.getPrivilege())));
				assertTrue(tablePrivileges.getAllFull(connection).stream()
						.anyMatch(p -> "PUBLIC".equals(p.getGranteeName()) && "SELECT".equals(p.getPrivilege())));
				assertTrue(columnPrivileges.getAllFull(connection).stream()
						.anyMatch(p -> "PUBLIC".equals(p.getGranteeName()) && "PAYLOAD".equals(p.getColumnName())));
				Schema reread = reader.getAllFull(connection).stream().filter(s -> SCHEMA.equals(s.getName()))
						.findFirst().orElseThrow();
				assertEquals("Parity table", reread.getTables().get("T").getRemarks());
				assertEquals("Payload", reread.getTables().get("T").getColumns().get("PAYLOAD").getRemarks());
				assertEquals(2, reread.getViews().get("V").getColumns().size());
				assertTrue(reread.getTables().get("T").getIndexes().get("IX").getColumns().get(1).isIncludedColumn());
				assertEquals(com.sqlapp.data.db.dialect.db2.Db2RowPermissions.get(original),
						com.sqlapp.data.db.dialect.db2.Db2RowPermissions.get(reread));
				assertNotNull(reread.getMviews().get("MQ"));
				statement.execute("INSERT INTO " + SCHEMA + ".T VALUES (1, 7)");
				try (var rs = statement.executeQuery("SELECT PAYLOAD FROM " + SCHEMA + ".T WHERE ID=1")) {
					assertTrue(rs.next());
					assertEquals(0, rs.getInt(1));
				}
				// Exercise the registered Schema ALTER path and its reverse on the live catalog
				// snapshot.
				Schema disabled = reread.clone();
				var snapshot = com.sqlapp.data.db.dialect.db2.Db2RowPermissions.get(disabled);
				var permissions = snapshot.permissions().stream()
						.map(p -> new com.sqlapp.data.db.dialect.db2.Db2RowPermissions.Permission(p.schemaName(),
								p.name(), p.tableSchemaName(), p.tableName(), "R.ID > 10", p.correlationName(),
								p.enabled(), p.valid(), p.qualifier(), p.functionPath(), p.remarks()))
						.toList();
				com.sqlapp.data.db.dialect.db2.Db2RowPermissions.set(disabled, permissions, snapshot.tables());
				SqlFactory<Schema> alter = registry.getSqlFactory(disabled, SqlType.ALTER);
				for (SqlOperation operation : alter.createDiffSql(reread.diff(disabled)))
					statement.execute(operation.getSqlText());
				try (var rs = statement.executeQuery("SELECT COUNT(*) FROM " + SCHEMA + ".T")) {
					assertTrue(rs.next());
					assertEquals(0, rs.getInt(1));
				}
				for (SqlOperation operation : alter.createDiffSql(disabled.diff(reread)))
					statement.execute(operation.getSqlText());
				try (var rs = statement.executeQuery("SELECT COUNT(*) FROM " + SCHEMA + ".T")) {
					assertTrue(rs.next());
					assertEquals(1, rs.getInt(1));
				}
				verifyDatabaseAuthorities(connection);
			} finally {
				// The complete schema is owned exclusively by this container test.
				for (String ddl : dropSql()) {
					try {
						statement.execute(ddl);
					} catch (java.sql.SQLException e) {
						if (!"42704".equals(e.getSQLState()))
							throw e;
					}
				}
				statement.execute("DROP SCHEMA " + SCHEMA + " RESTRICT");
			}
		}
	}

	private static List<String> dropSql() {
		return List.of("DROP ALIAS " + SCHEMA + ".A FOR TABLE", "DROP VIEW " + SCHEMA + ".V",
				"DROP SEQUENCE " + SCHEMA + ".IX RESTRICT", "DROP PERMISSION " + SCHEMA + ".ALLOW_POSITIVE",
				"DROP MASK " + SCHEMA + ".HIDE_PAYLOAD", "DROP TABLE " + SCHEMA + ".MQ",
				"DROP TABLE " + SCHEMA + ".MQ_BASE", "DROP TABLE " + SCHEMA + ".T", "DROP FUNCTION " + SCHEMA + ".F",
				"DROP PROCEDURE " + SCHEMA + ".P");
	}

	private static void verifyDatabaseAuthorities(Connection connection) throws Exception {
		String role = "SQLAPP_PARITY_DATABASE_AUTH";
		var dialect = DialectResolver.getInstance().getDialect(connection);
		var registry = dialect.createSqlFactoryRegistry();
		try (var statement = connection.createStatement()) {
			statement.execute("CREATE ROLE " + role);
			try {
				statement.execute("GRANT BINDADD ON DATABASE TO ROLE " + role);
				var reader = dialect.getCatalogReader().getUserPrivilegeReader();
				reader.setUserName(role);
				Catalog original = new Catalog();
				original.getUserPrivileges().addAll(reader.getAllFull(connection));
				assertTrue(original.getUserPrivileges().stream().anyMatch(
						p -> "BINDADD".equals(p.getPrivilege()) && "R".equals(p.getSpecifics().get("GRANTEE_TYPE"))));
				Catalog restored = SchemaUtils.readXml(new StringReader(original.asXml()));
				statement.execute("REVOKE BINDADD ON DATABASE FROM ROLE " + role);
				SqlFactory<Catalog> create = registry.getSqlFactory(restored, SqlType.CREATE);
				for (SqlOperation operation : create.createSql(restored))
					statement.execute(operation.getSqlText());
				assertTrue(reader.getAllFull(connection).stream().anyMatch(p -> "BINDADD".equals(p.getPrivilege())));
				Catalog empty = restored.clone();
				empty.getUserPrivileges().clear();
				SqlFactory<Catalog> alter = registry.getSqlFactory(restored, SqlType.ALTER);
				for (SqlOperation operation : alter.createDiffSql(restored.diff(empty)))
					statement.execute(operation.getSqlText());
				assertTrue(reader.getAllFull(connection).stream().noneMatch(p -> "BINDADD".equals(p.getPrivilege())));
				for (SqlOperation operation : alter.createDiffSql(empty.diff(restored)))
					statement.execute(operation.getSqlText());
				assertTrue(reader.getAllFull(connection).stream().anyMatch(p -> "BINDADD".equals(p.getPrivilege())));
			} finally {
				statement.execute("DROP ROLE " + role);
			}
		}
	}
}
