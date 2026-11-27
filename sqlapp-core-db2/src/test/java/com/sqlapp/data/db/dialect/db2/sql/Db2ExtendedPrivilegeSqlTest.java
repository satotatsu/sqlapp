/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.db2.Db2ObjectPrivileges;
import com.sqlapp.data.db.dialect.db2.Db2ObjectPrivileges.*;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class Db2ExtendedPrivilegeSqlTest {
	@Test
	void constraintBackingIndexGrantsDoNotTargetUnstableRecreatedNames() {
		Catalog catalog = new Catalog();
		Schema schema = new Schema("APP");
		catalog.getSchemas().add(schema);
		Table table = new Table("T");
		schema.getTables().add(table);
		table.getColumns().add("ID", c -> c.setDataType(DataType.INT));
		UniqueConstraint constraint = new UniqueConstraint("UK");
		constraint.getColumns().add(table.getColumns().get("ID"));
		constraint.getIndex().setName("PHYSICAL_IX");
		table.getConstraints().add(constraint);
		Db2ObjectPrivileges.set(catalog, List
				.of(new Grant(Kind.INDEX, "APP", "PHYSICAL_IX", "CONTROL", null, GranteeType.PUBLIC, "PUBLIC", false)));
		assertThrows(IllegalArgumentException.class, () -> Db2ObjectPrivileges.createSql(catalog));
	}

	@Test
	void catalogCreateAndAlterOrderGrantsAroundNativeObjects() throws Exception {
		var dialect = DialectHolder.Db2_1215Dialect;
		Catalog catalog = new Catalog().setDialect(dialect);
		Schema schema = new Schema("APP");
		catalog.getSchemas().add(schema);
		schema.getSequences().add(new Sequence("SAME").setDataType(DataType.BIGINT));
		Table table = new Table("T");
		table.getColumns().add("ID", c -> c.setDataType(DataType.INT));
		table.getIndexes().add("SAME", table.getColumns().get("ID"));
		schema.getTables().add(table);
		Db2ObjectPrivileges.set(catalog,
				List.of(new Grant(Kind.SEQUENCE, "APP", "SAME", "USAGE", null, GranteeType.PUBLIC, "PUBLIC", true),
						new Grant(Kind.INDEX, "APP", "SAME", "CONTROL", null, GranteeType.PUBLIC, "PUBLIC", false)));
		Catalog restored = SchemaUtils.readXml(new java.io.StringReader(catalog.asXml()));
		restored.setDialect(dialect);
		var registry = dialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		registry.getOptions().setCreateIfNotExists(false);
		SqlFactory<Catalog> create = registry.getSqlFactory(restored, SqlType.CREATE);
		String ddl = create.createSql(restored).stream().map(o -> o.getSqlText()).reduce("", (a, b) -> a + "\n" + b);
		assertTrue(ddl.indexOf("CREATE SEQUENCE APP.SAME") < ddl.indexOf("GRANT USAGE ON SEQUENCE"), ddl);
		assertTrue(ddl.indexOf("CREATE INDEX APP.SAME") < ddl.indexOf("GRANT CONTROL ON INDEX"), ddl);
		Catalog empty = new Catalog().setDialect(dialect);
		SqlFactory<Catalog> alter = registry.getSqlFactory(restored, SqlType.ALTER);
		var added = alter.createDiffSql(empty.diff(restored)).stream().map(o -> o.getSqlText()).toList();
		assertTrue(added.getLast().startsWith("GRANT CONTROL ON INDEX"), added.toString());
		Catalog withoutGrants = restored.clone();
		Db2ObjectPrivileges.set(withoutGrants, List.of());
		var removed = alter.createDiffSql(restored.diff(withoutGrants)).stream().map(o -> o.getSqlText()).toList();
		assertEquals(2, removed.size());
		assertTrue(removed.getFirst().endsWith(" RESTRICT"));
		assertTrue(alter.createDiffSql(withoutGrants.diff(restored)).getFirst().getSqlText().startsWith("GRANT USAGE"));
		assertEquals(2, Db2ObjectPrivileges.get(restored).grants().size());
	}

	@Test
	void serverCreationPrecedesPassthruAndCatalogAlterReplaysTypedGrantees() throws Exception {
		Catalog before = new Catalog(), after = new Catalog();
		String serverDdl = "CREATE SERVER \"Same.Name\" TYPE ORACLE VERSION '9i' WRAPPER NET8";
		com.sqlapp.data.db.dialect.db2.Db2Federation.set(after, List.of(),
				List.of(new com.sqlapp.data.db.dialect.db2.Db2Federation.Server("Same.Name", null, null, null, null,
						null, null, serverDdl)),
				List.of());
		Db2ObjectPrivileges.set(after, List.of(
				new Grant(Kind.SERVER, null, "Same.Name", "PASSTHRU", "OWNER", GranteeType.USER, "SAME", false),
				new Grant(Kind.SERVER, null, "Same.Name", "PASSTHRU", "OWNER", GranteeType.GROUP, "SAME", false),
				new Grant(Kind.SERVER, null, "Same.Name", "PASSTHRU", "OWNER", GranteeType.PUBLIC, "PUBLIC", false)));
		Catalog restored = SchemaUtils.readXml(new java.io.StringReader(after.asXml()));
		var registry = DialectHolder.Db2_1215Dialect.createSqlFactoryRegistry();
		SqlFactory<Catalog> factory = registry.getSqlFactory(restored, SqlType.CREATE);
		var created = factory.createSql(restored).stream().map(o -> o.getSqlText()).toList();
		assertEquals(serverDdl, created.getFirst());
		assertEquals(3, created.stream().filter(s -> s.startsWith("GRANT PASSTHRU")).count());
		factory = registry.getSqlFactory(restored, SqlType.ALTER);
		var added = factory.createDiffSql(before.diff(restored)).stream().map(o -> o.getSqlText()).toList();
		assertEquals(serverDdl, added.getFirst());
		assertTrue(added.contains("GRANT PASSTHRU ON SERVER \"Same.Name\" TO USER \"SAME\""));
		assertTrue(added.contains("GRANT PASSTHRU ON SERVER \"Same.Name\" TO GROUP \"SAME\""));
		Catalog withoutGrants = restored.clone();
		Db2ObjectPrivileges.set(withoutGrants, List.of());
		var removed = factory.createDiffSql(restored.diff(withoutGrants)).stream().map(o -> o.getSqlText()).toList();
		assertEquals(3, removed.size());
		assertTrue(removed.contains("REVOKE PASSTHRU ON SERVER \"Same.Name\" FROM PUBLIC"));
		assertEquals(3, Db2ObjectPrivileges.get(restored).grants().size());
	}

	@Test
	void taggedDirectReaderResultsGenerateNativeScopeRatherThanTableGrants() {
		var privilege = new ObjectPrivilege().setSchemaName("APP").setObjectName("SAME").setPrivilege("USAGE")
				.setGranteeName("PUBLIC").setGrantable(true).setHierachy(false);
		privilege.getSpecifics().put("OBJECT_TYPE", "SEQUENCE");
		privilege.getSpecifics().put("GRANTEE_TYPE", "G");
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		SqlFactory<ObjectPrivilege> factory = registry.getSqlFactory(privilege, SqlType.GRANT);
		assertEquals("GRANT USAGE ON SEQUENCE \"APP\".\"SAME\" TO PUBLIC WITH GRANT OPTION",
				factory.createSql(privilege).getFirst().getSqlText());
		factory = registry.getSqlFactory(privilege, SqlType.REVOKE);
		assertTrue(factory.createSql(privilege).getFirst().getSqlText().endsWith(" RESTRICT"));
		privilege.getSpecifics().put("OBJECT_TYPE", "INDEX");
		privilege.setPrivilege("CONTROL").setGrantable(false);
		assertEquals("REVOKE CONTROL ON INDEX \"APP\".\"SAME\" FROM PUBLIC",
				factory.createSql(privilege).getFirst().getSqlText());
		privilege.getSpecifics().put("OBJECT_TYPE", "SERVER");
		privilege.setSchemaName(null).setPrivilege("PASSTHRU");
		assertEquals("REVOKE PASSTHRU ON SERVER \"SAME\" FROM PUBLIC",
				factory.createSql(privilege).getFirst().getSqlText());
	}
}
