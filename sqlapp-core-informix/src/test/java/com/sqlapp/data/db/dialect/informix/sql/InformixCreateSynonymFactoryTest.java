/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import static org.junit.jupiter.api.Assertions.*;
import static com.sqlapp.data.db.dialect.informix.metadata.InformixSynonymReader.*;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Synonym;
import com.sqlapp.data.schemas.SchemaUtils;

class InformixCreateSynonymFactoryTest {
	private Synonym synonym(String type, String ansi) {
		final var synonym = new Synonym("alias").setObjectSchemaName("owner").setObjectName("items");
		synonym.getSpecifics().put(INFORMIX_SYNONYM_TYPE, type);
		synonym.getSpecifics().put(INFORMIX_ANSI_DATABASE, ansi);
		return synonym;
	}

	private String sql(Synonym synonym) {
		return DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(synonym, SqlType.CREATE).get(0)
				.getSqlText();
	}

	@Test
	void preservesVisibilityAndAnsiSyntaxThroughXml() throws Exception {
		for (String type : java.util.List.of("P", "S")) {
			final Synonym restored = SchemaUtils.readXml(new StringReader(synonym(type, "false").asXml()));
			assertEquals("CREATE " + (type.equals("P") ? "PRIVATE" : "PUBLIC") + " SYNONYM alias FOR owner.items",
					sql(restored).replace("\"", ""));
		}
		assertEquals("CREATE SYNONYM alias FOR owner.items", sql(synonym("P", "true")).replace("\"", ""));
	}

	@Test
	void retainsDatabaseServerAndQuotedTargetIdentifiers() {
		final var synonym = synonym("P", "false").setObjectName("Odd\"Name");
		synonym.getSpecifics().put(INFORMIX_DATABASE_NAME, "other_db");
		synonym.getSpecifics().put(INFORMIX_SERVER_NAME, "other_server");
		assertTrue(sql(synonym).contains("\"other_db\"@\"other_server\":\"owner\".\"Odd\"\"Name\""), sql(synonym));
	}

	@Test
	void rejectsMissingAmbiguousAndIncompatibleConfiguration() {
		assertThrows(IllegalArgumentException.class, () -> sql(new Synonym("alias")));
		assertThrows(IllegalArgumentException.class, () -> sql(synonym("S", "true")));
		assertThrows(IllegalArgumentException.class, () -> sql(synonym("?", "false")));
		assertThrows(IllegalArgumentException.class, () -> sql(synonym("P", "?")));
		final var synonym = synonym("P", "false");
		synonym.getSpecifics().put(INFORMIX_SERVER_NAME, "server");
		assertThrows(IllegalArgumentException.class, () -> sql(synonym));
		synonym.getSpecifics().remove(INFORMIX_SERVER_NAME);
		synonym.setObjectName(null);
		assertThrows(IllegalArgumentException.class, () -> sql(synonym));
	}
}
