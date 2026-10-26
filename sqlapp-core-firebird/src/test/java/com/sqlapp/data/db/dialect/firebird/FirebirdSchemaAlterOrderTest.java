/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.firebird.FirebirdSchemaDependencies.*;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class FirebirdSchemaAlterOrderTest {
	private Dependency edge(Kind dependent, String name, Kind provider, String prerequisite) {
		return new Dependency(new ObjectId(dependent, name), new ObjectId(provider, prerequisite));
	}

	private Table table(String name) {
		var table = new Table(name);
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		return table;
	}

	private Schema schema() {
		var schema = new Schema();
		schema.getTables().add(table("T"));
		schema.getViews().add(new View("V").setStatement("SELECT ID FROM T"));
		schema.getProcedures().add(new Procedure("P").setStatement("BEGIN END"));
		FirebirdSchemaDependencies.set(schema,
				List.of(edge(Kind.PROCEDURE, "P", Kind.VIEW, "V"), edge(Kind.VIEW, "V", Kind.TABLE, "T")));
		return schema;
	}

	private List<String> sql(Schema before, Schema after) {
		return DialectHolder.defaultDialect25.createSqlFactoryRegistry().getSqlFactory(before, SqlType.ALTER)
				.createDiffSql(before.diff(after)).stream().map(operation -> operation.getSqlText()).toList();
	}

	@Test
	void additionsAndDeletionsUseOppositeDependencyOrders() {
		var schema = schema();
		var empty = new Schema();
		FirebirdSchemaDependencies.set(empty, List.of());
		var create = sql(empty, schema);
		assertTrue(create.get(0).contains("CREATE TABLE"), create.toString());
		assertTrue(create.get(1).contains("CREATE VIEW"), create.toString());
		assertTrue(create.get(2).contains("CREATE PROCEDURE"), create.toString());
		var drop = sql(schema, empty);
		assertEquals(List.of("DROP PROCEDURE P", "DROP VIEW V", "DROP TABLE T"), drop);
	}

	@Test
	void referenceRetargetingCanCreateThenAlterThenDrop() {
		var before = new Schema();
		before.getTables().add(table("OLD_TABLE"));
		before.getViews().add(new View("V").setStatement("SELECT ID FROM OLD_TABLE"));
		FirebirdSchemaDependencies.set(before, List.of(edge(Kind.VIEW, "V", Kind.TABLE, "OLD_TABLE")));
		var after = before.clone();
		after.getTables().clear();
		after.getTables().add(table("NEW_TABLE"));
		after.getViews().get("V").setStatement("SELECT ID FROM NEW_TABLE");
		FirebirdSchemaDependencies.set(after, List.of(edge(Kind.VIEW, "V", Kind.TABLE, "NEW_TABLE")));
		var plan = sql(before, after);
		assertTrue(plan.get(0).contains("CREATE TABLE NEW_TABLE"), plan.toString());
		assertTrue(plan.get(1).contains("ALTER VIEW V"), plan.toString());
		assertEquals("DROP TABLE OLD_TABLE", plan.getLast());
	}

	@Test
	void retainedStaleOrUnmodeledDependentsFailBeforeSqlIsReturned() {
		var before = schema();
		var after = before.clone();
		after.getTables().clear();
		assertThrows(UnsupportedOperationException.class, () -> sql(before, after));
		FirebirdSchemaDependencies.set(after, List.of());
		assertThrows(UnsupportedOperationException.class, () -> sql(before, after));
		before.getViews().clear();
		before.getProcedures().clear();
		var empty = new Schema();
		FirebirdSchemaDependencies.set(empty, List.of());
		assertThrows(UnsupportedOperationException.class, () -> sql(before, empty));
	}

	@Test
	void shapeChangesWithRetainedReferencesRequireExplicitStagingButColumnAddsDoNot() {
		var before = schema();
		var after = before.clone();
		after.getTables().get("T").getColumns().get("ID").setDataType(DataType.BIGINT);
		assertThrows(UnsupportedOperationException.class, () -> sql(before, after));
		var added = before.clone();
		added.getTables().get("T").getColumns().add(new Column("NEW_COLUMN").setDataType(DataType.INT));
		assertTrue(sql(before, added).getFirst().contains("ADD NEW_COLUMN"));
	}

	@Test
	void releasingAReferenceBeforeDeletingAColumnUsesTheOldGraph() throws Exception {
		var before = new Schema();
		before.getTables().add(table("T"));
		before.getTables().get("T").getColumns().add(new Column("KEPT").setDataType(DataType.INT));
		before.getProcedures().add(new Procedure("P").setStatement("BEGIN END"));
		FirebirdSchemaDependencies.set(before, List.of(edge(Kind.PROCEDURE, "P", Kind.TABLE, "T")));
		var after = before.clone();
		after.getTables().get("T").getColumns().remove("ID");
		after.getProcedures().get("P").setStatement("BEGIN EXIT; END");
		FirebirdSchemaDependencies.set(after, List.of());
		String beforeXml = before.asXml(), afterXml = after.asXml();
		var plan = sql(before, after);
		assertEquals(beforeXml, before.asXml(), "Planner must not modify the source snapshot");
		assertEquals(afterXml, after.asXml(), "Planner must not modify the target snapshot");
		assertTrue(plan.getFirst().contains("ALTER PROCEDURE P"), plan.toString());
		assertTrue(plan.getLast().contains("DROP ID"), plan.toString());
		after.getSpecifics().remove(FirebirdSchemaDependencies.SPECIFICS_KEY);
		assertThrows(UnsupportedOperationException.class, () -> sql(before, after));
	}

	@Test
	void cyclesAreRejectedAndExplicitEmptyGraphsSurviveXml() throws Exception {
		var empty = new Schema();
		FirebirdSchemaDependencies.set(empty, List.of());
		Schema restored = SchemaUtils.readXml(new java.io.StringReader(empty.asXml()));
		assertNotNull(restored.getSpecifics().get(FirebirdSchemaDependencies.SPECIFICS_KEY));
		assertTrue(FirebirdSchemaDependencies.get(restored).isEmpty());
		var target = schema();
		FirebirdSchemaDependencies.set(target,
				List.of(edge(Kind.PROCEDURE, "P", Kind.VIEW, "V"), edge(Kind.VIEW, "V", Kind.PROCEDURE, "P")));
		var error = assertThrows(UnsupportedOperationException.class, () -> sql(empty, target));
		assertTrue(error.getMessage().contains("cyclic dependencies"));
	}

	@Test
	void packageBodyDropUsesVendorSyntaxAndPrecedesHeaderDrop() {
		for (var dialect : List.of(DialectHolder.defaultDialect30, DialectHolder.defaultDialect40,
				DialectHolder.defaultDialect50)) {
			var registry = dialect.createSqlFactoryRegistry();
			assertEquals("DROP PACKAGE BODY \"quoted\"\" body\"",
					registry.createSql(new PackageBody("quoted\" body"), SqlType.DROP).getFirst().getSqlText());
			var before = new Schema();
			before.getPackages().add(new com.sqlapp.data.schemas.Package("PKG").setStatement("BEGIN END"));
			before.getPackageBodies().add(new PackageBody("PKG").setStatement("BEGIN END"));
			var drop = registry.getSqlFactory(before, SqlType.ALTER).createDiffSql(before.diff(new Schema())).stream()
					.map(operation -> operation.getSqlText()).toList();
			assertEquals(List.of("DROP PACKAGE BODY PKG", "DROP PACKAGE PKG"), drop);
		}
	}

	@Test
	void modeledForeignKeyReleaseDoesNotRequireCatalogPayload() {
		var before = new Schema();
		var parent = table("PARENT");
		var child = table("CHILD");
		before.getTables().add(parent);
		before.getTables().add(child);
		child.getConstraints()
				.add(new ForeignKeyConstraint("FK_DEP", child.getColumns().get(0), parent.getColumns().get(0)));
		var after = before.clone();
		after.getTables().get("CHILD").getConstraints().clear();
		after.getTables().remove("PARENT");
		var plan = sql(before, after);
		assertTrue(plan.getFirst().contains("DROP CONSTRAINT FK_DEP"), plan.toString());
		assertEquals("DROP TABLE PARENT", plan.getLast());
		child.getColumns()
				.add(new Column("VALUE").setDataType(DataType.INT).setFormula("(SELECT MAX(ID) FROM PARENT)"));
		var opaque = before.clone();
		opaque.getTables().get("CHILD").getConstraints().clear();
		opaque.getTables().remove("PARENT");
		assertThrows(UnsupportedOperationException.class, () -> sql(before, opaque));
	}

	@Test
	void unknownLegacyBodyGraphsCannotSilentlyGuessCrossObjectOrder() {
		var before = schema();
		before.getSpecifics().remove(FirebirdSchemaDependencies.SPECIFICS_KEY);
		assertThrows(UnsupportedOperationException.class, () -> sql(before, new Schema()));
		var target = schema();
		target.getSpecifics().remove(FirebirdSchemaDependencies.SPECIFICS_KEY);
		assertThrows(UnsupportedOperationException.class, () -> sql(new Schema(), target));
	}

	@Test
	void modeledTriggerOwnerDoesNotNeedCatalogPayload() {
		var target = new Schema();
		target.getTables().add(table("T"));
		var trigger = new Trigger("TR").setTableName("T").setActionTiming("BEFORE").setStatement("BEGIN END");
		trigger.addEventManipulation("INSERT");
		target.getTriggers().add(trigger);
		var create = sql(new Schema(), target);
		assertTrue(create.getFirst().contains("CREATE TABLE T"), create.toString());
		assertTrue(create.getLast().contains("CREATE TRIGGER TR"), create.toString());
		assertEquals(List.of("DROP TRIGGER TR", "DROP TABLE T"), sql(target, new Schema()));
	}

	@Test
	void modeledColumnDomainsSupplyCreateAndDropOrderWithoutCatalogPayload() {
		var schema = new Schema();
		schema.getDomains().add(new Domain("D").setDataType(DataType.INT));
		schema.getTables().add(table("T"));
		schema.getTables().get("T").getColumns().get("ID").setDomainName("D");
		assertTrue(sql(new Schema(), schema).getFirst().contains("CREATE DOMAIN D"));
		assertEquals(List.of("DROP TABLE T", "DROP DOMAIN D"), sql(schema, new Schema()));
		var retained = schema.clone();
		retained.getDomains().clear();
		assertThrows(UnsupportedOperationException.class, () -> sql(schema, retained));
	}
}
