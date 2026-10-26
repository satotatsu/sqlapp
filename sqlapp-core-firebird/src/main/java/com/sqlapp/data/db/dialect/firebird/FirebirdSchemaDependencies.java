/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird;

import java.util.List;
import java.util.Objects;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.util.JsonConverter;

/**
 * Catalog dependency identities retained alongside the canonical Schema
 * objects.
 */
public final class FirebirdSchemaDependencies {
	public static final String SPECIFICS_KEY = "firebird.dependencies.v1";

	public enum Kind {
		TABLE, VIEW, TRIGGER, PROCEDURE, DOMAIN, SEQUENCE, FUNCTION, PACKAGE, PACKAGE_BODY, EXCEPTION, BLOB_FILTER,
		COLLATION
	}

	public record ObjectId(Kind kind, String name) {
		public ObjectId {
			Objects.requireNonNull(kind, "kind");
			if (name == null || name.isBlank() || name.indexOf('\0') >= 0)
				throw new IllegalArgumentException("Invalid Firebird dependency name");
		}
	}

	public record Dependency(ObjectId dependent, ObjectId prerequisite) {
		public Dependency {
			Objects.requireNonNull(dependent);
			Objects.requireNonNull(prerequisite);
		}
	}

	public record Manifest(int format, List<Dependency> dependencies) {
	}

	private FirebirdSchemaDependencies() {
	}

	public static List<Dependency> get(Schema schema) {
		String json = schema.getSpecifics().get(SPECIFICS_KEY);
		if (json == null)
			return List.of();
		Manifest manifest = new JsonConverter().getObjectMapper().readerFor(Manifest.class)
				.with(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);
		if (manifest == null || manifest.format() != 1 || manifest.dependencies() == null)
			throw new IllegalArgumentException("Invalid Firebird dependency manifest");
		return List.copyOf(manifest.dependencies());
	}

	public static void set(Schema schema, List<Dependency> dependencies) {
		var copy = List.copyOf(dependencies);
		// A recorded empty graph is distinct from an older artifact without dependency
		// information.
		schema.getSpecifics().put(SPECIFICS_KEY, new JsonConverter().toJsonString(new Manifest(1, copy)));
	}
}
