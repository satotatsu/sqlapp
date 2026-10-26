/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird;

import java.util.List;
import java.util.Objects;

/**
 * Complete executable definitions for Firebird objects outside the shared
 * model.
 */
public record FirebirdSchemaObject(Kind kind, String name, List<String> definition, String dropDefinition) {
	public enum Kind {
		EXCEPTION, BLOB_FILTER, COLLATION
	}

	public FirebirdSchemaObject {
		Objects.requireNonNull(kind, "kind");
		requireText(name, "name");
		definition = List.copyOf(definition);
		if (definition.isEmpty())
			throw new IllegalArgumentException("An object needs a CREATE definition.");
		definition.forEach(sql -> requireText(sql, "definition"));
		requireText(dropDefinition, "dropDefinition");
	}

	private static void requireText(String text, String property) {
		if (text == null || text.isBlank() || text.indexOf('\0') >= 0)
			throw new IllegalArgumentException("Invalid Firebird object " + property);
	}
}
