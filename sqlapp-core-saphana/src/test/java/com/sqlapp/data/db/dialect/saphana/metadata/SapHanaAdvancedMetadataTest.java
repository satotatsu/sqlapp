/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.metadata;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.saphana.DialectHolder;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;

class SapHanaAdvancedMetadataTest {
	@Test
	void retainsSyncAndAsyncModesWithoutRenderingCatalogSentinelsAsThresholds() throws Exception {
		var reader = new SapHanaFulltextIndexReader(DialectHolder.defaultDialect);
		for (int[] mode : java.util.List.of(new int[] { 0, -1 }, new int[] { -1, -1 }, new int[] { 1, 2 })) {
			Index index = new Index("FT");
			reader.setDbSpecificInfo(new ExResultSet(null) {
				@Override
				public Object getObject(String name) {
					return null;
				}

				@Override
				public int getInt(String name) {
					return name.equals("FLUSH_EVERY_MINUTES") ? mode[0] : mode[1];
				}
			}, index);
			assertEquals(mode[0] == 0 ? "SYNC" : "ASYNC", index.getSpecifics().get("SYNC_MODE"));
			assertEquals(mode[0] > 0 ? "1" : null, index.getSpecifics().get("FLUSH_EVERY_MINUTES"));
			assertEquals(mode[1] > 0 ? "2" : null, index.getSpecifics().get("FLUSH_AFTER_DOCUMENTS"));
		}
	}

	@Test
	void keepsLegacySynonymQueryAndReadsRemoteDatabaseOnHana2() throws Exception {
		var reader = new SapHanaSynonymReader(DialectHolder.defaultDialect);
		assertFalse(reader.getSqlSqlNode(new ProductVersionInfo().setMajorVersion(1)).toString()
				.contains("CURRENT_DATABASE"));
		assertTrue(reader.getSqlSqlNode(new ProductVersionInfo().setMajorVersion(2)).toString()
				.contains("CURRENT_DATABASE"));
		var synonym = reader.createSynonym(new ExResultSet(null) {
			@Override
			public String getString(String name) {
				return switch (name.toUpperCase(java.util.Locale.ROOT)) {
				case "SYNONYM_NAME" -> "ALIAS";
				case "SCHEMA_NAME" -> "APP";
				case "OBJECT_SCHEMA" -> "Remote Schema";
				case "OBJECT_NAME" -> "T";
				case "TARGET_DATABASE" -> "Remote DB";
				default -> null;
				};
			}

			@Override
			public long getLong(String name) {
				return 1;
			}
		});
		assertEquals("Remote DB", synonym.getDbLinkName());
	}
}
