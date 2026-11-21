/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.logic;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OracleTriggerHeaderTest {
	@Test
	void detectsCompoundDeclaration() {
		assertTrue(OracleTriggerHeader
				.declaresCompound("CREATE OR REPLACE TRIGGER APP.T FOR INSERT ON APP.X COMPOUND TRIGGER END T;"));
		assertTrue(OracleTriggerHeader.declaresCompound(
				"create trigger t for update of a on x\ncompound\n  -- c\n  trigger\nbefore statement is begin null; end before statement;\nend;"));
		assertTrue(OracleTriggerHeader
				.declaresCompound("CREATE TRIGGER T FOR INSERT ON X WHEN (1 = 1) COMPOUND /* x */ TRIGGER END;"));
	}

	@Test
	void ignoresBodyCommentsLiteralsAndIdentifiers() {
		assertFalse(OracleTriggerHeader.declaresCompound(
				"CREATE TRIGGER T BEFORE INSERT ON X FOR EACH ROW BEGIN NULL; END; -- COMPOUND TRIGGER"));
		assertFalse(OracleTriggerHeader.declaresCompound(
				"CREATE TRIGGER T BEFORE INSERT ON X FOR EACH ROW DECLARE v VARCHAR2(30) := 'x'; BEGIN NULL; END;\n"
						+ "/* COMPOUND TRIGGER */"));
		assertFalse(OracleTriggerHeader.declaresCompound(
				"CREATE TRIGGER T BEFORE INSERT ON X /* COMPOUND TRIGGER */ FOR EACH ROW BEGIN NULL; END;"));
		assertFalse(OracleTriggerHeader.declaresCompound(
				"CREATE TRIGGER T BEFORE INSERT ON X -- COMPOUND TRIGGER\nFOR EACH ROW BEGIN NULL; END;"));
		assertFalse(OracleTriggerHeader.declaresCompound(
				"CREATE TRIGGER T BEFORE INSERT ON X FOR EACH ROW WHEN (NEW.A = 'COMPOUND TRIGGER') BEGIN NULL; END;"));
		assertFalse(OracleTriggerHeader.declaresCompound(
				"CREATE TRIGGER T BEFORE INSERT ON X FOR EACH ROW WHEN (NEW.A = q'[it's COMPOUND TRIGGER]') "
						+ "BEGIN NULL; END;"));
		assertFalse(OracleTriggerHeader.declaresCompound(
				"CREATE TRIGGER \"COMPOUND TRIGGER\" BEFORE INSERT ON X FOR EACH ROW BEGIN NULL; END;"));
		assertFalse(OracleTriggerHeader.declaresCompound(
				"CREATE TRIGGER T BEFORE INSERT ON X FOR EACH ROW WHEN (COMPOUND TRIGGER) BEGIN NULL; END;"));
		assertFalse(OracleTriggerHeader.declaresCompound(null));
		assertFalse(OracleTriggerHeader.declaresCompound("CREATE TRIGGER 'unterminated"));
	}
}
