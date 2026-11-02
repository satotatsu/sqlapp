/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class MariadbRoutineCompletenessTest {
	@Test
	void missingBodyOrReturnTypeFailsAcrossSupportedRegistryVersions() {
		for (int[] version : new int[][] { { 10, 3 }, { 10, 11 }, { 11, 3 }, { 11, 4 }, { 11, 5 }, { 12, 1 } }) {
			var registry = DialectResolver.getInstance().getDialect("MariaDB", version[0], version[1], 0)
					.createSqlFactoryRegistry();
			for (String body : new String[] { null, "", " \n " }) {
				Procedure procedure = new Procedure("missing_body");
				procedure.setStatement(body);
				assertThrows(IllegalArgumentException.class, () -> registry.createSql(procedure, SqlType.CREATE));
				Function function = new Function("missing_body");
				function.setStatement(body);
				function.getReturning().setDataType(DataType.INT);
				assertThrows(IllegalArgumentException.class, () -> registry.createSql(function, SqlType.CREATE));
			}
			Function noReturn = new Function("missing_return");
			noReturn.setStatement("RETURN 1");
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(noReturn, SqlType.CREATE));
		}
	}

	@Test
	void blankDefinitionsCannotBypassBodyOrReturnValidation() {
		var registry = DialectResolver.getInstance().getDialect("MariaDB", 11, 5, 0).createSqlFactoryRegistry();
		Procedure procedure = new Procedure("blank_definition");
		procedure.setDefinition("  \n ");
		procedure.setStatement("BEGIN SELECT 1; END");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(procedure, SqlType.CREATE));
		Function function = new Function("blank_definition");
		function.setDefinition("  \n ");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(function, SqlType.CREATE));
		function.setDefinition((String) null);
		function.setStatement("RETURN 1");
		function.getReturning().setDefinition("  ");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(function, SqlType.CREATE));
	}

	@Test
	void completeDefinitionsRecreateWithoutDuplicateBodyAndArgumentsAfterXml() throws Exception {
		for (int[] version : new int[][] { { 10, 3 }, { 11, 4 }, { 11, 5 }, { 12, 1 } }) {
			var registry = DialectResolver.getInstance().getDialect("MariaDB", version[0], version[1], 0)
					.createSqlFactoryRegistry();
			Procedure procedure = new Procedure("p");
			String procedureDdl = "CREATE PROCEDURE p() BEGIN SELECT 1; END";
			procedure.setDefinition(procedureDdl);
			procedure.setStatement("BEGIN SELECT 'supplemental'; END");
			procedure.getArguments().add(new NamedArgument("supplemental_argument"));
			Procedure restoredProcedure = SchemaUtils.readXml(new java.io.StringReader(procedure.asXml()));
			assertEquals(procedureDdl, registry.createSql(restoredProcedure, SqlType.CREATE).get(0).getSqlText());
			Function function = new Function("f");
			String functionDdl = "CREATE FUNCTION f() RETURNS INT RETURN 1";
			function.setDefinition(functionDdl);
			function.setStatement("RETURN 'supplemental'");
			Function restoredFunction = SchemaUtils.readXml(new java.io.StringReader(function.asXml()));
			assertEquals(functionDdl, registry.createSql(restoredFunction, SqlType.CREATE).get(0).getSqlText());
		}
	}
}
