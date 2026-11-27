/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird;

import java.util.Map;
import java.util.Objects;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.util.JsonConverter;

/** A declared routine type, distinct from its resolved scalar type metadata. */
public record FirebirdRoutineTypeReference(Kind kind, String domain, String relation, String column) {
	public static final String SPECIFICS_KEY = "firebird.typeReference.v1";

	public enum Kind {
		DOMAIN, TYPE_OF_DOMAIN, TYPE_OF_COLUMN
	}

	public record Manifest(int format, FirebirdRoutineTypeReference reference) {
	}

	public FirebirdRoutineTypeReference {
		Objects.requireNonNull(kind, "kind");
		if (kind == Kind.TYPE_OF_COLUMN) {
			identifier(relation);
			identifier(column);
			if (domain != null)
				throw new IllegalArgumentException("TYPE OF COLUMN cannot also name a domain");
		} else {
			identifier(domain);
			if (relation != null || column != null)
				throw new IllegalArgumentException("Domain references cannot also name a column");
		}
	}

	private static void identifier(String name) {
		if (name == null || name.isBlank() || name.indexOf('\0') >= 0)
			throw new IllegalArgumentException("A Firebird routine type reference requires nonempty identifiers");
	}

	public static FirebirdRoutineTypeReference domain(String name) {
		return new FirebirdRoutineTypeReference(Kind.DOMAIN, name, null, null);
	}

	public static FirebirdRoutineTypeReference typeOfDomain(String name) {
		return new FirebirdRoutineTypeReference(Kind.TYPE_OF_DOMAIN, name, null, null);
	}

	public static FirebirdRoutineTypeReference typeOfColumn(String relation, String column) {
		return new FirebirdRoutineTypeReference(Kind.TYPE_OF_COLUMN, null, relation, column);
	}

	public static FirebirdRoutineTypeReference get(Map<String, String> specifics) {
		String json = specifics.get(SPECIFICS_KEY);
		if (json == null)
			return null;
		Manifest manifest = new JsonConverter().getObjectMapper().readerFor(Manifest.class)
				.with(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);
		if (manifest == null || manifest.format() != 1 || manifest.reference() == null)
			throw new IllegalArgumentException("Invalid Firebird routine type reference manifest");
		return manifest.reference();
	}

	public static void set(Map<String, String> specifics, FirebirdRoutineTypeReference reference) {
		if (reference == null)
			specifics.remove(SPECIFICS_KEY);
		else
			specifics.put(SPECIFICS_KEY, new JsonConverter().toJsonString(new Manifest(1, reference)));
	}

	public void appendTo(FirebirdSqlBuilder builder) {
		if (!(builder.getDialect() instanceof Firebird25))
			throw new IllegalArgumentException("Routine type reference generation requires the Firebird 2.5+ dialect");
		switch (kind) {
		case DOMAIN -> builder.name(domain);
		case TYPE_OF_DOMAIN -> builder._add("TYPE OF ").name(domain);
		case TYPE_OF_COLUMN -> builder._add("TYPE OF COLUMN ").name(relation)._add(".").name(column);
		}
	}
}
