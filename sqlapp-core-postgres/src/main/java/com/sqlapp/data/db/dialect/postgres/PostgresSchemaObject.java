/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A PostgreSQL object retained as executable DDL when no shared Schema object
 * can represent it. Identity includes the containing schema and parent object.
 * Definition is authoritative; attributes are supplemental metadata.
 */
public record PostgresSchemaObject(Kind kind, String schemaName, String name, String parentName,
		List<String> definition, String dropDefinition, Map<String, String> attributes) {
	public enum Kind {
		EXTENSION, COLLATION, FOREIGN_DATA_WRAPPER, SERVER, USER_MAPPING, FOREIGN_TABLE,
		POLICY, ROW_SECURITY, STATISTICS, PUBLICATION, SUBSCRIPTION
	}

	public PostgresSchemaObject {
		Objects.requireNonNull(kind, "kind");
		if (name == null || name.isEmpty() || name.indexOf('\0') >= 0) {
			throw new IllegalArgumentException("Object name must not be empty or contain NUL.");
		}
		if ((schemaName != null && schemaName.indexOf('\0') >= 0) || (parentName != null && parentName.indexOf('\0') >= 0)) {
			throw new IllegalArgumentException("Schema and parent identifiers must not contain NUL.");
		}
		if ((kind == Kind.POLICY || kind == Kind.USER_MAPPING) && (parentName == null || parentName.isEmpty())) {
			throw new IllegalArgumentException("Containing table/server identity is required for " + kind);
		}
		definition = List.copyOf(definition);
		if (definition.isEmpty() || definition.stream().anyMatch(sql -> sql == null || sql.isBlank() || sql.indexOf('\0') >= 0)) {
			throw new IllegalArgumentException("Complete object definition is required: " + name);
		}
		if (dropDefinition == null || dropDefinition.isBlank()) {
			throw new IllegalArgumentException("Object drop definition is required: " + name);
		}
		attributes = Map.copyOf(attributes);
	}

	/** Stable JSON identity, preserving case and avoiding delimiter collisions. */
	public String id() {
		return new com.sqlapp.util.JsonConverter().toJsonString(List.of(kind.name(),
				schemaName == null ? "" : schemaName, name, parentName == null ? "" : parentName));
	}
}
