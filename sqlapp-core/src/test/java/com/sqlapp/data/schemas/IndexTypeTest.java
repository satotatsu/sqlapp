/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class IndexTypeTest {
	@Test void serializedEnumNamesAreAcceptedWithoutLosingVendorAliases() {
		for (var type : IndexType.values()) {
			assertSame(type, IndexType.parse(type.name()));
			assertSame(type, IndexType.parse(type.name().toLowerCase(Locale.ROOT)));
		}
		assertSame(IndexType.Function, IndexType.parse("FUNCTION NORMAL"));
		assertSame(IndexType.FunctionBitmap, IndexType.parse("FUNCTION BITMAP"));
	}
	@Test void allIndexTypesSurviveXmlRoundTrips() throws Exception {
		for (var type : IndexType.values()) {
			var original = new Index("I").setIndexType(type); original.getColumns().add("C");
			Index restored = SchemaUtils.readXml(new StringReader(original.asXml()));
			assertSame(type, restored.getIndexType(), type.name());
		}
	}
	@Test void absentAndUnknownTypesKeepTheirExistingBehavior() {
		assertNull(IndexType.parse(null)); assertNull(IndexType.parse("")); assertNull(IndexType.parse("unknown index type"));
	}
}
