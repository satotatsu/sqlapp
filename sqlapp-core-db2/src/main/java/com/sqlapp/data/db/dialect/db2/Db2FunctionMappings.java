/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2;

import java.util.*;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.util.JsonConverter;

/**
 * Ordered user-defined federation function mappings. Later mappings take
 * precedence.
 */
public final class Db2FunctionMappings {
	public static final String SPECIFICS_KEY = "db2.functionMappings.v1";

	private Db2FunctionMappings() {
	}

	public record ParameterOption(int ordinal, String location, String name, String value) {
		public ParameterOption {
			if (ordinal < 0 || !Set.of("L", "R").contains(location == null ? "" : location))
				throw new IllegalArgumentException("Invalid Db2 function mapping parameter position/location");
			name = optionName(name);
			value = text(value);
		}
	}

	public record Mapping(String name, String functionSchema, String functionName, String specificName,
			String serverName, String serverType, String serverVersion, String wrapperName, Boolean infix,
			Map<String, String> options, List<ParameterOption> parameterOptions, String remarks, String definition) {
		public Mapping {
			Db2CreationContext.identifier(name);
			for (String identifier : Arrays.asList(functionSchema, functionName, specificName, serverName, serverType,
					wrapperName))
				if (identifier != null)
					Db2CreationContext.identifier(identifier);
			if (serverVersion != null)
				text(serverVersion);
			if (options == null || parameterOptions == null)
				throw new IllegalArgumentException("Db2 mapping requires option collections");
			var sorted = new TreeMap<String, String>();
			options.forEach((key, value) -> {
				if (sorted.putIfAbsent(optionName(key), text(value)) != null)
					throw new IllegalArgumentException("Duplicate Db2 mapping option: " + key);
			});
			options = Collections.unmodifiableMap(sorted);
			parameterOptions = List.copyOf(parameterOptions);
			var ids = new HashSet<List<Object>>();
			for (var option : parameterOptions)
				if (!ids.add(List.of(option.ordinal(), option.location(), option.name())))
					throw new IllegalArgumentException("Duplicate Db2 parameter option");
			if (definition != null && text(definition).isBlank())
				throw new IllegalArgumentException("Db2 mapping definition must be executable SQL");
			if (remarks != null)
				text(remarks);
		}

		/**
		 * Resolves notation that the documented mapping catalog cannot reliably expose.
		 */
		public Mapping withInfix(boolean value) {
			return new Mapping(name, functionSchema, functionName, specificName, serverName, serverType, serverVersion,
					wrapperName, value, options, parameterOptions, remarks, definition);
		}

		public Mapping withDefinition(String sql) {
			return new Mapping(name, functionSchema, functionName, specificName, serverName, serverType, serverVersion,
					wrapperName, infix, options, parameterOptions, remarks, sql);
		}
	}

	public record Snapshot(int format, List<Mapping> mappings, boolean orderKnown) {
		public Snapshot(int format, List<Mapping> mappings) {
			this(format, mappings, true);
		}

		public Snapshot {
			if (format != 1 || mappings == null)
				throw new IllegalArgumentException("Invalid Db2 function mappings snapshot");
			mappings = List.copyOf(mappings);
			var names = new HashSet<String>();
			for (var mapping : mappings)
				if (!names.add(mapping.name()))
					throw new IllegalArgumentException("Duplicate Db2 function mapping: " + mapping.name());
		}
	}

	public static Snapshot get(Catalog catalog) {
		String json = catalog.getSpecifics().get(SPECIFICS_KEY);
		if (json == null)
			return new Snapshot(1, List.of());
		Snapshot result = new JsonConverter().getObjectMapper().readerFor(Snapshot.class)
				.with(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);
		if (result == null)
			throw new IllegalArgumentException("Invalid Db2 function mappings snapshot");
		return result;
	}

	public static void set(Catalog catalog, List<Mapping> mappings) {
		set(catalog, new Snapshot(1, mappings));
	}

	/**
	 * Retains an unresolved catalog order marker; supplying a List explicitly
	 * selects its order.
	 */
	public static void set(Catalog catalog, Snapshot snapshot) {
		if (snapshot == null)
			throw new IllegalArgumentException("Db2 function mappings snapshot must not be null");
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

	private static String text(String value) {
		if (value == null || value.indexOf('\0') >= 0)
			throw new IllegalArgumentException("Db2 mapping text must not be null or contain NUL");
		return value;
	}

	private static String optionName(String name) {
		if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*"))
			throw new IllegalArgumentException("Invalid Db2 function mapping option name");
		return name.toUpperCase(Locale.ROOT);
	}
}
