/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlTerminator;

class TransferSchemaSqlScriptTest {
	private static Dialect basic() {
		return new Dialect(null) {
		};
	}

	@Test
	void serializesStatementsAndWarningsWithoutTurningWarningLinesIntoSql() {
		var plan = new TransferSchemaPreparation.Plan("plan",
				List.of(new TransferSchemaPreparation.Statement("table-1", "CREATE TABLE ITEMS(ID INTEGER)"),
						new TransferSchemaPreparation.Statement("table-1", "ALTER TABLE ITEMS ADD NAME VARCHAR(40);")),
				List.of("Review default\nDROP TABLE SECRET"));
		var text = TransferSchemaSqlScript.render(plan, basic(), "job");
		assertTrue(text.contains("-- Job: job"));
		assertTrue(text.contains("-- Schema plan: plan"));
		assertTrue(text.contains("-- WARNING: Review default DROP TABLE SECRET\n"));
		assertTrue(text.contains("CREATE TABLE ITEMS(ID INTEGER)\n;\n"));
		assertTrue(text.endsWith("ALTER TABLE ITEMS ADD NAME VARCHAR(40);\n"));
		assertFalse(text.contains(";\n;\n"));
	}

	@Test
	void honorsStartTerminatorAndResetFromActualTargetPolicy() {
		var dialect = new Dialect(null) {
			@Override
			public SqlTerminator createSqlDelimiter(String sql) {
				var result = new SqlTerminator();
				result.setStartStatementTerminator("DELIMITER $$");
				result.setTerminator("$$");
				result.setEndStatementTerminator("DELIMITER ;");
				return result;
			}
		};
		var plan = new TransferSchemaPreparation.Plan("plan",
				List.of(new TransferSchemaPreparation.Statement("routine", "BEGIN body; END")));
		assertTrue(TransferSchemaSqlScript.render(plan, dialect, "job")
				.contains("DELIMITER $$\nBEGIN body; END\n$$\nDELIMITER ;\n"));
	}

	@Test
	void emptyPlanHasNoExportableDraftAndEmptyStatementsAreErrors() {
		assertNull(
				TransferSchemaSqlScript.render(new TransferSchemaPreparation.Plan("plan", List.of()), basic(), "job"));
		assertThrows(IllegalArgumentException.class,
				() -> TransferSchemaSqlScript.render(new TransferSchemaPreparation.Plan("plan",
						List.of(new TransferSchemaPreparation.Statement("bad", " "))), basic(), "job"));
	}

	@Test
	void batchSeparatorIsNotMistakenForTheSuffixOfAnIdentifier() {
		var dialect = new Dialect(null) {
			@Override
			public SqlTerminator createSqlDelimiter(String sql) {
				var result = new SqlTerminator();
				result.setTerminator("GO");
				return result;
			}
		};
		var plan = new TransferSchemaPreparation.Plan("plan",
				List.of(new TransferSchemaPreparation.Statement("cargo", "DROP TABLE CARGO")));
		assertTrue(TransferSchemaSqlScript.render(plan, dialect, "job").endsWith("DROP TABLE CARGO\nGO\n"));
	}
}
