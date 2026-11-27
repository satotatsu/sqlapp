/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.*;
import com.sqlapp.data.db.dialect.db2.Db2FunctionMappings.*;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

class Db2FunctionMappingSqlTest {
	private Mapping mapping(String name) {
		return new Mapping(name, "A.Schema", "F", "F\"1", "S.Name", null, null, null, false,
				Map.of("REMOTE_NAME", "r's.f"), List.of(), "mapping's comment", null);
	}

	private Catalog catalog(Mapping... mappings) {
		var catalog = new Catalog();
		Db2FunctionMappings.set(catalog, List.of(mappings));
		return catalog;
	}

	@Test
	void specificTargetsOptionsAndCommentsUseNativeQuoting() {
		var sql = Db2FunctionMappingSql.create(catalog(mapping("M")), DialectHolder.Db2_1110Dialect);
		assertEquals(
				"CREATE FUNCTION MAPPING \"M\" FOR SPECIFIC \"A.Schema\".\"F\"\"1\" SERVER \"S.Name\" OPTIONS (REMOTE_NAME 'r''s.f')",
				sql.getFirst());
		assertEquals("COMMENT ON FUNCTION MAPPING \"M\" IS 'mapping''s comment'", sql.getLast());
		var category = new Mapping("C", "APP", "F", "F1", null, "ORACLE", "8i", "W", true, Map.of(), List.of(), null,
				null);
		assertTrue(Db2FunctionMappingSql.create(catalog(category), DialectHolder.Db2_1110Dialect).getFirst()
				.endsWith("SERVER TYPE \"ORACLE\" VERSION '8i' WRAPPER \"W\" WITH INFIX"));
	}

	@Test
	void incompleteNotationBuiltinSignaturesAndParameterOptionsRequireResolution() {
		var unknown = new Mapping("U", "APP", "F", "F1", "S", null, null, null, null, Map.of(), List.of(), null, null);
		assertThrows(IllegalArgumentException.class,
				() -> Db2FunctionMappingSql.create(catalog(unknown), DialectHolder.Db2_1110Dialect));
		assertFalse(Db2FunctionMappingSql.create(catalog(unknown.withInfix(false)), DialectHolder.Db2_1110Dialect)
				.isEmpty());
		var builtin = new Mapping("B", null, "LENGTH", null, "S", null, null, null, false, Map.of(),
				List.of(new ParameterOption(1, "L", "CUSTOM", "Y")), null, null);
		assertThrows(IllegalArgumentException.class,
				() -> Db2FunctionMappingSql.create(catalog(builtin), DialectHolder.Db2_1110Dialect));
		String ddl = "CREATE FUNCTION MAPPING B FOR SYSIBM.LENGTH(VARCHAR()) SERVER S OPTIONS (DISABLE 'Y')";
		assertEquals(List.of(ddl),
				Db2FunctionMappingSql.create(catalog(builtin.withDefinition(ddl)), DialectHolder.Db2_1110Dialect));
		assertThrows(IllegalArgumentException.class,
				() -> Db2FunctionMappingSql.create(catalog(mapping("M")), DialectHolder.Db2_1050Dialect));
	}

	@Test
	void precedenceAllowsOnlyAppendAndCatalogDiffDoesNotReprocessExtension() {
		var original = catalog(mapping("Z"));
		var target = catalog(mapping("Z"), mapping("A"));
		assertTrue(Db2FunctionMappingSql.diff(original, original.clone(), DialectHolder.Db2_1110Dialect).isEmpty());
		assertEquals(2, Db2FunctionMappingSql.diff(original, target, DialectHolder.Db2_1110Dialect).size());
		assertThrows(IllegalArgumentException.class,
				() -> Db2FunctionMappingSql.diff(target, original, DialectHolder.Db2_1110Dialect));
		assertThrows(IllegalArgumentException.class, () -> Db2FunctionMappingSql.diff(target,
				catalog(mapping("A"), mapping("Z")), DialectHolder.Db2_1110Dialect));
		SqlFactory<Catalog> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(target,
				SqlType.ALTER);
		var sql = factory.createDiffSql(original.diff(target)).stream().map(SqlOperation::getSqlText).toList();
		assertEquals(1, sql.stream().filter(s -> s.startsWith("CREATE FUNCTION MAPPING")).count());
	}

	@Test
	void catalogCreatesFunctionBeforeItsMapping() {
		var catalog = catalog(mapping("M"));
		var schema = new Schema("A.Schema");
		catalog.getSchemas().add(schema);
		var function = new Function("F");
		function.getDefinition().add("CREATE FUNCTION \"A.Schema\".F() RETURNS INT RETURN 1");
		schema.getFunctions().add(function);
		SqlFactory<Catalog> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(catalog,
				SqlType.CREATE);
		var sql = factory.createSql(catalog).stream().map(SqlOperation::getSqlText).toList();
		assertTrue(
				sql.indexOf(function.getDefinition().getFirst()) < sql
						.indexOf(Db2FunctionMappingSql.create(catalog, DialectHolder.Db2_1110Dialect).getFirst()),
				sql.toString());
	}

	@Test
	void ambiguousCatalogOrderRequiresExplicitResolutionEvenWithCompleteDefinitions() {
		var catalog = new Catalog();
		Db2FunctionMappings.set(catalog, new Snapshot(1, List.of(mapping("Z"), mapping("A")), false));
		assertThrows(IllegalArgumentException.class,
				() -> Db2FunctionMappingSql.create(catalog, DialectHolder.Db2_1110Dialect));
		assertTrue(Db2FunctionMappingSql.diff(catalog, catalog.clone(), DialectHolder.Db2_1110Dialect).isEmpty());
		Db2FunctionMappings.set(catalog, Db2FunctionMappings.get(catalog).mappings());
		assertEquals(4, Db2FunctionMappingSql.create(catalog, DialectHolder.Db2_1110Dialect).size());
	}
}
