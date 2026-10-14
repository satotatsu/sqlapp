/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.schemas.properties.SpecificsProperty;
import com.sqlapp.util.JsonConverter;

/**
 * Typed access to PostgreSQL-only objects in the canonical Schema/Catalog
 * specifics. Existing XML serialization, cloning and comparison retain this
 * versioned payload without a shared XML format change.
 */
public final class PostgresSchemaObjects {
	public static final String SPECIFICS_KEY = "postgres.schemaObjects.v1";
	public static final String CONNECTION_TOKEN = "__SQLAPP_SUBSCRIPTION_CONNECTION__";

	private PostgresSchemaObjects() {
	}

	public record Manifest(int format, List<PostgresSchemaObject> objects) {
	}

	public static List<PostgresSchemaObject> get(SpecificsProperty<?> owner) {
		String json = owner.getSpecifics().get(SPECIFICS_KEY);
		if (json == null) {
			return List.of();
		}
		var converter = new JsonConverter();
		Manifest manifest = converter.getObjectMapper().readerFor(Manifest.class)
				.with(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);
		if (manifest == null || manifest.format() != 1 || manifest.objects() == null) {
			throw new IllegalArgumentException("Invalid PostgreSQL schema object manifest.");
		}
		validate(manifest.objects());
		validateScope(owner, manifest.objects());
		return List.copyOf(manifest.objects());
	}

	public static void set(SpecificsProperty<?> owner, List<PostgresSchemaObject> objects) {
		validate(objects);
		validateScope(owner, objects);
		if (objects.isEmpty()) {
			owner.getSpecifics().remove(SPECIFICS_KEY);
		} else {
			owner.getSpecifics().put(SPECIFICS_KEY,
					new JsonConverter().toJsonString(new Manifest(1, List.copyOf(objects))));
		}
	}

	private static void validate(List<PostgresSchemaObject> objects) {
		var ids = new HashSet<String>();
		for (var object : objects) {
			if (!ids.add(object.id())) {
				throw new IllegalArgumentException("Duplicate PostgreSQL object: " + object.id());
			}
		}
	}

	private static void validateScope(SpecificsProperty<?> owner, List<PostgresSchemaObject> objects) {
		for (var object : objects) {
			boolean schemaScoped = switch (object.kind()) {
			case COLLATION, FOREIGN_TABLE, POLICY, ROW_SECURITY, STATISTICS -> true;
			default -> false;
			};
			if (owner instanceof com.sqlapp.data.schemas.Schema schema) {
				if (!schemaScoped || !java.util.Objects.equals(schema.getName(), object.schemaName())) {
					throw new IllegalArgumentException(
							"Object " + object.id() + " does not belong in schema " + schema.getName());
				}
			} else if (!(owner instanceof com.sqlapp.data.schemas.Catalog) || schemaScoped) {
				throw new IllegalArgumentException(
						"Object " + object.id() + " requires its correct Schema or Catalog owner.");
			}
		}
	}

	/**
	 * Returns complete CREATE statements, failing before returning any SQL when a
	 * subscription connection is missing. Overrides are keyed by object id and are
	 * never written back to the Schema model. Subscriptions remain disconnected.
	 */
	public static List<String> createSql(SpecificsProperty<?> owner, Map<String, String> connections) {
		var result = new ArrayList<String>();
		for (var object : get(owner)) {
			result.addAll(createSql(object, connections));
		}
		return List.copyOf(result);
	}

	public static List<String> createSql(SpecificsProperty<?> owner) {
		return createSql(owner, Map.of());
	}

	/**
	 * Explicit DROP definitions, in reverse stored creation order; no CASCADE is
	 * added.
	 */
	public static List<String> dropSql(SpecificsProperty<?> owner) {
		var objects = new ArrayList<>(get(owner));
		java.util.Collections.reverse(objects);
		return objects.stream().map(PostgresSchemaObject::dropDefinition).toList();
	}

	/**
	 * Resolves external connection/secret values without modifying stored
	 * definitions.
	 */
	public static List<String> createSql(PostgresSchemaObject object, Map<String, String> connections) {
		var result = new ArrayList<String>();
		for (String original : object.definition()) {
			String sql = original;
			if (sql.contains(CONNECTION_TOKEN)) {
				String connection = connections.get(object.id());
				if (connection == null || connection.isBlank() || connection.indexOf('\0') >= 0) {
					throw new IllegalArgumentException("Supply connection info for subscription " + object.id()
							+ "; connection credentials are not exported.");
				}
				sql = sql.replace(CONNECTION_TOKEN, "E'" + connection.replace("\\", "\\\\").replace("'", "''") + "'");
			}
			for (String option : List.of("password", "sslpassword", "secret", "token")) {
				String token = "'__SQLAPP_OPTION_" + option + "__'";
				if (sql.contains(token)) {
					String value = connections.get(object.id() + ":" + option);
					if (value == null || value.indexOf('\0') >= 0) {
						throw new IllegalArgumentException("Supply external option " + option + " for " + object.id());
					}
					sql = sql.replace(token, "E'" + value.replace("\\", "\\\\").replace("'", "''") + "'");
				}
			}
			result.add(sql);
		}
		return List.copyOf(result);
	}
}
