/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.Db2Federation;
import com.sqlapp.data.db.dialect.db2.Db2Federation.*;
import com.sqlapp.data.db.dialect.db2.Db2_1210;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.schemas.Catalog;

/** Native federation DDL; no implicit dependency-dropping replacement. */
final class Db2FederationSql {
	private Db2FederationSql() {
	}

	static List<String> create(Catalog catalog, Dialect dialect) {
		var snapshot = Db2Federation.get(catalog);
		List<String> result = new ArrayList<>();
		for (var wrapper : snapshot.wrappers())
			result.addAll(create(wrapper));
		for (var defaults : snapshot.defaults())
			options(result, defaultsPrefix(defaults), Map.of(), defaults.options());
		for (var server : snapshot.servers())
			result.addAll(create(server, dialect));
		return List.copyOf(result);
	}

	private static List<String> create(Wrapper wrapper) {
		List<String> result = new ArrayList<>();
		String sql = wrapper.definition();
		if (sql == null) {
			sql = "CREATE WRAPPER " + name(wrapper.name());
			if (!wrapper.defaultLibrary())
				sql += " LIBRARY " + literal(wrapper.library());
			sql += createOptions(wrapper.options());
		}
		result.add(sql);
		if (wrapper.remarks() != null)
			result.add(comment("WRAPPER", wrapper.name(), wrapper.remarks()));
		return result;
	}

	private static List<String> create(Server server, Dialect dialect) {
		List<String> result = new ArrayList<>();
		String sql = server.definition();
		if (sql == null) {
			boolean db2Family = "DRDA".equals(server.wrapperName()) || (server.type() != null
					&& (server.type().toUpperCase(java.util.Locale.ROOT).startsWith("DB2") || java.util.Set
							.of("BIGSQL", "DASHDB").contains(server.type().toUpperCase(java.util.Locale.ROOT))));
			if (db2Family && (!(dialect instanceof Db2_1210) || !"Y".equals(server.options().get("SSO_AUTH"))))
				throw new IllegalArgumentException(
						"Db2 federation server requires a complete CREATE definition with the intended AUTHORIZATION or SSO strategy: "
								+ server.name());
			sql = "CREATE SERVER " + name(server.name());
			if (server.type() != null)
				sql += " TYPE " + type(server.type());
			if (server.version() != null)
				sql += " VERSION " + literal(server.version());
			sql += " WRAPPER " + name(server.wrapperName()) + createOptions(server.options());
		}
		result.add(sql);
		if (server.remarks() != null)
			result.add(comment("SERVER", server.name(), server.remarks()));
		return result;
	}

	static List<String> diff(Catalog original, Catalog target, Dialect dialect) {
		var before = Db2Federation.get(original);
		var after = Db2Federation.get(target);
		var oldWrappers = index(before.wrappers(), Wrapper::name);
		var newWrappers = index(after.wrappers(), Wrapper::name);
		var oldServers = index(before.servers(), Server::name);
		var newServers = index(after.servers(), Server::name);
		if (!newWrappers.keySet().containsAll(oldWrappers.keySet())
				|| !newServers.keySet().containsAll(oldServers.keySet()))
			throw new IllegalArgumentException(
					"Db2 federation removal requires an explicit DROP plan: dropping wrappers or servers can also drop dependent mappings and nicknames");
		List<String> result = new ArrayList<>();
		newWrappers.forEach((id, wrapper) -> {
			Wrapper previous = oldWrappers.get(id);
			if (previous == null)
				result.addAll(create(wrapper));
			else {
				if (wrapper.definition() != null && !previous.options().equals(wrapper.options()))
					throw new IllegalArgumentException(
							"Db2 complete wrapper definition is authoritative; supplemental option changes require an explicit native plan: "
									+ id);
				if (!Objects.equals(previous.definition(), wrapper.definition())
						|| !Objects.equals(previous.defaultLibrary(), wrapper.defaultLibrary())
						|| (!Boolean.TRUE.equals(wrapper.defaultLibrary())
								&& !Objects.equals(previous.library(), wrapper.library())))
					throw new IllegalArgumentException(
							"Db2 wrapper library/definition changes require an explicit recreation plan: " + id);
				options(result, "ALTER WRAPPER " + name(id), previous.options(), wrapper.options());
				if (!Objects.equals(previous.remarks(), wrapper.remarks()))
					result.add(comment("WRAPPER", id, wrapper.remarks()));
			}
		});
		var oldDefaults = index(before.defaults(), Defaults::id);
		var newDefaults = index(after.defaults(), Defaults::id);
		oldDefaults.forEach((id, previous) -> {
			Defaults next = newDefaults.get(id);
			Map<String, String> targetOptions = next == null ? Map.of() : next.options();
			if (!previous.options().equals(targetOptions))
				options(result, defaultsPrefix(previous), previous.options(), targetOptions);
		});
		newDefaults.forEach((id, defaults) -> {
			if (!oldDefaults.containsKey(id))
				options(result, defaultsPrefix(defaults), Map.of(), defaults.options());
		});
		newServers.forEach((id, server) -> {
			Server previous = oldServers.get(id);
			if (previous == null)
				result.addAll(create(server, dialect));
			else {
				if (server.definition() != null && (!previous.options().equals(server.options())
						|| !Objects.equals(previous.version(), server.version())))
					throw new IllegalArgumentException(
							"Db2 complete server definition is authoritative; supplemental changes require an explicit native plan: "
									+ id);
				if (!Objects.equals(previous.wrapperName(), server.wrapperName())
						|| !Objects.equals(previous.type(), server.type())
						|| !Objects.equals(previous.definition(), server.definition()))
					throw new IllegalArgumentException(
							"Db2 server wrapper/type/definition changes require an explicit recreation plan: " + id);
				if (!Objects.equals(previous.version(), server.version())) {
					if (server.version() == null)
						throw new IllegalArgumentException("Db2 server version cannot be removed by ALTER: " + id);
					result.add("ALTER SERVER " + name(id) + " VERSION " + literal(server.version()));
				}
				options(result, "ALTER SERVER " + name(id), previous.options(), server.options());
				if (!Objects.equals(previous.remarks(), server.remarks()))
					result.add(comment("SERVER", id, server.remarks()));
			}
		});
		return List.copyOf(result);
	}

	private static <K, T> Map<K, T> index(List<T> objects, Function<T, K> key) {
		Map<K, T> result = new LinkedHashMap<>();
		objects.forEach(object -> result.put(key.apply(object), object));
		return result;
	}

	private static String defaultsPrefix(Defaults defaults) {
		if (defaults.type() == null)
			throw new IllegalArgumentException(
					"Db2 server category defaults without TYPE require an explicit native configuration plan");
		String sql = "ALTER SERVER TYPE " + type(defaults.type());
		if (defaults.version() != null)
			sql += " VERSION " + literal(defaults.version());
		if (defaults.wrapperName() != null)
			sql += " WRAPPER " + name(defaults.wrapperName());
		return sql;
	}

	private static String createOptions(Map<String, String> options) {
		if (options.isEmpty())
			return "";
		return " OPTIONS (" + options.entrySet().stream().sorted(Map.Entry.comparingByKey())
				.map(e -> e.getKey() + " " + literal(e.getValue())).collect(java.util.stream.Collectors.joining(", "))
				+ ")";
	}

	private static void options(List<String> result, String prefix, Map<String, String> original,
			Map<String, String> target) {
		var keys = new TreeSet<>(original.keySet());
		keys.addAll(target.keySet());
		List<String> changes = new ArrayList<>();
		for (String key : keys) {
			if (Objects.equals(original.get(key), target.get(key)))
				continue;
			changes.add(target.containsKey(key)
					? (original.containsKey(key) ? "SET " : "ADD ") + key + " " + literal(target.get(key))
					: "DROP " + key);
		}
		if (!changes.isEmpty())
			result.add(prefix + " OPTIONS (" + String.join(", ", changes) + ")");
	}

	private static String type(String value) {
		if (!value.matches("[A-Za-z0-9_]+(?:[./-][A-Za-z0-9_]+)*"))
			throw new IllegalArgumentException(
					"Unsupported Db2 federation server TYPE; provide a complete definition: " + value);
		return value;
	}

	private static String name(String value) {
		return Db2CreationContext.identifier(value);
	}

	private static String literal(String value) {
		return value == null ? "NULL" : "'" + value.replace("'", "''") + "'";
	}

	private static String comment(String kind, String name, String value) {
		return "COMMENT ON " + kind + " " + name(name) + " IS " + literal(value);
	}
}
