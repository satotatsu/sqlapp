/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;

class SequenceCacheSqlTest {
	@Test
	void retainsExplicitOrderAcrossXml() throws Exception {
		var registry = DialectResolver.getInstance().getDialect("Informix", 12, 10).createSqlFactoryRegistry();
		for (boolean ordered : java.util.List.of(true, false)) {
			var sequence = new Sequence("ids").setOrder(ordered).setCache(false);
			Sequence restored = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(sequence.asXml()));
			assertTrue(registry.createSql(restored, SqlType.CREATE).get(0).getSqlText()
					.endsWith(ordered ? " ORDER" : " NOORDER"));
		}
	}

	@Test
	void preservesCacheSettingsAndRejectsInvalidSizes() {
		var registry = DialectResolver.getInstance().getDialect("Informix", 14, 10).createSqlFactoryRegistry();
		var sequence = new Sequence("audit_seq");
		sequence.setCache(false);
		assertTrue(registry.createSql(sequence, SqlType.CREATE).get(0).getSqlText().contains("NOCACHE"));
		sequence.setCache(true);
		sequence.setCacheSize(0);
		assertTrue(registry.createSql(sequence, SqlType.CREATE).get(0).getSqlText().contains("NOCACHE"));
		sequence.setCacheSize(20);
		sequence.setCache(false);
		assertTrue(registry.createSql(sequence, SqlType.CREATE).get(0).getSqlText().contains("NOCACHE"));
		sequence.setCache(true);
		assertTrue(registry.createSql(sequence, SqlType.CREATE).get(0).getSqlText().contains("CACHE 20"));
		sequence.setCacheSize(1);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(sequence, SqlType.CREATE));
		sequence.setCacheSize(-1);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(sequence, SqlType.CREATE));
	}
}
