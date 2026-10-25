/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.*;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class Db2GlobalVariableSqlTest {
	private Constant variable(String type) {
		var variable = new Constant("V.Name").setSchemaName("A.Schema");
		variable.getSpecifics().putAll(Map.of("DB2_OBJECT_TYPE", "VARIABLE", "TYPESCHEMA", "SYSIBM", "TYPENAME", type,
				"SCOPE", "S", "READONLY", "N", "LENGTH", "20", "SCALE", "0", "CODEPAGE", "1208"));
		return variable;
	}

	private List<String> sql(Constant variable, SqlType type) {
		SqlFactory<Constant> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(variable,
				type);
		return factory.createSql(variable).stream().map(o -> o.getSqlText()).toList();
	}

	@Test
	void expressionContextCommentsAndXmlAreRecreatable() throws Exception {
		var variable = variable("INTEGER");
		variable.getSpecifics().putAll(Map.of("QUALIFIER", "A.Schema", "FUNC_PATH", "\"SYSIBM\",\"A.Schema\""));
		variable.getStatement().add("(1 + 2)");
		variable.setRemarks("variable's comment");
		Constant restored = SchemaUtils.readXml(new java.io.StringReader(variable.asXml()));
		assertEquals(variable, restored);
		assertEquals(variable, variable.clone());
		var sql = sql(restored, SqlType.CREATE);
		assertEquals("SET CURRENT SCHEMA = 'A.Schema'", sql.getFirst());
		assertTrue(sql.contains("CREATE VARIABLE \"A.Schema\".\"V.Name\" INTEGER DEFAULT (1 + 2)"), sql.toString());
		assertEquals("COMMENT ON VARIABLE \"A.Schema\".\"V.Name\" IS 'variable''s comment'", sql.getLast());
		assertEquals(List.of("DROP VARIABLE \"A.Schema\".\"V.Name\" RESTRICT"), sql(variable, SqlType.DROP));
	}

	@Test
	void stringUnitsBinaryDataDecimalAndConstantValuesRetainTheirSemantics() {
		var variable = variable("VARCHAR");
		variable.getSpecifics().putAll(Map.of("TYPESTRINGUNITS", "CODEUNITS32", "STRINGUNITSLENGTH", "5"));
		assertTrue(sql(variable, SqlType.CREATE).getFirst().endsWith("VARCHAR(5 CODEUNITS32)"));
		variable = variable("VARCHAR");
		variable.getSpecifics().put("CODEPAGE", "0");
		assertTrue(sql(variable, SqlType.CREATE).getFirst().endsWith("VARCHAR(20) FOR BIT DATA"));
		variable = variable("DECIMAL");
		variable.getSpecifics().putAll(Map.of("LENGTH", "15", "SCALE", "3"));
		assertTrue(sql(variable, SqlType.CREATE).getFirst().endsWith("DECIMAL(15,3)"),
				sql(variable, SqlType.CREATE).toString());
		variable = variable("INTEGER");
		variable.getSpecifics().put("READONLY", "C");
		variable.getStatement().add("42");
		assertTrue(sql(variable, SqlType.CREATE).getFirst().endsWith("INTEGER CONSTANT 42"));
	}

	@Test
	void completeDefinitionsAreAuthoritativeAndUnsupportedComponentsCannotBeGuessed() {
		var variable = variable("CURSOR");
		assertThrows(IllegalArgumentException.class, () -> sql(variable, SqlType.CREATE));
		variable.getDefinition().add(
				"CREATE VARIABLE \"A.Schema\".\"V.Name\" CURSOR CONSTANT (CURSOR FOR SELECT 1 FROM SYSIBM.SYSDUMMY1)");
		assertEquals(variable.getDefinition(), sql(variable, SqlType.CREATE));
		variable.getDefinition().clear();
		variable.getSpecifics().put("TYPESCHEMA", "CUSTOM");
		assertThrows(IllegalArgumentException.class, () -> sql(variable, SqlType.CREATE));
		variable.getSpecifics().putAll(Map.of("TYPESCHEMA", "SYSIBM", "TYPENAME", "INTEGER", "READONLY", "C"));
		assertThrows(IllegalArgumentException.class, () -> sql(variable, SqlType.CREATE));
		SqlFactory<Constant> old = DialectHolder.Db2_1050Dialect.createSqlFactoryRegistry().getSqlFactory(variable,
				SqlType.CREATE);
		assertThrows(IllegalArgumentException.class, () -> old.createSql(variable));
	}

	@Test
	void catalogCreatesVariablesBeforeTablesAndReplaysReadWriteGrants() {
		Catalog catalog = new Catalog();
		Schema schema = new Schema("A.Schema");
		catalog.getSchemas().add(schema);
		var variable = variable("INTEGER");
		schema.getConstants().add(variable);
		Table table = new Table("T");
		table.getColumns().add("ID", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
		schema.getTables().add(table);
		Db2ObjectPrivileges.set(catalog,
				List.of(new Db2ObjectPrivileges.Grant(Db2ObjectPrivileges.Kind.VARIABLE, schema.getName(),
						variable.getName(), "READ", null, Db2ObjectPrivileges.GranteeType.PUBLIC, "PUBLIC", true)));
		var registry = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry();
		SqlFactory<Catalog> create = registry.getSqlFactory(catalog, SqlType.CREATE);
		var sql = create.createSql(catalog).stream().map(o -> o.getSqlText()).toList();
		int variableIndex = -1, tableIndex = -1;
		for (int i = 0; i < sql.size(); i++) {
			if (sql.get(i).startsWith("CREATE VARIABLE"))
				variableIndex = i;
			if (sql.get(i).startsWith("CREATE TABLE"))
				tableIndex = i;
		}
		assertTrue(variableIndex >= 0 && tableIndex > variableIndex, sql.toString());
		assertTrue(sql.getLast().startsWith("GRANT READ ON VARIABLE"));
		SqlFactory<Constant> alter = registry.getSqlFactory(variable, SqlType.ALTER);
		var changed = variable.clone();
		changed.getStatement().add("42");
		assertThrows(IllegalArgumentException.class, () -> alter.createDiffSql(variable.diff(changed)));
		assertTrue(alter.createDiffSql(variable.diff(variable.clone())).isEmpty());
	}

	@Test
	void missingDimensionsAndAmbiguousUnicodeLengthsFailClearly() {
		var text = variable("VARCHAR");
		assertThrows(IllegalArgumentException.class, () -> sql(text, SqlType.CREATE));
		text.getSpecifics().putAll(Map.of("TYPESTRINGUNITS", "CODEUNITS32", "STRINGUNITSLENGTH", "5"));
		text.getSpecifics().remove("LENGTH");
		assertThrows(IllegalArgumentException.class, () -> sql(text, SqlType.CREATE));
		var graphic = variable("GRAPHIC");
		assertThrows(IllegalArgumentException.class, () -> sql(graphic, SqlType.CREATE));
		var decimal = variable("DECIMAL");
		decimal.getSpecifics().putAll(Map.of("LENGTH", "5", "SCALE", "6"));
		assertThrows(IllegalArgumentException.class, () -> sql(decimal, SqlType.CREATE));
	}
}
