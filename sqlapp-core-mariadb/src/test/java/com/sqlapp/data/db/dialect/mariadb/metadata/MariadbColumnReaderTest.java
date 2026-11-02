/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.jdbc.ExResultSet;

class MariadbColumnReaderTest {
	private Column read(String extra, String expression) throws Exception {
		var reader = new MariadbColumn10_27Reader(DialectResolver.getInstance().getDialect("MariaDB", 10, 2, 7));
		return reader.createColumn(null, new ExResultSet(null) {
			@Override
			public String getString(String name) {
				return switch (name) {
				case "COLUMN_NAME" -> "computed";
				case "TABLE_NAME" -> "sample";
				case "DATA_TYPE", "COLUMN_TYPE" -> "int";
				case "IS_NULLABLE" -> "YES";
				case "EXTRA" -> extra;
				case "GENERATION_EXPRESSION" -> expression;
				case "COLUMN_DEFAULT" -> "(1 + 2)";
				default -> null;
				};
			}

			@Override
			public Object getObject(String name) {
				return null;
			}

			@Override
			public long getLong(String name) {
				return 0;
			}

			@Override
			public boolean wasNull() {
				return true;
			}

			@Override
			public int getInt(String name) {
				return 0;
			}
		});
	}

	@Test
	void readsInvisibleVirtualColumnsUnderTurkishLocale() throws Exception {
		Locale original = Locale.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("tr-TR"));
			Column column = read("virtual generated invisible", "`source` + 1");
			assertTrue(column.isHidden());
			assertEquals("`source` + 1", column.getFormula());
			assertFalse(column.isFormulaPersisted());
			Column restored = SchemaUtils.readXml(new java.io.StringReader(column.asXml()));
			assertTrue(restored.isHidden());
			assertEquals(column.getFormula(), restored.getFormula());
		} finally {
			Locale.setDefault(original);
		}
	}

	@Test
	void preservesStoredExpressionAndNativeDefault() throws Exception {
		Column stored = read("STORED GENERATED", "`source` + 1");
		assertFalse(stored.isHidden());
		assertTrue(stored.isFormulaPersisted());
		Column ordinary = read(null, null);
		assertFalse(ordinary.isHidden());
		assertEquals("(1 + 2)", ordinary.getDefaultValue());
	}
}
