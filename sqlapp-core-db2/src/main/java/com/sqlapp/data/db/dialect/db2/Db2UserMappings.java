/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.util.JsonConverter;

/**
 * Catalog user mappings; unreadable catalog credentials are explicit markers.
 */
public final class Db2UserMappings {
	public static final String SPECIFICS_KEY = "db2.userMappings.v1";

	private Db2UserMappings() {
	}

	public enum Principal {
		USER, PUBLIC
	}

	public record Mapping(Principal principal, String authorizationName, String serverName, Map<String, String> options,
			List<String> unavailableOptions, String definition) {
		public Mapping {
			if (principal == null || options == null || unavailableOptions == null)
				throw new IllegalArgumentException(
						"Db2 user mapping requires principal, options and unavailable-option markers");
			Db2CreationContext.identifier(authorizationName);
			Db2CreationContext.identifier(serverName);
			if ((principal == Principal.PUBLIC) != "PUBLIC".equals(authorizationName))
				throw new IllegalArgumentException("Db2 public mapping must explicitly use PUBLIC");
			var values = new TreeMap<String, String>();
			options.forEach((key, value) -> {
				String normalized = optionName(key);
				if (value == null || value.indexOf('\0') >= 0 || values.putIfAbsent(normalized, value) != null)
					throw new IllegalArgumentException(
							"Missing value or duplicate Db2 user mapping option: " + normalized);
			});
			var missing = new TreeSet<String>();
			for (String key : unavailableOptions) {
				String normalized = optionName(key);
				if (values.containsKey(normalized) || !missing.add(normalized))
					throw new IllegalArgumentException(
							"Conflicting or duplicate unavailable Db2 user mapping option: " + normalized);
			}
			if (definition != null && (definition.isBlank() || definition.indexOf('\0') >= 0))
				throw new IllegalArgumentException("Db2 user mapping definition must be executable SQL");
			options = java.util.Collections.unmodifiableMap(values);
			unavailableOptions = List.copyOf(missing);
		}

		public List<Object> id() {
			return List.of(principal, authorizationName, serverName);
		}

		/** Supplies an intended value and resolves its unreadable catalog marker. */
		public Mapping withOption(String name, String value) {
			String key = optionName(name);
			var values = new TreeMap<>(options);
			values.put(key, value);
			return new Mapping(principal, authorizationName, serverName, values,
					unavailableOptions.stream().filter(option -> !option.equals(key)).toList(), definition);
		}
	}

	public record Snapshot(int format, List<Mapping> mappings) {
		public Snapshot {
			if (format != 1 || mappings == null)
				throw new IllegalArgumentException("Invalid Db2 user mapping snapshot");
			mappings = List.copyOf(mappings);
			var ids = new HashSet<List<Object>>();
			var modes = new java.util.HashMap<String, Principal>();
			for (var mapping : mappings) {
				if (!ids.add(mapping.id()))
					throw new IllegalArgumentException("Duplicate Db2 user mapping: " + mapping.id());
				Principal previous = modes.putIfAbsent(mapping.serverName(), mapping.principal());
				if (previous != null && previous != mapping.principal())
					throw new IllegalArgumentException(
							"Db2 PUBLIC and individual mappings cannot coexist for server: " + mapping.serverName());
			}
		}
	}

	public static Snapshot get(Catalog catalog) {
		String json = catalog.getSpecifics().get(SPECIFICS_KEY);
		if (json == null)
			return new Snapshot(1, List.of());
		Snapshot result = new JsonConverter().getObjectMapper().readerFor(Snapshot.class)
				.with(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);
		if (result == null)
			throw new IllegalArgumentException("Invalid Db2 user mapping snapshot");
		return result;
	}

	public static void set(Catalog catalog, List<Mapping> mappings) {
		var snapshot = new Snapshot(1, mappings);
		if (mappings.isEmpty()) {
			var retained = new com.sqlapp.data.schemas.DbInfo();
			catalog.getSpecifics().forEach((key, value) -> {
				if (!SPECIFICS_KEY.equalsIgnoreCase(key))
					retained.put(key, value);
			});
			catalog.setSpecifics(retained);
		} else
			catalog.getSpecifics().put(SPECIFICS_KEY, new JsonConverter().toJsonString(snapshot));
	}

	private static String optionName(String name) {
		if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*"))
			throw new IllegalArgumentException("Invalid Db2 user mapping option name");
		return name.toUpperCase(java.util.Locale.ROOT);
	}
}
