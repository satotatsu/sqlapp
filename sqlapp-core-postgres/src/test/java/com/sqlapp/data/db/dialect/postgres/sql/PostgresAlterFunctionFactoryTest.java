/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.OnNullCall;
import com.sqlapp.data.schemas.SqlSecurity;

class PostgresAlterFunctionFactoryTest {
	private Function function() {
		Function function = new Function("f.name").setSchemaName("Other Schema").setLanguage("sql")
				.setStatement("SELECT 1").setRemarks("before").setDeterministic(false);
		function.getArguments().add(new NamedArgument("value").setDataType(DataType.UUID).setArrayDimension(1));
		function.getReturning().setDataType(DataType.INT);
		return function;
	}

	@Test
	void changesAttributesAndClearsCommentsThroughDifferenceRegistry() {
		for (int major : new int[] { 8, 11, 15, 18 }) {
			var registry = DialectResolver.getInstance().getDialect("postgres", major, major == 8 ? 3 : 0, null).createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			Function original = function();
			Function target = original.clone().setDeterministic(null).setStable(true)
					.setOnNullCall(OnNullCall.ReturnsNullOnNullInput).setSqlSecurity(SqlSecurity.Definer).setRemarks(null);
			var operations = registry.createSql(original.diff(target));
			assertEquals(2, operations.size());
			assertEquals("ALTER FUNCTION \"Other Schema\".\"f.name\"(UUID[]) STABLE RETURNS NULL ON NULL INPUT SECURITY DEFINER", operations.get(0).getSqlText());
			assertEquals(SqlType.ALTER, operations.get(0).getSqlType());
			assertEquals("COMMENT ON FUNCTION \"Other Schema\".\"f.name\"(UUID[]) IS NULL", operations.get(1).getSqlText());
			var reverse = registry.createSql(target.diff(original));
			assertTrue(reverse.get(0).getSqlText().endsWith("VOLATILE CALLED ON NULL INPUT SECURITY INVOKER"));
			assertTrue(reverse.get(1).getSqlText().endsWith("'before'"));
			assertTrue(registry.createSql(original.diff(original.clone()), SqlType.ALTER).isEmpty());
		}
	}

	@Test
	void rejectsUnsupportedChangesBeforeReturningAnySql() {
		var registry = DialectResolver.getInstance().getDialect("postgres", 15, 0, null).createSqlFactoryRegistry();
		Function original = function();
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(original.diff(original.clone().setStatement("SELECT 2").setRemarks("after"))));
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(original.diff(original.clone().setName("renamed"))));
		Function target = original.clone(); target.getReturning().setDataType(DataType.BIGINT);
		Function changedReturning = target;
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(original.diff(changedReturning)));
		target = original.clone(); target.getArguments().get(0).setArrayDimension(0);
		Function changedArguments = target;
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(original.diff(changedArguments)));
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(original, SqlType.ALTER));
	}

	@Test
	void changesDefinitionBackedProcedureSecurityAndCommentsWithoutReplayingDefinition() {
		var registry = DialectResolver.getInstance().getDialect("postgres", 11, 0, null).createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(false);
		Function original = function().setDefinition("CREATE PROCEDURE ignored() LANGUAGE SQL AS 'SELECT 1'");
		original.getSpecifics().put("ROUTINE_KIND", "PROCEDURE");
		Function target = original.clone().setSqlSecurity(SqlSecurity.Definer).setRemarks("after");
		var operations = registry.createSql(original.diff(target));
		assertTrue(operations.get(0).getSqlText().startsWith("ALTER PROCEDURE "));
		assertTrue(operations.get(1).getSqlText().startsWith("COMMENT ON PROCEDURE "));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(original.clone().setDeterministic(true))));
		var oldRegistry = DialectResolver.getInstance().getDialect("postgres", 10, 0, null).createSqlFactoryRegistry();
		assertThrows(UnsupportedOperationException.class, () -> oldRegistry.createSql(original.diff(target)));
	}
}
