/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.sql.ParameterDirection;

class Db2RoutineArgumentSqlTest {
	private final com.sqlapp.data.db.dialect.Dialect dialect = DialectHolder.Db2_1110Dialect;

	private <T> String create(T object) {
		SqlFactory<T> factory = dialect.createSqlFactoryRegistry().getSqlFactory(object, SqlType.CREATE);
		return factory.createSql(object).getFirst().getSqlText().replaceAll("\\s+", " ");
	}

	private NamedArgument argument() {
		var argument = new NamedArgument("P").setDataType(DataType.VARCHAR).setLength(20)
				.setDirection(ParameterDirection.Input);
		argument.getSpecifics().putAll(Map.of("TYPENAME", "VARCHAR", "TYPESCHEMA", "SYSIBM", "LENGTH", "20", "CODEPAGE",
				"1208", "TYPESTRINGUNITS", "CODEUNITS32", "STRINGUNITSLENGTH", "5", "LOCATOR", "N"));
		return argument;
	}

	@Test
	void procedureAndFunctionUseDeclaredUnitsAfterXml() throws Exception {
		var procedure = new Procedure("P").setSchemaName("APP").setStatement("BEGIN END");
		procedure.getArguments().add(argument());
		assertTrue(create(procedure).contains("IN P VARCHAR(5 CODEUNITS32)"));
		Schema schema = new Schema("APP");
		schema.getProcedures().add(procedure);
		Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
		assertTrue(create(restored.getProcedures().getFirst()).contains("IN P VARCHAR(5 CODEUNITS32)"));
		var function = new Function("F").setSchemaName("APP").setStatement("RETURN 1");
		function.getReturning().setDataType(DataType.INT);
		function.getArguments().add(argument());
		assertTrue(create(function).contains("P VARCHAR(5 CODEUNITS32)"));
		assertFalse(create(function).contains("IN P"));
	}

	@Test
	void userTypeAndModuleIdentityAreQuotedEvenWhenNamedLikeBuiltins() {
		var procedure = new Procedure("P").setSchemaName("APP").setStatement("BEGIN END");
		var argument = argument();
		procedure.getArguments().add(argument);
		argument.getSpecifics().put("TYPESCHEMA", "Type.Schema");
		assertTrue(create(procedure).contains("\"Type.Schema\".\"VARCHAR\""));
		argument.getSpecifics().put("TYPEMODULENAME", "M\"Q");
		assertTrue(create(procedure).contains("\"Type.Schema\".\"M\"\"Q\".\"VARCHAR\""));
		assertEquals(Long.valueOf(20), argument.getLength());
	}

	@Test
	void ambiguousUnitsAndLocatorArgumentsRequireCompleteDefinition() {
		var procedure = new Procedure("P").setSchemaName("APP").setStatement("BEGIN END");
		var argument = argument();
		procedure.getArguments().add(argument);
		argument.getSpecifics().remove("STRINGUNITSLENGTH");
		assertThrows(IllegalArgumentException.class, () -> create(procedure));
		argument.getSpecifics().put("STRINGUNITSLENGTH", "5");
		argument.getSpecifics().put("LOCATOR", "Y");
		assertThrows(IllegalArgumentException.class, () -> create(procedure));
		procedure.setDefinition("CREATE PROCEDURE APP.P() LANGUAGE SQL BEGIN END");
		assertEquals(procedure.getDefinition().getFirst(), create(procedure));
	}

	@Test
	void inputDefaultsSurviveRecreationAndOutputDefaultsAreRejected() {
		var procedure = new Procedure("P").setSchemaName("APP").setStatement("BEGIN END");
		var argument = argument().setDefaultValue("'O''Brien'");
		procedure.getArguments().add(argument);
		assertTrue(create(procedure).contains("VARCHAR(5 CODEUNITS32) DEFAULT 'O''Brien'"));
		argument.setDirection(ParameterDirection.Output);
		assertThrows(IllegalArgumentException.class, () -> create(procedure));
		argument.setDirection(ParameterDirection.Input).setDefaultValue("NULL");
		var function = new Function("F").setSchemaName("APP").setStatement("RETURN 1");
		function.getReturning().setDataType(DataType.INT);
		function.getArguments().add(argument.clone());
		assertTrue(create(function).contains("DEFAULT NULL"));
		function.setFunctionType(FunctionType.Row);
		assertThrows(IllegalArgumentException.class, () -> create(function));
		argument.getSpecifics().clear();
		argument.setDataType(DataType.INT).setDefaultValue("1");
		assertTrue(create(procedure).contains("INTEGER DEFAULT 1"));
	}

	@Test
	void scalarAndTableReturnsRetainNativeUnitsAndQualifiedTypesAfterXml() throws Exception {
		var function = new Function("F").setSchemaName("APP").setStatement("RETURN 'a'");
		function.getReturning().setDataType(DataType.VARCHAR).setLength(20);
		function.getReturning().getSpecifics().putAll(argument().getSpecifics());
		assertTrue(create(function).contains("RETURNS VARCHAR(5 CODEUNITS32)"));
		Schema schema = new Schema("APP");
		schema.getFunctions().add(function);
		Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
		assertTrue(create(restored.getFunctions().getFirst()).contains("RETURNS VARCHAR(5 CODEUNITS32)"));
		function.getReturning().getSpecifics().put("TYPESCHEMA", "Type.Schema");
		assertTrue(create(function).contains("RETURNS \"Type.Schema\".\"VARCHAR\""));
		function.setFunctionType(FunctionType.Table);
		function.getReturning().toTable();
		var column = new Column("C.with.dot").setDataType(DataType.VARCHAR).setLength(20);
		column.getSpecifics().putAll(argument().getSpecifics());
		function.getReturning().getTable().getColumns().add(column);
		assertTrue(create(function).contains("\"C.with.dot\" VARCHAR(5 CODEUNITS32)"));
		column.getSpecifics().put("TYPESCHEMA", "Other");
		column.getSpecifics().put("TYPEMODULENAME", "M");
		assertTrue(create(function).contains("\"Other\".\"M\".\"VARCHAR\""));
		column.getSpecifics().put("LOCATOR", "Y");
		assertThrows(IllegalArgumentException.class, () -> create(function));
		function.setDefinition("CREATE FUNCTION APP.F() RETURNS INTEGER LANGUAGE SQL RETURN 1");
		assertEquals(function.getDefinition().getFirst(), create(function));
	}
}
