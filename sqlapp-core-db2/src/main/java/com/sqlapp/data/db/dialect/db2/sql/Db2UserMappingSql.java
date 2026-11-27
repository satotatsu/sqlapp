/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.*;
import com.sqlapp.data.db.dialect.db2.Db2UserMappings.*;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.schemas.Catalog;

final class Db2UserMappingSql {
	private Db2UserMappingSql() {
	}

	static List<String> create(Catalog catalog, Dialect dialect) {
		var snapshot = validate(catalog, dialect);
		return snapshot.mappings().stream().map(Db2UserMappingSql::create).toList();
	}

	private static Snapshot validate(Catalog catalog, Dialect dialect) {
		var snapshot = Db2UserMappings.get(catalog);
		if (!snapshot.mappings().isEmpty() && !(dialect instanceof Db2_1110))
			throw new IllegalArgumentException("Db2 user mapping generation requires LUW 11.1 or later");
		for (var server : Db2Federation.get(catalog).servers()) {
			if (server.options().containsKey("FED_PROXY_USER") && snapshot.mappings().stream().anyMatch(
					mapping -> mapping.serverName().equals(server.name()) && mapping.principal() == Principal.PUBLIC))
				throw new IllegalArgumentException(
						"Db2 PUBLIC mapping cannot coexist with FED_PROXY_USER: " + server.name());
		}
		return snapshot;
	}

	private static String create(Mapping mapping) {
		if (mapping.definition() != null)
			return mapping.definition();
		if (!mapping.unavailableOptions().isEmpty() || !mapping.options().containsKey("REMOTE_PASSWORD"))
			throw new IllegalArgumentException(
					"Db2 user mapping requires explicit intended credentials or a complete CREATE definition: "
							+ mapping.id());
		return "CREATE USER MAPPING" + target(mapping) + " OPTIONS (" + mapping.options().entrySet().stream()
				.map(e -> e.getKey() + " " + literal(e.getValue())).collect(java.util.stream.Collectors.joining(", "))
				+ ")";
	}

	static Plan diff(Catalog original, Catalog target, Dialect dialect) {
		var oldMappings = index(validate(original, dialect));
		var newMappings = index(validate(target, dialect));
		List<String> before = new ArrayList<>(), after = new ArrayList<>();
		oldMappings.forEach((id, mapping) -> {
			if (!newMappings.containsKey(id))
				before.add("DROP USER MAPPING" + target(mapping));
		});
		newMappings.forEach((id, mapping) -> {
			Mapping previous = oldMappings.get(id);
			if (previous == null) {
				after.add(create(mapping));
				return;
			}
			if (!Objects.equals(previous.definition(), mapping.definition()))
				throw new IllegalArgumentException(
						"Db2 user mapping definition changes require an explicit native plan: " + id);
			boolean changed = !previous.options().equals(mapping.options())
					|| !previous.unavailableOptions().equals(mapping.unavailableOptions());
			if (!changed)
				return;
			if (mapping.definition() != null)
				throw new IllegalArgumentException(
						"Db2 complete user mapping definition is authoritative; supplemental changes require an explicit native plan: "
								+ id);
			if (!mapping.options().containsKey("REMOTE_PASSWORD")
					&& !mapping.unavailableOptions().contains("REMOTE_PASSWORD"))
				throw new IllegalArgumentException("Db2 user mapping password cannot be removed implicitly: " + id);
			for (String unavailable : mapping.unavailableOptions())
				if (!previous.unavailableOptions().contains(unavailable))
					throw new IllegalArgumentException(
							"Db2 user mapping cannot replace a known option with an unreadable value: " + id);
			var keys = new TreeSet<>(previous.options().keySet());
			keys.addAll(previous.unavailableOptions());
			keys.addAll(mapping.options().keySet());
			List<String> changes = new ArrayList<>();
			for (String key : keys) {
				if (mapping.unavailableOptions().contains(key))
					continue;
				boolean exists = previous.options().containsKey(key) || previous.unavailableOptions().contains(key);
				if (!mapping.options().containsKey(key))
					changes.add("DROP " + key);
				else if (!Objects.equals(previous.options().get(key), mapping.options().get(key)))
					changes.add((exists ? "SET " : "ADD ") + key + " " + literal(mapping.options().get(key)));
			}
			if (!changes.isEmpty())
				after.add("ALTER USER MAPPING" + target(mapping) + " OPTIONS (" + String.join(", ", changes) + ")");
		});
		return new Plan(before, after);
	}

	private static Map<List<Object>, Mapping> index(Snapshot snapshot) {
		var result = new LinkedHashMap<List<Object>, Mapping>();
		snapshot.mappings().forEach(mapping -> result.put(mapping.id(), mapping));
		return result;
	}

	private static String target(Mapping mapping) {
		return " FOR "
				+ (mapping.principal() == Principal.PUBLIC ? "PUBLIC"
						: Db2CreationContext.identifier(mapping.authorizationName()))
				+ " SERVER " + Db2CreationContext.identifier(mapping.serverName());
	}

	private static String literal(String value) {
		return "'" + value.replace("'", "''") + "'";
	}

	record Plan(List<String> before, List<String> after) {
		Plan {
			before = List.copyOf(before);
			after = List.copyOf(after);
		}
	}
}
