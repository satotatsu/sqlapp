/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.util.JsonConverter;

/** Typed federation metadata in the canonical Catalog's existing specifics. */
public final class Db2Federation {
	public static final String SPECIFICS_KEY = "db2.federation.v1";
	public static final java.util.Set<String> DERIVED_OPTIONS = java.util.Set.of("DATE_COMPAT", "NUMBER_COMPAT",
			"SAME_DECFLT_ROUNDING", "VARCHAR2_COMPAT");

	private Db2Federation() {
	}

	public record Wrapper(String name, String library, Boolean defaultLibrary, Map<String, String> options,
			String remarks, String definition, Map<String, String> attributes) {
		public Wrapper {
			Db2CreationContext.identifier(name);
			text(library);
			text(remarks);
			Db2Federation.definition(definition);
			if (definition == null
					&& (defaultLibrary == null || (!defaultLibrary && (library == null || library.isBlank()))))
				throw new IllegalArgumentException(
						"Db2 wrapper requires a library choice or complete CREATE definition: " + name);
			options = Db2Federation.options(options);
			attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
		}
	}

	public record Server(String name, String wrapperName, String type, String version, Map<String, String> options,
			Map<String, String> derivedOptions, String remarks, String definition) {
		public Server {
			Db2CreationContext.identifier(name);
			if (wrapperName != null)
				Db2CreationContext.identifier(wrapperName);
			if (definition == null && wrapperName == null)
				throw new IllegalArgumentException(
						"Db2 server requires its wrapper or complete CREATE definition: " + name);
			text(type);
			text(version);
			text(remarks);
			Db2Federation.definition(definition);
			options = Db2Federation.options(options);
			derivedOptions = Db2Federation.options(derivedOptions);
			if (options.keySet().stream().anyMatch(DERIVED_OPTIONS::contains)
					|| derivedOptions.keySet().stream().anyMatch(k -> !DERIVED_OPTIONS.contains(k)))
				throw new IllegalArgumentException(
						"Db2 automatically derived server options must be retained separately");
		}
	}

	/** Defaults apply to a category of servers, independently of named servers. */
	public record Defaults(String wrapperName, String type, String version, Map<String, String> options) {
		public Defaults {
			if (wrapperName != null)
				Db2CreationContext.identifier(wrapperName);
			text(type);
			text(version);
			options = Db2Federation.options(options);
		}

		public List<String> id() {
			return Arrays.asList(wrapperName, type, version);
		}
	}

	public record Snapshot(int format, List<Wrapper> wrappers, List<Server> servers, List<Defaults> defaults) {
		public Snapshot {
			if (format != 1 || wrappers == null || servers == null || defaults == null)
				throw new IllegalArgumentException("Invalid Db2 federation snapshot");
			wrappers = List.copyOf(wrappers);
			servers = List.copyOf(servers);
			defaults = List.copyOf(defaults);
			var names = new HashSet<String>();
			for (var wrapper : wrappers)
				if (!names.add(wrapper.name()))
					throw new IllegalArgumentException("Duplicate Db2 wrapper: " + wrapper.name());
			names.clear();
			for (var server : servers)
				if (!names.add(server.name()))
					throw new IllegalArgumentException("Duplicate Db2 server: " + server.name());
			var ids = new HashSet<List<String>>();
			for (var entry : defaults)
				if (!ids.add(entry.id()))
					throw new IllegalArgumentException("Duplicate Db2 server defaults: " + entry.id());
		}
	}

	public static Snapshot get(Catalog catalog) {
		String json = catalog.getSpecifics().get(SPECIFICS_KEY);
		if (json == null)
			return new Snapshot(1, List.of(), List.of(), List.of());
		Snapshot snapshot = new JsonConverter().getObjectMapper().readerFor(Snapshot.class)
				.with(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);
		if (snapshot == null)
			throw new IllegalArgumentException("Invalid Db2 federation snapshot");
		return snapshot;
	}

	public static void set(Catalog catalog, List<Wrapper> wrappers, List<Server> servers, List<Defaults> defaults) {
		var snapshot = new Snapshot(1, wrappers, servers, defaults);
		if (wrappers.isEmpty() && servers.isEmpty() && defaults.isEmpty()) {
			var retained = new com.sqlapp.data.schemas.DbInfo();
			catalog.getSpecifics().forEach((key, value) -> {
				if (!SPECIFICS_KEY.equalsIgnoreCase(key))
					retained.put(key, value);
			});
			catalog.setSpecifics(retained);
		} else
			catalog.getSpecifics().put(SPECIFICS_KEY, new JsonConverter().toJsonString(snapshot));
	}

	private static Map<String, String> options(Map<String, String> values) {
		if (values == null)
			return Map.of();
		var result = new TreeMap<String, String>();
		values.forEach((key, value) -> {
			if (key == null || !key.matches("[A-Za-z_][A-Za-z0-9_]*") || value == null)
				throw new IllegalArgumentException("Invalid Db2 federation option name or value");
			text(value);
			if (result.putIfAbsent(key.toUpperCase(java.util.Locale.ROOT), value) != null)
				throw new IllegalArgumentException("Duplicate Db2 federation option: " + key);
		});
		return java.util.Collections.unmodifiableMap(result);
	}

	private static void definition(String value) {
		text(value);
		if (value != null && value.isBlank())
			throw new IllegalArgumentException("Db2 federation definition must not be blank");
	}

	private static void text(String value) {
		if (value != null && value.indexOf('\0') >= 0)
			throw new IllegalArgumentException("Db2 federation text must not contain NUL");
	}
}
