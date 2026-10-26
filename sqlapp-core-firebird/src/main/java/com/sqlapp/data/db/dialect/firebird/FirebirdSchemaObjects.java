/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.util.JsonConverter;

/** Typed, versioned native definitions retained by Schema XML and cloning. */
public final class FirebirdSchemaObjects {
	public static final String SPECIFICS_KEY = "firebird.schemaObjects.v1";

	private FirebirdSchemaObjects() {
	}

	public record Manifest(int format, List<FirebirdSchemaObject> objects) {
	}

	public static List<FirebirdSchemaObject> get(Schema schema) {
		String json = schema.getSpecifics().get(SPECIFICS_KEY);
		if (json == null)
			return List.of();
		Manifest manifest = new JsonConverter().getObjectMapper().readerFor(Manifest.class)
				.with(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);
		if (manifest == null || manifest.format() != 1 || manifest.objects() == null)
			throw new IllegalArgumentException("Invalid Firebird schema object manifest.");
		validate(manifest.objects());
		return List.copyOf(manifest.objects());
	}

	public static void set(Schema schema, List<FirebirdSchemaObject> objects) {
		validate(objects);
		if (objects.isEmpty())
			schema.getSpecifics().remove(SPECIFICS_KEY);
		else
			schema.getSpecifics().put(SPECIFICS_KEY,
					new JsonConverter().toJsonString(new Manifest(1, List.copyOf(objects))));
	}

	private static void validate(List<FirebirdSchemaObject> objects) {
		var identities = new HashSet<List<Object>>();
		for (var object : objects) {
			if (!identities.add(List.of(object.kind(), object.name())))
				throw new IllegalArgumentException("Duplicate Firebird object: " + object.name());
		}
	}

	public static List<String> createSql(Schema schema) {
		return get(schema).stream().flatMap(object -> object.definition().stream()).toList();
	}

	/**
	 * Explicit DROP statements; callers must remove dependent routines, tables and
	 * domains first.
	 */
	public static List<String> dropSql(Schema schema) {
		var objects = new ArrayList<>(get(schema));
		java.util.Collections.reverse(objects);
		return objects.stream().map(FirebirdSchemaObject::dropDefinition).toList();
	}
}
