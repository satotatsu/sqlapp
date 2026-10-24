/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.Db2FunctionMappings.*;
import com.sqlapp.data.schemas.*;

class Db2FunctionMappingsTest {
	private Mapping mapping(String name) { return new Mapping(name, "APP", "F", "F1", "S", null, null, null, null, Map.of("remote_name", "r's.f"), List.of(), null, null); }
	@Test void orderedSnapshotsSurviveXmlAndCloneWithoutMutatingOtherMetadata() throws Exception {
		Catalog catalog = new Catalog(); catalog.getSpecifics().put("other", "retained");
		Db2FunctionMappings.set(catalog, List.of(mapping("Z"), mapping("A")));
		Catalog restored = SchemaUtils.readXml(new java.io.StringReader(catalog.asXml()));
		assertEquals(Db2FunctionMappings.get(catalog), Db2FunctionMappings.get(restored));
		assertEquals(Db2FunctionMappings.get(catalog), Db2FunctionMappings.get(catalog.clone()));
		assertEquals(List.of("Z", "A"), Db2FunctionMappings.get(restored).mappings().stream().map(Mapping::name).toList());
		assertNull(Db2FunctionMappings.get(restored).mappings().getFirst().infix());
		Db2FunctionMappings.set(restored, List.of());
		assertEquals("retained", restored.getSpecifics().get("other"));
		assertTrue(Db2FunctionMappings.get(restored).mappings().isEmpty());
	}
	@Test void malformedSnapshotsAndDuplicateOptionsAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> new Snapshot(1, List.of(mapping("A"), mapping("A"))));
		assertThrows(IllegalArgumentException.class, () -> new Mapping("A", "APP", "F", "F1", "S", null, null, null, false, Map.of("a", "1", "A", "2"), List.of(), null, null));
		assertThrows(IllegalArgumentException.class, () -> new ParameterOption(1, "X", "CAST", "Y"));
		assertThrows(IllegalArgumentException.class, () -> mapping("A").withDefinition(" "));
		Catalog catalog = new Catalog(); catalog.getSpecifics().put(Db2FunctionMappings.SPECIFICS_KEY, "{\"format\":1,\"mappings\":[],\"unknown\":true}");
		assertThrows(RuntimeException.class, () -> Db2FunctionMappings.get(catalog));
	}
}
