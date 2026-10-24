/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2;

import java.util.*;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.util.JsonConverter;

/**
 * User-defined type mappings in creation/precedence order. Native conditions
 * are retained losslessly.
 */
public final class Db2TypeMappings {
	public static final String SPECIFICS_KEY = "db2.typeMappings.v1";
	public static final List<String> ATTRIBUTES = List.of("TYPESCHEMA", "TYPENAME", "LENGTH", "SCALE", "LOWER_LEN",
			"UPPER_LEN", "LOWER_SCALE", "UPPER_SCALE", "S_OPR_P", "BIT_DATA", "WRAPNAME", "SERVERNAME", "SERVERTYPE",
			"SERVERVERSION", "REMOTE_TYPESCHEMA", "REMOTE_TYPENAME", "REMOTE_META_TYPE", "REMOTE_LOWER_LEN",
			"REMOTE_UPPER_LEN", "REMOTE_LOWER_SCALE", "REMOTE_UPPER_SCALE", "REMOTE_S_OPR_P", "REMOTE_BIT_DATA");

	private Db2TypeMappings() {
	}

	public enum Direction {
		FORWARD, REVERSE
	}

	public record Mapping(String name, Direction direction, Map<String, String> attributes, String remarks,
			String definition) {
		public Mapping {
			Db2CreationContext.identifier(name);
			if (direction == null || attributes == null)
				throw new IllegalArgumentException("Db2 type mapping requires direction and attributes");
			var values = new TreeMap<String, String>();
			attributes.forEach((key, value) -> {
				if (!ATTRIBUTES.contains(key) || value == null || value.indexOf('\0') >= 0)
					throw new IllegalArgumentException("Unknown or invalid Db2 type mapping attribute: " + key);
				values.put(key, value);
			});
			attributes = Collections.unmodifiableMap(values);
			if (definition != null && (definition.isBlank() || definition.indexOf('\0') >= 0))
				throw new IllegalArgumentException("Db2 type mapping definition must be executable SQL");
			if (remarks != null && remarks.indexOf('\0') >= 0)
				throw new IllegalArgumentException("Invalid Db2 type mapping comment");
		}

		public Mapping withDefinition(String sql) {
			return new Mapping(name, direction, attributes, remarks, sql);
		}
	}

	public record Snapshot(int format, List<Mapping> mappings, boolean orderKnown) {
		public Snapshot(int format, List<Mapping> mappings) {
			this(format, mappings, true);
		}

		public Snapshot {
			if (format != 1 || mappings == null)
				throw new IllegalArgumentException("Invalid Db2 type mapping snapshot");
			mappings = List.copyOf(mappings);
			var names = new HashSet<String>();
			for (var mapping : mappings)
				if (!names.add(mapping.name()))
					throw new IllegalArgumentException("Duplicate Db2 type mapping: " + mapping.name());
		}
	}

	public static Snapshot get(Catalog catalog) {
		String json = catalog.getSpecifics().get(SPECIFICS_KEY);
		if (json == null)
			return new Snapshot(1, List.of());
		Snapshot result = new JsonConverter().getObjectMapper().readerFor(Snapshot.class)
				.with(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);
		if (result == null)
			throw new IllegalArgumentException("Invalid Db2 type mapping snapshot");
		return result;
	}

	public static void set(Catalog catalog, List<Mapping> mappings) {
		set(catalog, new Snapshot(1, mappings));
	}

	public static void set(Catalog catalog, Snapshot snapshot) {
		if (snapshot == null)
			throw new IllegalArgumentException("Db2 type mapping snapshot must not be null");
		if (snapshot.mappings().isEmpty()) {
			var retained = new com.sqlapp.data.schemas.DbInfo();
			catalog.getSpecifics().forEach((key, value) -> {
				if (!SPECIFICS_KEY.equalsIgnoreCase(key))
					retained.put(key, value);
			});
			catalog.setSpecifics(retained);
		} else
			catalog.getSpecifics().put(SPECIFICS_KEY, new JsonConverter().toJsonString(snapshot));
	}
}
