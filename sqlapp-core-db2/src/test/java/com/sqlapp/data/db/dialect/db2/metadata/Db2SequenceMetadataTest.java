/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.jdbc.ExResultSet;

class Db2SequenceMetadataTest {
	@Test
	void readsNoCacheAndLargeStartWithoutTruncation() throws Exception {
		var reader = new Db2SequenceReader(DialectHolder.defaultDialect);
		var start = new BigDecimal("9223372036854775808");
		var sequence = reader.createSequence(new ExResultSet(null) {
			@Override public String getString(String name) {
				return switch(name) { case "sequence_name" -> "S"; case "schema_name" -> "TEST";
				case "ORDER" -> "Y"; default -> null; };
			}
			@Override public BigDecimal getBigDecimal(String name) {
				return name.equals("START") ? start : name.equals("CACHE") ? BigDecimal.ONE : BigDecimal.ZERO;
			}
			@Override public int getInt(String name) { return name.equals("PRECISION") ? 31 : 0; }
			@Override public long getLong(String name) { return 1; }
			@Override public Timestamp getTimestamp(String name) { return null; }
			@Override public Object getObject(String name) { return null; }
		});
		assertEquals(start.toBigIntegerExact(), sequence.getStartValue());
		assertFalse(sequence.isCache());
		assertEquals(1, sequence.getCacheSize());
	}
}
