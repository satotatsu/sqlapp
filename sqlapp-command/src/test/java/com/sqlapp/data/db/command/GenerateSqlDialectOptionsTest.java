/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.CharacterSemantics;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

/**
 * Dialect options for SQL generated from schema objects without a connection.
 */
class GenerateSqlDialectOptionsTest {

	@Test
	void oracleMaxStringSizeOptionAppliesToXmlDerivedDialect() {
		String standard = generate(Map.of());
		assertFalse(standard.contains("VARCHAR2(5000"), standard);
		assertTrue(standard.contains("CLOB"), standard);
		String extended = generate(Map.of("oracle.maxStringSize", "EXTENDED"));
		assertTrue(extended.contains("VARCHAR2(5000 CHAR)"), extended);
	}

	@Test
	void unsupportedOptionsStopGeneration() {
		assertThrows(RuntimeException.class, () -> generate(Map.of("oracle.unknown", "x")));
		assertThrows(RuntimeException.class, () -> generate(Map.of("oracle.maxStringSize", "HUGE")));
	}

	private static String generate(final Map<String, String> options) {
		final Catalog catalog = new Catalog();
		catalog.setProductName("Oracle").setProductMajorVersion(19).setProductMinorVersion(0);
		final Schema schema = new Schema("APP");
		catalog.getSchemas().add(schema);
		final Table table = new Table("T");
		table.getColumns().add(new Column("TEXT").setDataType(DataType.VARCHAR).setLength(5000)
				.setCharacterSemantics(CharacterSemantics.Char));
		schema.getTables().add(table);
		final GenerateSimpleSqlCommand command = new GenerateSimpleSqlCommand();
		command.setTarget(catalog.getSchemas());
		command.setDialectOptions(options);
		command.run();
		return command.getSqlOperations().stream().map(operation -> operation.getSqlText())
				.collect(Collectors.joining("\n")).toUpperCase(Locale.ROOT);
	}
}
