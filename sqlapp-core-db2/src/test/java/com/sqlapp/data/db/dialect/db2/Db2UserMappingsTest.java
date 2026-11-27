/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.Db2UserMappings.*;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.SchemaUtils;

class Db2UserMappingsTest {
	@Test
	void xmlCloneAndCredentialResolutionRetainExplicitMissingState() throws Exception {
		var mapping = new Mapping(Principal.USER, "Local.Name", "Remote.Server",
				Map.of("REMOTE_AUTHID", " remote's id "), List.of("REMOTE_PASSWORD"), null);
		Catalog catalog = new Catalog();
		catalog.getSpecifics().put("OTHER", "retained");
		Db2UserMappings.set(catalog, List.of(mapping));
		Catalog restored = SchemaUtils.readXml(new StringReader(catalog.asXml()));
		assertEquals(Db2UserMappings.get(catalog), Db2UserMappings.get(restored));
		assertEquals(Db2UserMappings.get(catalog), Db2UserMappings.get(catalog.clone()));
		var resolved = mapping.withOption("remote_password", "example's password");
		assertTrue(resolved.unavailableOptions().isEmpty());
		assertFalse(mapping.options().containsKey("REMOTE_PASSWORD"));
		assertThrows(UnsupportedOperationException.class, () -> resolved.options().put("X", "x"));
		Db2UserMappings.set(restored, List.of());
		assertEquals("retained", restored.getSpecifics().get("OTHER"));
		assertTrue(Db2UserMappings.get(restored).mappings().isEmpty());
		assertFalse(restored.getSpecifics().containsKey(Db2UserMappings.SPECIFICS_KEY));
	}

	@Test
	void malformedPayloadsDuplicateOptionsAndConflictingPublicMappingsFail() {
		var user = new Mapping(Principal.USER, "U", "S", Map.of(), List.of("REMOTE_PASSWORD"), null);
		var publicMapping = new Mapping(Principal.PUBLIC, "PUBLIC", "S", Map.of(), List.of("REMOTE_PASSWORD"), null);
		assertThrows(IllegalArgumentException.class, () -> new Snapshot(1, List.of(user, user)));
		assertThrows(IllegalArgumentException.class, () -> new Snapshot(1, List.of(user, publicMapping)));
		assertThrows(IllegalArgumentException.class,
				() -> new Mapping(Principal.USER, "PUBLIC", "S", Map.of(), List.of(), null));
		assertThrows(IllegalArgumentException.class,
				() -> new Mapping(Principal.USER, "U", "S", Map.of("X", "a", "x", "b"), List.of(), null));
		assertThrows(IllegalArgumentException.class,
				() -> new Mapping(Principal.USER, "U", "S", Map.of("X", "a"), List.of("x"), null));
		Catalog catalog = new Catalog();
		for (String json : List.of("null", "{}", "{\"format\":2,\"mappings\":[]}",
				"{\"format\":1,\"mappings\":[],\"unknown\":true}")) {
			catalog.getSpecifics().put(Db2UserMappings.SPECIFICS_KEY, json);
			assertThrows(RuntimeException.class, () -> Db2UserMappings.get(catalog));
		}
	}
}
