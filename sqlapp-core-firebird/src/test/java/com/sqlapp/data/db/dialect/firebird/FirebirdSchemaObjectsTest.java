/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;

class FirebirdSchemaObjectsTest {
	private FirebirdSchemaObject exception(String name) {
		return new FirebirdSchemaObject(FirebirdSchemaObject.Kind.EXCEPTION, name,
				List.of("CREATE EXCEPTION \"" + name + "\" 'message'"), "DROP EXCEPTION \"" + name + "\"");
	}

	@Test
	void xmlCloneAndRegistryRetainExecutableDefinitions() throws Exception {
		var schema = new Schema();
		var object = exception("Mixed case");
		FirebirdSchemaObjects.set(schema, List.of(object));
		assertEquals(List.of(object), FirebirdSchemaObjects.get(schema.clone()));
		Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
		assertEquals(List.of(object), FirebirdSchemaObjects.get(restored));
		assertEquals(object.definition(), DialectHolder.defaultDialect50.createSqlFactoryRegistry()
				.createSql(restored, SqlType.CREATE).stream().map(sql -> sql.getSqlText()).toList());
		assertEquals(List.of(object.dropDefinition()), FirebirdSchemaObjects.dropSql(restored));
		restored.getProcedures()
				.add(new com.sqlapp.data.schemas.Procedure("P").setStatement("BEGIN EXCEPTION \"Mixed case\"; END"));
		for (var dialect : List.of(DialectHolder.defaultDialect25, DialectHolder.defaultDialect30,
				DialectHolder.defaultDialect40, DialectHolder.defaultDialect50)) {
			var sql = dialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE);
			assertEquals(object.definition().get(0), sql.get(0).getSqlText());
			assertTrue(sql.get(1).getSqlText().contains("PROCEDURE"));
		}
	}

	@Test
	void invalidManifestsAndDuplicatesFailWithoutMutating() {
		var schema = new Schema();
		var object = exception("x");
		FirebirdSchemaObjects.set(schema, List.of(object));
		assertThrows(IllegalArgumentException.class, () -> FirebirdSchemaObjects.set(schema, List.of(object, object)));
		assertEquals(List.of(object), FirebirdSchemaObjects.get(schema));
		for (String json : List.of("{\"format\":2,\"objects\":[]}", "{\"format\":1,\"objects\":[],\"extra\":true}")) {
			schema.getSpecifics().put(FirebirdSchemaObjects.SPECIFICS_KEY, json);
			assertThrows(RuntimeException.class, () -> FirebirdSchemaObjects.get(schema));
		}
		assertThrows(IllegalArgumentException.class, () -> exception("\0"));
		assertThrows(IllegalArgumentException.class, () -> new FirebirdSchemaObject(FirebirdSchemaObject.Kind.EXCEPTION,
				"x", List.of(), "DROP EXCEPTION x"));
	}

	@Test
	void additionsUseSchemaAlterButReplacementRequiresExplicitMigration() {
		var original = new Schema();
		var target = original.clone();
		FirebirdSchemaObjects.set(target, List.of(exception("x")));
		var factory = DialectHolder.defaultDialect50.createSqlFactoryRegistry().getSqlFactory(original, SqlType.ALTER);
		assertEquals(exception("x").definition(),
				factory.createDiffSql(original.diff(target)).stream().map(s -> s.getSqlText()).toList());
		assertThrows(UnsupportedOperationException.class, () -> factory.createDiffSql(target.diff(original)));
	}

	@Test
	void collationsRoundTripThroughXmlAndPrecedeDependentDomains() throws Exception {
		var object = new FirebirdSchemaObject(FirebirdSchemaObject.Kind.COLLATION, "custom collation",
				List.of("CREATE COLLATION \"custom collation\" FOR UTF8 FROM UNICODE NO PAD CASE INSENSITIVE"),
				"DROP COLLATION \"custom collation\"");
		var schema = new Schema();
		FirebirdSchemaObjects.set(schema, List.of(object));
		schema.getDomains()
				.add(new com.sqlapp.data.schemas.Domain("D").setDataType(com.sqlapp.data.db.datatype.DataType.VARCHAR)
						.setLength(20).setCharacterSet("UTF8").setCollation("custom collation"));
		Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
		assertEquals(List.of(object), FirebirdSchemaObjects.get(restored));
		for (var dialect : List.of(DialectHolder.defaultDialect25, DialectHolder.defaultDialect30,
				DialectHolder.defaultDialect40, DialectHolder.defaultDialect50)) {
			var sql = dialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE);
			assertEquals(object.definition().get(0), sql.get(0).getSqlText());
			assertTrue(sql.get(1).getSqlText().contains("CREATE DOMAIN"));
			assertTrue(sql.get(1).getSqlText().contains("COLLATE \"custom collation\""));
			var factory = dialect.createSqlFactoryRegistry().getSqlFactory(new Schema(), SqlType.ALTER);
			assertEquals(object.definition().get(0),
					factory.createDiffSql(new Schema().diff(restored)).get(0).getSqlText());
			assertThrows(UnsupportedOperationException.class, () -> factory.createDiffSql(restored.diff(new Schema())));
		}
	}
}
