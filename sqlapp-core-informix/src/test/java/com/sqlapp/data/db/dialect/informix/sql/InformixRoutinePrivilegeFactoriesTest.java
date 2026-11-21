/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.PrivilegeState;
import com.sqlapp.data.schemas.RoutinePrivilege;

class InformixRoutinePrivilegeFactoriesTest {
	private RoutinePrivilege privilege() {
		var result = new RoutinePrivilege().setSchemaName("owner").setObjectName("routine")
				.setSpecificName("specific\"name").setPrivilege("EXECUTE").setHierachy(false).setGranteeName("reader")
				.setGrantorName("owner");
		result.getSpecifics().put("INFORMIX_ROUTINE_KIND", "PROCEDURE");
		result.getSpecifics().put("INFORMIX_ROUTINE_IDENTITY", "SPECIFIC_NAME");
		return result;
	}

	private String sql(RoutinePrivilege value, SqlType type) {
		return DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(value, type).get(0).getSqlText();
	}

	@Test
	void preservesSpecificIdentityGrantorAndGrantOption() {
		assertEquals(
				"GRANT EXECUTE ON SPECIFIC PROCEDURE \"owner\".\"specific\"\"name\" TO \"reader\" WITH GRANT OPTION AS \"owner\"",
				sql(privilege().setGrantable(true), SqlType.CREATE));
		assertEquals("REVOKE EXECUTE ON SPECIFIC PROCEDURE \"owner\".\"specific\"\"name\" FROM \"reader\" AS \"owner\"",
				sql(privilege(), SqlType.DROP));
	}

	@Test
	void allowsPublicAndExecutingUserAsGrantor() {
		assertTrue(
				sql(privilege().setGranteeName("public").setGrantorName(null), SqlType.GRANT).endsWith(" TO PUBLIC"));
	}

	@Test
	void addressesUnnamedNoArgumentProceduresWithAnExplicitSignature() {
		var value = privilege().setSpecificName("routine()");
		value.getSpecifics().put("INFORMIX_ROUTINE_IDENTITY", "PARAMETER_TYPE_IDS");
		assertEquals("GRANT EXECUTE ON PROCEDURE \"owner\".\"routine\"() TO \"reader\" AS \"owner\"",
				sql(value, SqlType.GRANT));
		assertEquals("REVOKE EXECUTE ON PROCEDURE \"owner\".\"routine\"() FROM \"reader\" AS \"owner\"",
				sql(value, SqlType.REVOKE));
		value.setSpecificName("routine(2)");
		assertThrows(IllegalArgumentException.class, () -> sql(value, SqlType.GRANT));
	}

	@Test
	void createsCatalogRoutinesBeforeTheirGrantsWithoutStandaloneCreateSchema() {
		var catalog = new com.sqlapp.data.schemas.Catalog("testdb");
		var schema = new com.sqlapp.data.schemas.Schema("owner");
		catalog.getSchemas().add(schema);
		var routine = new com.sqlapp.data.schemas.Procedure("routine");
		routine.getDefinition().add("CREATE PROCEDURE \"owner\".\"routine\"(); END PROCEDURE;");
		schema.getProcedures().add(routine);
		catalog.getRoles().add(new com.sqlapp.data.schemas.Role("reader"));
		var value = privilege().setSpecificName("routine()");
		value.getSpecifics().put("INFORMIX_ROUTINE_IDENTITY", "PARAMETER_TYPE_IDS");
		catalog.getRoutinePrivileges().add(value);
		var operations = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(catalog, SqlType.CREATE);
		var statements = operations.stream().map(operation -> operation.getSqlText().strip()).toList();
		assertEquals(3, statements.size(), statements.toString());
		assertTrue(statements.get(0).startsWith("CREATE ROLE"));
		assertTrue(statements.get(1).startsWith("CREATE PROCEDURE"));
		assertTrue(statements.get(2).startsWith("GRANT EXECUTE ON PROCEDURE"));
	}

	@Test
	void plansCatalogRoutineGrantChangesWithGrantChainProtection() {
		var empty = new com.sqlapp.data.schemas.Catalog("testdb");
		var granted = empty.clone();
		granted.getRoutinePrivileges().add(privilege().setGrantable(false));
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertEquals(sql(privilege(), SqlType.GRANT),
				registry.createSql(empty.diff(granted), SqlType.ALTER).getFirst().getSqlText());
		assertEquals(sql(privilege(), SqlType.REVOKE),
				registry.createSql(granted.diff(empty), SqlType.ALTER).getFirst().getSqlText());
		var upgraded = granted.clone();
		upgraded.getRoutinePrivileges().get(0).setGrantable(true);
		var operations = registry.createSql(granted.diff(upgraded), SqlType.ALTER);
		assertEquals(1, operations.size());
		assertTrue(operations.getFirst().getSqlText().contains("WITH GRANT OPTION"));
		var downgrade = registry.createSql(upgraded.diff(granted), SqlType.ALTER);
		assertEquals(java.util.List.of(sql(privilege(), SqlType.REVOKE), sql(privilege(), SqlType.GRANT)),
				downgrade.stream().map(operation -> operation.getSqlText()).toList());
		assertEquals(1, registry.createSql(upgraded.diff(empty), SqlType.ALTER).size());
		var delegated = upgraded.clone();
		delegated.getRoutinePrivileges().add(privilege().setGrantorName("reader").setGranteeName("downstream"));
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(delegated.diff(granted), SqlType.ALTER));
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(delegated.diff(empty), SqlType.ALTER));
	}

	@Test
	void ordersDelegatedRoutineGrantsForCatalogCreateAndAlter() {
		var before = new com.sqlapp.data.schemas.Catalog("testdb");
		var after = before.clone();
		var delegate = privilege().setGrantorName("z_intermediate").setGranteeName("a_delegate");
		var parent = privilege().setGranteeName("z_intermediate").setGrantable(true);
		after.getRoutinePrivileges().add(delegate);
		after.getRoutinePrivileges().add(parent);
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var expected = java.util.List.of(sql(parent, SqlType.GRANT), sql(delegate, SqlType.GRANT));
		assertEquals(expected, registry.createSql(after, SqlType.CREATE).stream().map(o -> o.getSqlText()).toList());
		assertEquals(expected,
				registry.createSql(before.diff(after), SqlType.ALTER).stream().map(o -> o.getSqlText()).toList());
		parent.setGrantorName("a_delegate");
		delegate.setGrantable(true);
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(after, SqlType.CREATE));
	}

	@Test
	void ordersDelegatedGrantsAcrossTableAndColumnScopes() {
		var before = new com.sqlapp.data.schemas.Catalog("testdb");
		var after = before.clone();
		var table = new com.sqlapp.data.schemas.ObjectPrivilege().setSchemaName("owner").setObjectName("items")
				.setPrivilege("UPDATE").setGrantorName("owner").setGranteeName("table_delegate").setHierachy(false)
				.setGrantable(true);
		var column = new com.sqlapp.data.schemas.ColumnPrivilege().setSchemaName("owner").setTableName("items")
				.setColumnName("label").setPrivilege("UPDATE").setGrantorName("table_delegate")
				.setGranteeName("z_column_delegate").setGrantable(true);
		var delegated = column.clone().setGrantorName("z_column_delegate").setGranteeName("a_reader")
				.setGrantable(false);
		after.getColumnPrivileges().add(delegated);
		after.getColumnPrivileges().add(column);
		after.getObjectPrivileges().add(table);
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var expected = java.util.stream.Stream.<Object>of(table, column, delegated)
				.map(p -> registry.createSql(p, SqlType.GRANT).getFirst().getSqlText()).toList();
		assertEquals(expected, registry.createSql(after, SqlType.CREATE).stream().map(o -> o.getSqlText()).toList());
		assertEquals(expected,
				registry.createSql(before.diff(after), SqlType.ALTER).stream().map(o -> o.getSqlText()).toList());
	}

	@Test
	void plansMixedPrivilegesAndProtectsColumnGrantsFromTableRevoke() {
		var before = new com.sqlapp.data.schemas.Catalog("testdb");
		var tableGrant = new com.sqlapp.data.schemas.ObjectPrivilege().setSchemaName("owner").setObjectName("items")
				.setPrivilege("UPDATE").setGranteeName("reader").setGrantorName("owner").setHierachy(false)
				.setGrantable(false);
		before.getObjectPrivileges().add(tableGrant);
		var after = before.clone();
		after.getObjectPrivileges().get(0).setGrantable(true);
		after.getColumnPrivileges()
				.add(new com.sqlapp.data.schemas.ColumnPrivilege().setSchemaName("owner").setTableName("items")
						.setColumnName("label").setPrivilege("UPDATE").setGranteeName("reader").setGrantorName("owner")
						.setGrantable(false));
		after.getRoutinePrivileges().add(privilege());
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var operations = registry.createSql(before.diff(after), SqlType.ALTER);
		assertEquals(3, operations.size());
		assertTrue(operations.stream()
				.anyMatch(o -> o.getSqlText().contains("UPDATE ON") && o.getSqlText().contains("WITH GRANT OPTION")));
		assertTrue(operations.stream().anyMatch(o -> o.getSqlText().contains("UPDATE (\"label\")")));
		assertTrue(operations.stream().anyMatch(o -> o.getSqlText().contains("GRANT EXECUTE")));
		var withColumn = after.clone();
		withColumn.getObjectPrivileges().get(0).setGrantable(false);
		var removed = withColumn.clone();
		removed.getObjectPrivileges().clear();
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(withColumn.diff(removed), SqlType.ALTER));
	}

	@Test
	void removesTableGrantOptionOnlyWhenNoDownstreamGrantCanBeAffected() {
		var before = new com.sqlapp.data.schemas.Catalog("testdb");
		var table = new com.sqlapp.data.schemas.ObjectPrivilege().setSchemaName("owner").setObjectName("items")
				.setPrivilege("UPDATE").setGrantorName("owner").setGranteeName("reader").setHierachy(false)
				.setGrantable(true);
		before.getObjectPrivileges().add(table);
		var after = before.clone();
		after.getObjectPrivileges().get(0).setGrantable(false);
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var operations = registry.createSql(before.diff(after), SqlType.ALTER);
		assertEquals(2, operations.size());
		assertTrue(operations.getFirst().getSqlText().startsWith("REVOKE UPDATE"));
		assertTrue(operations.getLast().getSqlText().startsWith("GRANT UPDATE"));
		assertFalse(operations.getLast().getSqlText().contains("WITH GRANT OPTION"));
		var empty = new com.sqlapp.data.schemas.Catalog("testdb");
		assertEquals(1, registry.createSql(before.diff(empty), SqlType.ALTER).size());
		before.getColumnPrivileges()
				.add(new com.sqlapp.data.schemas.ColumnPrivilege().setSchemaName("owner").setTableName("items")
						.setColumnName("label").setPrivilege("UPDATE").setGrantorName("reader")
						.setGranteeName("delegate"));
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(before.diff(after), SqlType.ALTER));
	}

	@Test
	void coalescesColumnRevocationAndRestoresOnlySafeRetainedGrants() {
		var before = new com.sqlapp.data.schemas.Catalog("testdb");
		for (String name : java.util.List.of("label", "description")) {
			before.getColumnPrivileges()
					.add(new com.sqlapp.data.schemas.ColumnPrivilege().setSchemaName("owner").setTableName("items")
							.setColumnName(name).setPrivilege("UPDATE").setGranteeName("reader").setGrantorName("owner")
							.setGrantable(false));
		}
		var after = before.clone();
		after.getColumnPrivileges().clear();
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var operations = registry.createSql(before.diff(after), SqlType.ALTER);
		assertEquals(1, operations.size());
		assertEquals("REVOKE UPDATE ON \"owner\".\"items\" FROM \"reader\" AS \"owner\"",
				operations.getFirst().getSqlText());
		after.getColumnPrivileges().add(before.getColumnPrivileges().getFirst().clone());
		var partial = registry.createSql(before.diff(after), SqlType.ALTER);
		assertEquals(2, partial.size());
		assertEquals(operations.getFirst().getSqlText(), partial.getFirst().getSqlText());
		assertEquals("GRANT UPDATE (\"label\") ON \"owner\".\"items\" TO \"reader\" AS \"owner\"",
				partial.getLast().getSqlText());
		before.getColumnPrivileges().getFirst().setGrantable(true);
		after.getColumnPrivileges().getFirst().setGrantable(true);
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(before.diff(after), SqlType.ALTER));
		after.getColumnPrivileges().getFirst().setGrantable(false);
		assertEquals(partial.stream().map(o -> o.getSqlText()).toList(),
				registry.createSql(before.diff(after), SqlType.ALTER).stream().map(o -> o.getSqlText()).toList());
		var delegated = before.clone();
		delegated.getColumnPrivileges().add(
				before.getColumnPrivileges().getFirst().clone().setGrantorName("reader").setGranteeName("delegate"));
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(delegated.diff(after), SqlType.ALTER));
		before.getColumnPrivileges().getFirst().setGrantable(false);
		after.getColumnPrivileges().clear();
		var retained = new com.sqlapp.data.schemas.ObjectPrivilege().setSchemaName("owner").setObjectName("items")
				.setPrivilege("UPDATE").setGranteeName("reader").setGrantorName("owner").setHierachy(false)
				.setGrantable(false);
		before.getObjectPrivileges().add(retained);
		after.getObjectPrivileges().add(retained.clone());
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(before.diff(after), SqlType.ALTER));
		after.getObjectPrivileges().clear();
		assertEquals(1, registry.createSql(before.diff(after), SqlType.ALTER).size());
	}

	@Test
	void retainsExecutableIdentityAcrossXml() throws Exception {
		RoutinePrivilege original = privilege().setGrantable(true);
		RoutinePrivilege restored = com.sqlapp.data.schemas.SchemaUtils
				.readXml(new java.io.StringReader(original.asXml()));
		assertEquals(sql(original, SqlType.GRANT), sql(restored, SqlType.GRANT));
		assertFalse(restored.isHierachy());
	}

	@Test
	void rejectsUnsupportedSemanticsAndAmbiguousIdentities() {
		assertThrows(IllegalArgumentException.class, () -> sql(privilege().setPrivilege("UPDATE"), SqlType.GRANT));
		assertThrows(IllegalArgumentException.class,
				() -> sql(privilege().setState(PrivilegeState.Deny), SqlType.GRANT));
		assertThrows(IllegalArgumentException.class, () -> sql(privilege().setHierachy(true), SqlType.GRANT));
		assertThrows(IllegalArgumentException.class, () -> sql(privilege().setSchemaName(null), SqlType.GRANT));
		assertThrows(IllegalArgumentException.class,
				() -> sql(privilege().setGranteeName("public").setGrantable(true), SqlType.GRANT));
		assertThrows(IllegalArgumentException.class,
				() -> sql(privilege().setGrantee(new com.sqlapp.data.schemas.Role("reader")).setGrantable(true),
						SqlType.GRANT));
		var ambiguous = privilege();
		ambiguous.getSpecifics().remove("INFORMIX_ROUTINE_IDENTITY");
		assertThrows(IllegalArgumentException.class, () -> sql(ambiguous, SqlType.GRANT));
	}
}
