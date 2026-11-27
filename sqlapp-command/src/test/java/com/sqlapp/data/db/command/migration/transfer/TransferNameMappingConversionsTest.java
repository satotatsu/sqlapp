/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Column definitions in the transfer mapping: rename shorthand, type and
 * conversion.
 */
class TransferNameMappingConversionsTest {
	private final TransferNameMappingIO io = new TransferNameMappingIO();

	@Test
	void readsShorthandAndObjectColumnsAndFormatsRenamesAsPlainNames() {
		var configuration = io.validate("""
				formatVersion: 1
				tables:
				  CUSTOMERS:
				    columns:
				      NAME: "氏名"
				      STATUS: {type: "varchar(10)", conversion: "value == 'A' ? 'ACTIVE' : 'INACTIVE'"}
				      NOTE: {name: MEMO, conversion: "value.trim()"}
				  ORDERS: {targetTable: "注文"}
				""");
		var customers = configuration.tables().get("CUSTOMERS");
		assertEquals(TransferNameMappingIO.ColumnMapping.rename("氏名"), customers.columns().get("NAME"));
		assertEquals(
				new TransferNameMappingIO.ColumnMapping(null, "varchar(10)", "value == 'A' ? 'ACTIVE' : 'INACTIVE'"),
				customers.columns().get("STATUS"));
		assertEquals(Map.of("NAME", "氏名", "NOTE", "MEMO"), customers.names());
		assertTrue(configuration.hasTypesOrConversions());
		assertFalse(configuration.tables().get("ORDERS").hasTypesOrConversions());
		var formatted = io.format(configuration);
		assertTrue(formatted.contains("NAME: \"氏名\"") || formatted.contains("NAME: 氏名"), formatted);
		assertFalse(formatted.contains("name: \"氏名\""), formatted);
		assertEquals(configuration, io.validate(formatted));
	}

	@Test
	void editingNamesKeepsTypesAndConversions() {
		var mapping = io.validate("""
				formatVersion: 1
				tables:
				  CUSTOMERS:
				    columns:
				      NAME: N
				      STATUS: {name: S, type: "CHAR(2)"}
				      NOTE: {conversion: "value"}
				""").tables().get("CUSTOMERS");
		var renamed = mapping.withTargetTable("顧客").withNames(Map.of("NAME", "N2", "NOTE", "MEMO"));
		assertEquals("顧客", renamed.targetTable());
		assertEquals(TransferNameMappingIO.ColumnMapping.rename("N2"), renamed.columns().get("NAME"));
		// STATUS lost its rename but keeps its type; NOTE gains a rename.
		assertEquals(new TransferNameMappingIO.ColumnMapping(null, "CHAR(2)", null), renamed.columns().get("STATUS"));
		assertEquals(new TransferNameMappingIO.ColumnMapping("MEMO", null, "value"), renamed.columns().get("NOTE"));
		assertFalse(mapping.withNames(Map.of()).columns().containsKey("NAME"));
	}

	@Test
	void rejectsInvalidColumnDefinitions() {
		for (String invalid : List.of("NAME: 1", "NAME: ''", "NAME: {}", "NAME: {name: 1}", "NAME: {conversion: ''}",
				"NAME: {conversion: \"a\\u0000\"}", "NAME: {type: VARCHARX(1)}", "NAME: {type: \"VARCHAR(\"}",
				"NAME: {type: OTHER}", "NAME: {other: x}", "'': N"))
			assertThrows(IllegalArgumentException.class,
					() -> io.validate("formatVersion: 1\ntables:\n  CUSTOMERS:\n    columns: {" + invalid + "}\n"),
					invalid);
		assertEquals("value\n  .trim()", io.validate(
				"formatVersion: 1\ntables:\n  CUSTOMERS:\n    columns:\n      NAME: {conversion: \"value\\n  .trim()\"}\n")
				.tables().get("CUSTOMERS").columns().get("NAME").conversion());
	}
}
