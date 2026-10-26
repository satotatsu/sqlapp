/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.db.sql.SqlType;

class FirebirdPublicationsTest {
	private Schema schema() {
		var schema = new Schema();
		schema.getTables().add(new Table("included\" table"));
		schema.getTables().add(new Table("excluded table"));
		FirebirdPublications.set(schema, new FirebirdPublication(true, true, List.of("included\" table")));
		return schema;
	}

	@Test
	void xmlCloneAndSafeRestoreKeepActivationExplicit() throws Exception {
		assertEquals(new FirebirdPublication(false, false, List.of("a", "b")),
				new FirebirdPublication(false, false, List.of("b", "a")));
		Schema source = schema(), restored = SchemaUtils.readXml(new StringReader(source.asXml()));
		assertEquals(FirebirdPublications.get(source), FirebirdPublications.get(restored));
		assertEquals(FirebirdPublications.get(source), FirebirdPublications.get(source.clone()));
		assertEquals(List.of("ALTER DATABASE DISABLE PUBLICATION", "ALTER DATABASE EXCLUDE ALL FROM PUBLICATION",
				"ALTER DATABASE INCLUDE ALL TO PUBLICATION", "ALTER TABLE \"excluded table\" DISABLE PUBLICATION"),
				FirebirdPublications.restoreSql(restored, DialectHolder.defaultDialect40));
		var active = FirebirdPublications.restoreSql(restored, DialectHolder.defaultDialect50,
				FirebirdPublications.Activation.SOURCE_STATE);
		assertEquals("ALTER DATABASE ENABLE PUBLICATION", active.getLast());
		FirebirdPublications.set(restored, new FirebirdPublication(false, false, List.of("included\" table")));
		assertEquals(
				List.of("ALTER DATABASE DISABLE PUBLICATION", "ALTER DATABASE EXCLUDE ALL FROM PUBLICATION",
						"ALTER TABLE \"included\"\" table\" ENABLE PUBLICATION"),
				FirebirdPublications.restoreSql(restored, DialectHolder.defaultDialect50,
						FirebirdPublications.Activation.SOURCE_STATE));
	}

	@Test
	void invalidPartialOrUnsupportedTargetsFailBeforeReturningSql() {
		Schema schema = schema();
		assertThrows(UnsupportedOperationException.class,
				() -> FirebirdPublications.restoreSql(schema, DialectHolder.defaultDialect30));
		schema.getTables().clear();
		assertThrows(IllegalArgumentException.class,
				() -> FirebirdPublications.restoreSql(schema, DialectHolder.defaultDialect40));
		schema.getTables().add(new Table("included\" table").setTableType(Table.TableType.Temporary));
		assertThrows(IllegalArgumentException.class,
				() -> FirebirdPublications.restoreSql(schema, DialectHolder.defaultDialect40));
		schema.getTables().get(0).setTableType((Table.TableType) null).getSpecifics().put("firebird.external.file",
				"file.dat");
		assertThrows(IllegalArgumentException.class,
				() -> FirebirdPublications.restoreSql(schema, DialectHolder.defaultDialect40));
		assertThrows(IllegalArgumentException.class, () -> new FirebirdPublication(false, false, List.of("x", "x")));
		assertThrows(IllegalArgumentException.class, () -> new FirebirdPublication(false, false, List.of("\0")));
		assertTrue(FirebirdPublications.restoreSql(new Schema(), DialectHolder.defaultDialect25).isEmpty());
	}

	@Test
	void manifestsAreStrictAndOrdinarySchemaAlterCannotHidePolicyChanges() {
		for (String json : List.of(
				"{\"format\":2,\"publication\":{\"active\":false,\"autoEnable\":false,\"tables\":[]}}",
				"{\"format\":1,\"publication\":{\"tables\":[]}}",
				"{\"format\":1,\"publication\":{\"active\":null,\"autoEnable\":false,\"tables\":[]}}",
				"{\"format\":1,\"publication\":{\"active\":false,\"autoEnable\":false,\"tables\":[],\"unknown\":true}}")) {
			var schema = new Schema();
			schema.getSpecifics().put(FirebirdPublications.SPECIFICS_KEY, json);
			assertThrows(RuntimeException.class, () -> FirebirdPublications.get(schema));
			assertThrows(RuntimeException.class,
					() -> DialectHolder.defaultDialect40.createSqlFactoryRegistry().createSql(schema, SqlType.CREATE));
		}
		Schema source = schema(), target = source.clone();
		FirebirdPublications.set(target, new FirebirdPublication(false, true, List.of("included\" table")));
		var factory = DialectHolder.defaultDialect40.createSqlFactoryRegistry().getSqlFactory(source, SqlType.ALTER);
		assertThrows(UnsupportedOperationException.class, () -> factory.createDiffSql(source.diff(target)));
		assertTrue(factory.createDiffSql(source.diff(source.clone())).isEmpty());
		var explicit = (com.sqlapp.data.db.dialect.firebird.sql.FirebirdAlterSchemaFactory) factory;
		assertEquals(FirebirdPublications.restoreSql(target, DialectHolder.defaultDialect40),
				explicit.createDiffSql(source.diff(target), FirebirdPublications.Activation.DISABLED).stream()
						.map(sql -> sql.getSqlText()).toList());
		assertThrows(IllegalArgumentException.class,
				() -> explicit.createDiffSql(source.diff(new Schema()), FirebirdPublications.Activation.DISABLED));
		target.getTables().add(new Table("NEW_TABLE"));
		target.getTables().get("NEW_TABLE").getColumns()
				.add(new Column("ID").setDataType(com.sqlapp.data.db.datatype.DataType.INT));
		FirebirdPublications.set(target, new FirebirdPublication(true, false, List.of("NEW_TABLE")));
		var plan = explicit.createDiffSql(source.diff(target), FirebirdPublications.Activation.SOURCE_STATE).stream()
				.map(sql -> sql.getSqlText()).toList();
		assertEquals("ALTER DATABASE DISABLE PUBLICATION", plan.getFirst());
		assertTrue(plan.get(1).contains("CREATE TABLE"));
		assertEquals("ALTER DATABASE ENABLE PUBLICATION", plan.getLast());
	}
}
