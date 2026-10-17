/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;

class OracleSequenceOptionsTest {
	private String sql(Sequence sequence, Dialect dialect) {
		return dialect.createSqlFactoryRegistry().createSql(sequence, SqlType.CREATE).get(0).getSqlText();
	}
	@Test void scaleExtendAndSessionHaveAnEighteenCBoundary() {
		for (String key : List.of("SCALE", "SESSION")) {
			Sequence sequence = new Sequence("S"); sequence.getSpecifics().put(key, "true");
			assertThrows(IllegalArgumentException.class, () -> sql(sequence, DialectHolder.oracle12cDialect));
			assertTrue(sql(sequence, DialectHolder.oracle18cDialect).contains(key));
		}
		Sequence sequence = new Sequence("S"); sequence.getSpecifics().put("SCALE", "true"); sequence.getSpecifics().put("EXTEND", "true");
		assertTrue(sql(sequence, DialectHolder.oracle26aiDialect).contains("SCALE EXTEND"));
	}
	@Test void invalidFlagsAndExtendWithoutScaleAreRejected() {
		for (String key : List.of("SCALE", "EXTEND", "SESSION")) {
			Sequence sequence = new Sequence("S"); sequence.getSpecifics().put(key, "invalid");
			assertThrows(IllegalArgumentException.class, () -> sql(sequence, DialectHolder.oracle26aiDialect));
		}
		Sequence sequence = new Sequence("S"); sequence.getSpecifics().put("EXTEND", "true");
		assertThrows(IllegalArgumentException.class, () -> sql(sequence, DialectHolder.oracle26aiDialect));
	}
	@Test void explicitFalseFlagsRemainCompatibleWithLegacySequences() {
		Sequence sequence = new Sequence("S");
		String expected = sql(sequence, DialectHolder.defaultDialect);
		for (String key : List.of("SCALE", "EXTEND", "SESSION")) sequence.getSpecifics().put(key, "false");
		assertEquals(expected, sql(sequence, DialectHolder.defaultDialect));
	}
}
