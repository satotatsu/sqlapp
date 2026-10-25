/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.schemas.Package;

class Db2DefinitionSqlTest {
	@Test
	void multilineNativeDefinitionsRemainOneOperationAfterXmlForEveryFactory() throws Exception {
		var domain = new Domain("D").setSchemaName("APP");
		domain.getSpecifics().put("DB2_OBJECT_TYPE", "DATA_TYPE");
		var variable = new Constant("V").setSchemaName("APP");
		variable.getSpecifics().put("DB2_OBJECT_TYPE", "VARIABLE");
		var module = new Package("M").setSchemaName("APP");
		module.getSpecifics().putAll(Map.of("DB2_OBJECT_TYPE", "MODULE", "MODULETYPE", "M"));
		List<AbstractNamedObject<?>> objects = List.of(new Table("T"), new View("VW"), new Mview("MQ"),
				new Type("RT").setSchemaName("APP"), domain, variable, module, new Function("F"), new Procedure("P"));
		var texts = List.of("CREATE TABLE T (\n\n ID INTEGER\n)", "CREATE VIEW VW AS\n\nSELECT 1 FROM SYSIBM.SYSDUMMY1",
				"CREATE TABLE MQ AS (\nSELECT 1 FROM SYSIBM.SYSDUMMY1\n) DATA INITIALLY DEFERRED REFRESH DEFERRED",
				"CREATE TYPE APP.RT AS ROW (\n ID INTEGER\n)", "CREATE TYPE APP.D AS\n INTEGER WITH COMPARISONS",
				"CREATE VARIABLE APP.V\n INTEGER DEFAULT 1", "CREATE MODULE\n APP.M",
				"CREATE FUNCTION F() RETURNS INTEGER LANGUAGE SQL\nBEGIN ATOMIC\n\n RETURN 1;\nEND",
				"CREATE PROCEDURE P() LANGUAGE SQL\nBEGIN\n\n VALUES 'semi;colon';\nEND");
		for (int i = 0; i < objects.size(); i++) {
			var object = objects.get(i);
			String text = texts.get(i);
			object.setDefinition(text);
			Object restored = SchemaUtils.readXml(new StringReader(object.asXml()));
			SqlFactory<Object> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry()
					.getSqlFactory(restored, SqlType.CREATE);
			var operations = factory.createSql(restored);
			assertEquals(1, operations.size(), object.getClass().getName());
			assertEquals(text, operations.getFirst().getSqlText());
		}
		Table table = new Table("T").setDefinition("CREATE TABLE T (ID INTEGER)");
		String indexDdl = "CREATE INDEX I ON T (\n\n ID\n)";
		table.getIndexes().add(new Index("I").setDefinition(indexDdl));
		Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
		SqlFactory<Index> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry()
				.getSqlFactory(restored.getIndexes().getFirst(), SqlType.CREATE);
		assertEquals(indexDdl, factory.createSql(restored.getIndexes().getFirst()).getFirst().getSqlText());
	}

	@Test
	void explicitScriptsPreserveInternalSemicolonsAndIgnoreDirectiveInsideLiteral() {
		String body = "CREATE PROCEDURE APP.P() LANGUAGE SQL\nBEGIN\n VALUES 'a;b@c';\n VALUES 2;\nEND";
		var dialect = DialectHolder.Db2_1110Dialect;
		assertEquals(List.of(body, "COMMENT ON PROCEDURE APP.P IS 'test'"), Db2DefinitionSql.statements(
				List.of("--#SET TERMINATOR @", body + "@", "COMMENT ON PROCEDURE APP.P IS 'test'@"), dialect));
		String literal = "CREATE VIEW V AS SELECT '\n--#SET TERMINATOR @\n' FROM SYSIBM.SYSDUMMY1";
		assertEquals(List.of(literal), Db2DefinitionSql.statements(List.of(literal), dialect));
	}

	@Test
	void emptyNullAndNulTextAreRejectedButBlankLinesArePreserved() {
		assertEquals("BEGIN\n\nEND", Db2DefinitionSql.text(List.of("BEGIN", "", "END")));
		for (var lines : List.of(List.<String>of(), List.of(" "), List.of("a\0"), Arrays.asList("a", null)))
			assertThrows(IllegalArgumentException.class, () -> Db2DefinitionSql.text(lines));
		assertThrows(IllegalArgumentException.class,
				() -> Db2DefinitionSql.statements(List.of("--#SET TERMINATOR @"), DialectHolder.Db2_1110Dialect));
	}
}
