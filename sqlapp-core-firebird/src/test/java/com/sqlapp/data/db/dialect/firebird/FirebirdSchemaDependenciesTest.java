/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.FirebirdSchemaDependencies.*;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class FirebirdSchemaDependenciesTest {
	private Dependency edge(Kind dependent, String name, Kind provider, String prerequisite) {
		return new Dependency(new ObjectId(dependent, name), new ObjectId(provider, prerequisite));
	}

	private Schema schema() {
		var schema = new Schema();
		schema.getTables().add(new Table("source"));
		schema.getTables().get(0).getColumns()
				.add(new Column("ID").setDataType(com.sqlapp.data.db.datatype.DataType.INT));
		schema.getProcedures().add(new Procedure("reader").setStatement("BEGIN END").setRemarks("retained comment"));
		schema.getViews().add(new View("view").setStatement("SELECT ID FROM \"source\""));
		return schema;
	}

	@Test
	void xmlCloneAndStableCreationOrderPreserveObjectGroups() throws Exception {
		var schema = schema();
		var edges = List.of(edge(Kind.PROCEDURE, "reader", Kind.VIEW, "view"),
				edge(Kind.VIEW, "view", Kind.TABLE, "source"));
		FirebirdSchemaDependencies.set(schema, edges);
		Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
		assertEquals(edges, FirebirdSchemaDependencies.get(restored));
		assertEquals(edges, FirebirdSchemaDependencies.get(schema.clone()));
		for (var dialect : List.of(DialectHolder.defaultDialect25, DialectHolder.defaultDialect30,
				DialectHolder.defaultDialect40, DialectHolder.defaultDialect50)) {
			var sql = dialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE).stream()
					.map(s -> s.getSqlText()).toList();
			assertTrue(sql.get(0).contains("CREATE TABLE"), sql.toString());
			assertTrue(sql.get(1).contains("CREATE VIEW"), sql.toString());
			assertTrue(sql.get(2).contains("CREATE PROCEDURE"), sql.toString());
			assertTrue(sql.get(3).contains("COMMENT ON PROCEDURE"), sql.toString());
		}
	}

	@Test
	void cyclesFailBeforeReturningAPartialPlanAndKindsRemainDistinct() {
		var schema = schema();
		FirebirdSchemaDependencies.set(schema, List.of(edge(Kind.PROCEDURE, "reader", Kind.VIEW, "view"),
				edge(Kind.VIEW, "view", Kind.PROCEDURE, "reader")));
		var error = assertThrows(UnsupportedOperationException.class,
				() -> DialectHolder.defaultDialect25.createSqlFactoryRegistry().createSql(schema, SqlType.CREATE));
		assertTrue(error.getMessage().contains("cyclic dependencies"));
		FirebirdSchemaDependencies.set(schema, List.of(edge(Kind.PROCEDURE, "reader", Kind.PROCEDURE, "reader"),
				edge(Kind.PROCEDURE, "reader", Kind.TABLE, "reader")));
		assertFalse(
				DialectHolder.defaultDialect25.createSqlFactoryRegistry().createSql(schema, SqlType.CREATE).isEmpty());
	}

	@Test
	void invalidManifestsFailAndPlanningMetadataDoesNotBecomeAlterDdl() {
		var source = schema();
		var target = source.clone();
		FirebirdSchemaDependencies.set(target, List.of(edge(Kind.PROCEDURE, "reader", Kind.TABLE, "source")));
		assertTrue(DialectHolder.defaultDialect25.createSqlFactoryRegistry().getSqlFactory(source, SqlType.ALTER)
				.createDiffSql(source.diff(target)).isEmpty());
		target.getSpecifics().put(FirebirdSchemaDependencies.SPECIFICS_KEY, "{\"format\":2,\"dependencies\":[]}");
		assertThrows(IllegalArgumentException.class, () -> FirebirdSchemaDependencies.get(target));
		assertThrows(IllegalArgumentException.class, () -> new ObjectId(Kind.TABLE, "\0"));
	}

	@Test
	void expressionIndexTypeSurvivesSchemaXml() throws Exception {
		var schema = schema();
		var index = new Index("EXPR").setIndexType(IndexType.Function);
		index.getColumns().add("ABS(ID)");
		schema.getTables().get(0).getIndexes().add(index);
		Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
		assertEquals(IndexType.Function, restored.getTables().get(0).getIndexes().get(0).getIndexType(),
				schema.asXml());
		assertTrue(DialectHolder.defaultDialect30.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE)
				.stream().anyMatch(sql -> sql.getSqlText().contains("COMPUTED BY")));
	}
}
