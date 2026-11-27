/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.Db2TypeMappings.*;
import com.sqlapp.data.schemas.*;

class Db2TypeMappingsTest {
	private Mapping mapping(String name) {
		return new Mapping(name, Direction.FORWARD,
				Map.of("TYPENAME", "DATE", "REMOTE_TYPENAME", "DATE", "SERVERNAME", "S"), null, null);
	}

	@Test
	void orderDirectionConditionsAndUnresolvedPrecedenceSurviveXmlAndClone() throws Exception {
		var catalog = new Catalog();
		catalog.getSpecifics().put("other", "retained");
		Db2TypeMappings.set(catalog, new Snapshot(1, List.of(mapping("Z"), mapping("A")), false));
		Catalog restored = SchemaUtils.readXml(new java.io.StringReader(catalog.asXml()));
		assertEquals(Db2TypeMappings.get(catalog), Db2TypeMappings.get(restored));
		assertEquals(Db2TypeMappings.get(catalog), Db2TypeMappings.get(catalog.clone()));
		assertFalse(Db2TypeMappings.get(restored).orderKnown());
		assertEquals("Z", Db2TypeMappings.get(restored).mappings().getFirst().name());
		Db2TypeMappings.set(restored, List.of());
		assertEquals("retained", restored.getSpecifics().get("other"));
	}

	@Test
	void duplicateNamesUnknownAttributesAndInvalidPayloadsFail() {
		assertThrows(IllegalArgumentException.class, () -> new Snapshot(1, List.of(mapping("A"), mapping("A"))));
		assertThrows(IllegalArgumentException.class,
				() -> new Mapping("A", Direction.REVERSE, Map.of("UNKNOWN", "x"), null, null));
		assertThrows(IllegalArgumentException.class, () -> mapping("A").withDefinition(" "));
		var catalog = new Catalog();
		catalog.getSpecifics().put(Db2TypeMappings.SPECIFICS_KEY, "{\"format\":1,\"mappings\":[],\"unknown\":true}");
		assertThrows(RuntimeException.class, () -> Db2TypeMappings.get(catalog));
	}
}
