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
	void releasingAReferenceBeforeDeletingAColumnUsesTheOldGraph() {
		var before = new Schema();
		before.getTables().add(table("T"));
		before.getTables().get("T").getColumns().add(new Column("KEPT").setDataType(DataType.INT));
		before.getProcedures().add(new Procedure("P").setStatement("BEGIN END"));
		FirebirdSchemaDependencies.set(before, List.of(edge(Kind.PROCEDURE, "P", Kind.TABLE, "T")));
		var after = before.clone();
		after.getTables().get("T").getColumns().remove("ID");
		after.getProcedures().get("P").setStatement("BEGIN EXIT; END");
		FirebirdSchemaDependencies.set(after, List.of());
		var plan = sql(before, after);
		assertTrue(plan.getFirst().contains("ALTER PROCEDURE P"), plan.toString());
		assertTrue(plan.getLast().contains("DROP ID"), plan.toString());
	}
}
