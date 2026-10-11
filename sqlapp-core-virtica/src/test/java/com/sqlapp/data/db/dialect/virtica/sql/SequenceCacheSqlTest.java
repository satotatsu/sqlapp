/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.virtica.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;

class SequenceCacheSqlTest {
	@Test
	void preservesCacheSettingsAndRejectsInvalidSizes() {
		var registry = DialectResolver.getInstance().getDialect("Vertica", 25, 1).createSqlFactoryRegistry();
		var sequence = new Sequence("audit_seq");
		sequence.setCache(false);
		assertTrue(registry.createSql(sequence, SqlType.CREATE).get(0).getSqlText().contains("NO CACHE"));
		assertTrue(registry.createSql(sequence, SqlType.ALTER).get(0).getSqlText().contains("NO CACHE"));
		sequence.setCache(true);
		sequence.setCacheSize(0);
		assertTrue(registry.createSql(sequence, SqlType.CREATE).get(0).getSqlText().contains("NO CACHE"));
		sequence.setCacheSize(20);
		sequence.setCache(false);
		assertTrue(registry.createSql(sequence, SqlType.CREATE).get(0).getSqlText().contains("NO CACHE"));
		assertTrue(registry.createSql(sequence, SqlType.ALTER).get(0).getSqlText().contains("NO CACHE"));
		sequence.setCache(true);
		assertTrue(registry.createSql(sequence, SqlType.CREATE).get(0).getSqlText().contains("CACHE 20"));
		sequence.setCacheSize(-1);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(sequence, SqlType.CREATE));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(sequence, SqlType.ALTER));
	}
}
