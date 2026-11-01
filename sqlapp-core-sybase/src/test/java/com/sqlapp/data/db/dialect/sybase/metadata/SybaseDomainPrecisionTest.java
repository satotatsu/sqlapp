/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sybase.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.sqlapp.data.db.dialect.sybase.DialectHolder;
import com.sqlapp.data.schemas.*;

class SybaseDomainPrecisionTest {
	@ParameterizedTest
	@ValueSource(strings = { "numeric", "decimal", "NUMERIC", "DECIMAL" })
	void preservesDeclaredPrecisionThroughDomainXml(String type) throws Exception {
		final var row = new HashMap<String, Object>();
		row.put("domain_name", "amount_type");
		row.put("base_type_name", type);
		row.put("length", 17L);
		row.put("prec", 28L);
		row.put("scale", 5);
		row.put("allownulls", true);
		row.put("catalog_name", "db");
		row.put("schema_name", "dbo");
		final var domain = new SybaseDomainReader(DialectHolder.defaultDialect)
				.createDomain(SybaseReaderTestSupport.row(row));
		assertEquals(28L, domain.getLength());
		assertEquals(5, domain.getScale());
		final Domain restored = SchemaUtils.readXml(new java.io.StringReader(domain.asXml()));
		assertEquals(domain.getLength(), restored.getLength());
		assertEquals(domain.getScale(), restored.getScale());
		assertEquals("dbo", restored.getSchemaName());
	}

	@Test
	void leavesUnknownPrecisionUnknownAndPreservesCharacterLengths() {
		assertNull(SybaseUtils.getTypeLength("numeric", 17L, null));
		assertEquals(28L, SybaseUtils.getTypeLength("dec", 17L, 28L));
		assertEquals(80L, SybaseUtils.getTypeLength("varchar", 80L, 0L));
		assertEquals(20L, SybaseUtils.getTypeLength("varchar", 0L, 20L));
	}

	@Test
	void normalizesTypeNamesIndependentlyOfDefaultLocale() {
		final Locale previous = Locale.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("tr-TR"));
			assertEquals(28L, SybaseUtils.getTypeLength("numeric", 17L, 28L));
			assertEquals(40L, SybaseUtils.getMaxLength("nchar", 80L));
		} finally {
			Locale.setDefault(previous);
		}
	}
}
