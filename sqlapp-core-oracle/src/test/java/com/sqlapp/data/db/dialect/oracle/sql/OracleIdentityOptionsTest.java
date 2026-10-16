/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class OracleIdentityOptionsTest {
	private Table table() {
		Table table = new Table("T").setSchemaName("APP");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setIdentity(true).setIdentityGenerationType(IdentityGenerationType.Always).setIdentityStartValue(100).setIdentityStep(1));
		var sequence = table.getColumns().get(0).getSequence(); sequence.setCacheSize(20).setCache(true).setOrder(false);
		return table;
	}
	private String diff(Table before, Table after) {
		var factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
		return factory.createDiffSql(before.diff(after)).get(0).getSqlText();
	}
	@Test
	void changedOptionsUseAlterTableWithoutRestartOrSequenceAlter() throws Exception {
		Table before = table(), after = before.clone();
		var sequence = after.getColumns().get(0).getSequence(); sequence.setIncrementBy(2).setCacheSize(40).setCycle(true).setOrder(true);
		Table restored = SchemaUtils.readXml(new java.io.StringReader(after.asXml()));
		assertFalse(before.getColumns().get(0).getSequence().isOrder());
		assertTrue(sequence.isOrder());
		assertTrue(restored.getColumns().get(0).getSequence().isOrder(), after.asXml());
		String sql = diff(before, restored);
		assertTrue(sql.contains("AS IDENTITY (INCREMENT BY 2 CYCLE ORDER CACHE 40)"), sql);
		assertFalse(sql.contains("START WITH")); assertFalse(sql.contains("ALTER SEQUENCE")); assertFalse(sql.contains("DROP"));
		assertEquals(100L, before.getColumns().get(0).getIdentityStartValue());
	}
	@Test
	void explicitCacheAndBoundsRemovalHaveOracleClauses() {
		Table before = table(); before.getColumns().get(0).getSequence().setMinValue(1).setMaxValue(1000);
		Table after = before.clone(); var sequence = after.getColumns().get(0).getSequence();
		sequence.setMinValue((java.math.BigInteger) null).setMaxValue((java.math.BigInteger) null).setCacheSize(0).setCache(false);
		String sql = diff(before, after); assertTrue(sql.contains("NOMINVALUE NOMAXVALUE NOCACHE"), sql);
	}
	@Test
	void restartAndInvalidOrAmbiguousOptionsFail() {
		for (String invalid : java.util.List.of("restart", "zero", "cache", "implicitIncrement", "position")) {
			Table before = table(), after = before.clone(); var sequence = after.getColumns().get(0).getSequence();
			switch (invalid) {
			case "restart" -> sequence.setStartValue(200);
			case "zero" -> sequence.setIncrementBy(0);
			case "cache" -> sequence.setCacheSize(1);
			case "implicitIncrement" -> sequence.setIncrementBy((java.math.BigInteger) null);
			case "position" -> sequence.setLastValue(500);
			}
			assertThrows(IllegalArgumentException.class, () -> diff(before, after), invalid);
		}
	}
}
