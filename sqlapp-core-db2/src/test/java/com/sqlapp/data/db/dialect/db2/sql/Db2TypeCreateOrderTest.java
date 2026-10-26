/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.*;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

class Db2TypeCreateOrderTest {
	private Type row(String name, String target) {
		var type = new Type(name).setSchemaName("APP").setMetaType(Type.MetaType.Row);
		type.getColumns().add("V", field -> {
			if (target == null)
				field.setDataType(DataType.INT);
			else
				field.getSpecifics().putAll(Map.of("DB2_TYPE_SCHEMA", "APP", "TYPENAME", target));
		});
		return type;
	}

	private Domain domain(String name, String source, String kind) {
		var type = new Domain(name).setSchemaName("APP");
		type.getSpecifics().putAll(Map.of("DB2_OBJECT_TYPE", "DATA_TYPE", "METATYPE", kind, "SOURCESCHEMA",
				source.equals("INTEGER") ? "SYSIBM" : "APP", "SOURCENAME", source, "TYPERULES", "S"));
		if (kind.equals("A"))
			type.setArrayDimension(1).setArrayDimensionUpperBound(4);
		return type;
	}

	private List<String> sql(Schema schema) {
		SqlFactory<Schema> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(schema,
				SqlType.CREATE);
		return factory.createSql(schema).stream().map(SqlOperation::getSqlText).toList();
	}

	@Test
	void mixedRowDistinctArrayAndCursorDependenciesSurviveXmlAndCatalogCreate() throws Exception {
		var schema = new Schema("APP");
		var cursor = new Type("C").setMetaType(Type.MetaType.Cursor);
		cursor.getSpecifics()
				.putAll(Map.of("DB2_CURSOR_KIND", "S", "DB2_CURSOR_ROW_SCHEMA", "APP", "DB2_CURSOR_ROW_NAME", "B"));
		schema.getTypes().addAll(List.of(cursor, row("N", "A"), row("B", "D")));
		schema.getDomains().addAll(List.of(domain("A", "B", "A"), domain("D", "INTEGER", "T")));
		Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
		var statements = sql(restored);
		for (var edge : List.of(List.of("D", "B"), List.of("B", "A"), List.of("A", "N"), List.of("B", "C"))) {
			String first = "CREATE TYPE \"APP\".\"" + edge.getFirst() + "\" AS ";
			String second = "CREATE TYPE \"APP\".\"" + edge.getLast() + "\" AS ";
			assertTrue(position(statements, first) < position(statements, second), statements.toString());
		}
		assertTrue(statements.contains("CREATE TYPE \"APP\".\"B\" AS ROW (\"V\" \"APP\".\"D\")"));
		var catalog = new Catalog();
		catalog.getSchemas().add(restored);
		SqlFactory<Catalog> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(catalog,
				SqlType.CREATE);
		assertTrue(factory.createSql(catalog).stream().map(SqlOperation::getSqlText).toList().containsAll(statements));
		assertEquals(schema, restored);
	}

	private int position(List<String> sql, String prefix) {
		return java.util.stream.IntStream.range(0, sql.size()).filter(index -> sql.get(index).startsWith(prefix))
				.findFirst().orElseThrow();
	}

	@Test
	void domainOnlySchemasAndIndependentTypesAreEmittedOnceInStableOrder() {
		var schema = new Schema("APP");
		schema.getDomains().add(domain("D", "INTEGER", "T"));
		assertEquals(1, sql(schema).stream().filter(value -> value.startsWith("CREATE TYPE")).count());
		schema.getTypes().addAll(List.of(row("R1", null), row("R2", null)));
		var statements = sql(schema);
		assertEquals(3, statements.stream().filter(value -> value.startsWith("CREATE TYPE")).count());
		assertTrue(position(statements, "CREATE TYPE \"APP\".\"R1\"") < position(statements,
				"CREATE TYPE \"APP\".\"R2\""));
	}

	@Test
	void cyclesDuplicateIdentitiesAndIncompleteReferencesFailBeforeReturningPlans() {
		var schema = new Schema("APP");
		schema.getTypes().addAll(List.of(row("R1", "R2"), row("R2", "R1")));
		assertTrue(assertThrows(IllegalArgumentException.class, () -> sql(schema)).getMessage().contains("Cyclic"));
		schema.getTypes().clear();
		schema.getTypes().add(row("R", "A"));
		schema.getDomains().add(domain("A", "R", "A"));
		assertThrows(IllegalArgumentException.class, () -> sql(schema));
		schema.getDomains().clear();
		schema.getTypes().clear();
		schema.getTypes().add(row("D", null));
		schema.getDomains().add(domain("D", "INTEGER", "T"));
		assertThrows(IllegalArgumentException.class, () -> sql(schema));
		schema.getDomains().clear();
		schema.getTypes().getFirst().getColumns().getFirst().getSpecifics().put("DB2_TYPE_SCHEMA", "CUSTOM");
		assertThrows(IllegalArgumentException.class, () -> sql(schema));
	}

	@Test
	void fullDefinitionsRemainAuthoritativeOverSupplementalDependencyFields() {
		var first = row("R1", "R2");
		first.setDefinition("CREATE TYPE APP.R1 AS ROW (V INTEGER)");
		var second = row("R2", "R1");
		var schema = new Schema("APP");
		schema.getTypes().addAll(List.of(second, first));
		var statements = sql(schema);
		assertTrue(statements.indexOf(first.getDefinition().getFirst()) < position(statements,
				"CREATE TYPE \"APP\".\"R2\""));
	}

	@Test
	void directTypeAndDomainCollectionsUseTheSameDependencyOrder() {
		var base = row("B", null);
		var nested = row("N", "B");
		SqlFactory<Type> types = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(nested,
				SqlType.CREATE);
		assertTrue(
				types.createSql(List.of(nested, base)).getFirst().getSqlText().startsWith("CREATE TYPE \"APP\".\"B\""));
		var distinct = domain("D", "INTEGER", "T");
		var array = domain("A", "D", "A");
		SqlFactory<Domain> domains = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(array,
				SqlType.CREATE);
		assertTrue(domains.createSql(List.of(array, distinct)).getFirst().getSqlText()
				.startsWith("CREATE TYPE \"APP\".\"D\""));
	}

	@Test
	void catalogOrdersCrossSchemaRowArrayAndModuleCursorPrerequisitesAfterXml() throws Exception {
		var base = new Schema("B\".Q");
		base.getTypes().add(row("R", null));
		var consumer = new Schema("A");
		var dependent = row("N", "R");
		dependent.getColumns().getFirst().getSpecifics().put("DB2_TYPE_SCHEMA", base.getName());
		consumer.getTypes().add(dependent);
		var array = domain("ARR", "R", "A");
		array.getSpecifics().put("SOURCESCHEMA", base.getName());
		consumer.getDomains().add(array);
		var cursor = new Type("C").setMetaType(Type.MetaType.Cursor);
		cursor.getSpecifics().putAll(Map.of("DB2_CURSOR_KIND", "S", "DB2_CURSOR_ROW_SCHEMA", base.getName(),
				"DB2_CURSOR_ROW_MODULE", "M", "DB2_CURSOR_ROW_NAME", "R"));
		consumer.getTypes().add(cursor);
		var catalog = new Catalog();
		catalog.getSchemas().addAll(List.of(consumer, base));
		Catalog restored = SchemaUtils.readXml(new StringReader(catalog.asXml()));
		SqlFactory<Catalog> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(restored,
				SqlType.CREATE);
		var statements = factory.createSql(restored).stream().map(SqlOperation::getSqlText).toList();
		assertTrue(statements.indexOf(sql(restored.getSchemas().get(base.getName())).getFirst()) < statements
				.indexOf(sql(restored.getSchemas().get("A")).getFirst()));
		assertTrue(position(statements, "CREATE TYPE \"B\"\".Q\".\"R\"") < position(statements,
				"CREATE TYPE \"A\".\"N\""));
		assertEquals("A", restored.getSchemas().getFirst().getName());
		assertEquals(catalog, restored);
	}

	@Test
	void schemaCyclesFailClearlyAndUnrelatedSchemasRetainTheirOrder() {
		var a = new Schema("A");
		var b = new Schema("B");
		var ar = row("AR", "BR");
		ar.getColumns().getFirst().getSpecifics().put("DB2_TYPE_SCHEMA", "B");
		a.getTypes().add(ar);
		var br = row("BR", "AR");
		br.getColumns().getFirst().getSpecifics().put("DB2_TYPE_SCHEMA", "A");
		b.getTypes().add(br);
		var catalog = new Catalog();
		catalog.getSchemas().addAll(List.of(a, b));
		SqlFactory<Catalog> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(catalog,
				SqlType.CREATE);
		assertTrue(assertThrows(IllegalArgumentException.class, () -> factory.createSql(catalog)).getMessage()
				.contains("Cyclic Db2 type"));
		a.getTypes().add(row("LEAF", null));
		br.getColumns().getFirst().getSpecifics().put("TYPENAME", "LEAF");
		assertTrue(assertThrows(IllegalArgumentException.class, () -> factory.createSql(catalog)).getMessage()
				.contains("interleaved"));
		br.setDefinition("CREATE TYPE B.BR AS ROW (V INTEGER)");
		assertEquals(List.of(b, a), Db2TypeCreateOrder.schemas(List.of(a, b)));
		ar.setDefinition("CREATE TYPE A.AR AS ROW (V INTEGER)");
		assertEquals(List.of(a, b), Db2TypeCreateOrder.schemas(List.of(a, b)));
		ar.getDefinition().clear();
		ar.getColumns().getFirst().getSpecifics().put("DB2_TYPE_SCHEMA", "EXTERNAL");
		assertEquals(List.of(a, b), Db2TypeCreateOrder.schemas(List.of(a, b)));
	}
}
