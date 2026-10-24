/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.Db2Federation.*;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.SchemaUtils;

class Db2FederationTest {
	private Wrapper wrapper() {
		return new Wrapper("W.with.dot", "lib's.so", false, Map.of("module", " x's "), "comment", null, Map.of());
	}

	@Test
	void xmlCloneAndRemovalPreserveTypedSnapshotAndOtherSpecifics() throws Exception {
		Catalog catalog = new Catalog();
		var server = new Server("S", "W.with.dot", "ORACLE", "9i", Map.of("node", "service"),
				Map.of("DATE_COMPAT", "Y"), null, null);
		var defaults = new Defaults(null, "ORACLE", null, Map.of("cpu_ratio", "2.0"));
		Db2Federation.set(catalog, List.of(wrapper()), List.of(server), List.of(defaults));
		Catalog restored = SchemaUtils.readXml(new StringReader(catalog.asXml()));
		assertEquals(Db2Federation.get(catalog), Db2Federation.get(restored));
		assertEquals(Db2Federation.get(catalog), Db2Federation.get(catalog.clone()));
		assertEquals(" x's ", Db2Federation.get(restored).wrappers().getFirst().options().get("MODULE"));
		restored.getSpecifics().put("OTHER", "retained");
		Db2Federation.set(restored, List.of(), List.of(), List.of());
		assertFalse(restored.getSpecifics().containsKey(Db2Federation.SPECIFICS_KEY));
		assertEquals("retained", restored.getSpecifics().get("OTHER"));
	}

	@Test
	void duplicateIdentitiesOptionsAndMalformedPayloadsAreRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> new Snapshot(1, List.of(wrapper(), wrapper()), List.of(), List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> new Wrapper("W", "lib.so", false, Map.of("a", "1", "A", "2"), null, null, Map.of()));
		assertThrows(IllegalArgumentException.class,
				() -> new Wrapper("W", null, null, Map.of(), null, null, Map.of()));
		assertThrows(IllegalArgumentException.class,
				() -> new Server("S", "W", null, null, Map.of("DATE_COMPAT", "Y"), Map.of(), null, null));
		assertThrows(IllegalArgumentException.class,
				() -> new Server("S", "W", null, null, Map.of("BAD;SQL", "Y"), Map.of(), null, null));
		Catalog catalog = new Catalog();
		for (String payload : List.of("null", "{}", "{\"format\":2,\"wrappers\":[],\"servers\":[],\"defaults\":[]}",
				"{\"format\":1,\"wrappers\":[],\"servers\":[],\"defaults\":[],\"unknown\":true}")) {
			catalog.getSpecifics().put(Db2Federation.SPECIFICS_KEY, payload);
			assertThrows(RuntimeException.class, () -> Db2Federation.get(catalog));
		}
	}

	@Test
	void completeDefinitionsDoNotRequireDuplicateEmbeddedComponents() {
		assertDoesNotThrow(() -> new Wrapper("W", null, null, null, null, "CREATE WRAPPER W", null));
		assertDoesNotThrow(() -> new Server("S", null, null, null, null, null, null, "CREATE SERVER S WRAPPER W"));
		assertThrows(IllegalArgumentException.class, () -> new Server("S", null, null, null, null, null, null, " "));
	}
}
