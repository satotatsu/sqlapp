/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;

class Db2SequenceMetadataDdlTest extends AbstractDb2SqlFactoryTest {
	@Test
	void zeroAndDisabledCacheUseNoCache() {
		for (Sequence sequence : java.util.List.of(new Sequence("S").setCacheSize(0), new Sequence("S").setCacheSize(1),
				new Sequence("S").setCache(false), new Sequence("S").setCache(false).setCacheSize(20))) {
			String text = sql(sequence);
			assertTrue(text.contains("NO CACHE"), text);
			assertFalse(text.contains("CACHE 0"), text);
		}
	}
	@Test
	void retainsPositiveCacheSize() {
		assertTrue(sql(new Sequence("S").setCacheSize(7)).contains("CACHE 7"));
	}

	@Test
	void retainsRequestedOrdering() {
		assertTrue(sql(new Sequence("S").setOrder(true)).endsWith(" ORDER"));
	}

	@Test
	void decimalSequenceRequiresIntegralScale() {
		Sequence sequence = new Sequence("S");
		sequence.setDataType(com.sqlapp.data.db.datatype.DataType.DECIMAL).setPrecision(20);
		assertTrue(sql(sequence).contains("DECIMAL(20,0)"));
		sequence.setScale(2);
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> sql(sequence));
	}

	private String sql(Sequence sequence) {
		com.sqlapp.data.db.sql.SqlFactory<Sequence> factory = sqlFactoryRegistry.getSqlFactory(sequence, SqlType.CREATE);
		return factory.createSql(sequence).get(0).getSqlText();
	}
}
