/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.saphana.DialectHolder;
import com.sqlapp.jdbc.ExResultSet;

class SapHanaSequenceMetadataTest {
	@Test
	void readsNoCacheAndLargeStartWithoutTruncation() throws Exception {
		var reader = new SapHanaSequenceReader(DialectHolder.defaultDialect);
		var start = new BigDecimal("9223372036854775808");
		var sequence = reader.createSequence(new ExResultSet(null) {
			@Override public String getString(String name) {
				return switch(name) { case "sequence_name" -> "S"; case "schema_name" -> "TEST";
				case "ORDER" -> "Y"; default -> null; };
			}
			@Override public BigDecimal getBigDecimal(String name) {
				return name.equals("START_NUMBER") || name.equals("START") ? start : BigDecimal.ZERO;
			}
			@Override public int getInt(String name) { return name.equals("CACHE_SIZE") ? 1 : 0; }
			@Override public long getLong(String name) { return 1; }
			@Override public Timestamp getTimestamp(String name) { return null; }
			@Override public Object getObject(String name) { return null; }
		});
		assertEquals(start.toBigIntegerExact(), sequence.getStartValue());
		assertFalse(sequence.isCache());
		assertEquals(1, sequence.getCacheSize());
	}
}
