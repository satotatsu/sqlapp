/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.sqlapp.data.db.dialect.postgres.PostgresSchemaObject;
import com.sqlapp.data.db.dialect.postgres.PostgresSchemaObjects;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.properties.SpecificsProperty;

/** Definition-backed objects have no portable shared ALTER factory. */
final class PostgresSchemaObjectDiff {
	final List<SqlOperation> drops = new ArrayList<>();
	final List<SqlOperation> before = new ArrayList<>();
	final List<SqlOperation> foreignTables = new ArrayList<>();
	final List<SqlOperation> after = new ArrayList<>();
	private final DbCommonObject<?> original;
	private final DbCommonObject<?> target;
	private final Map<String, String> externalValues;
	private final com.sqlapp.data.db.dialect.Dialect dialect;

	PostgresSchemaObjectDiff(DbCommonObject<?> original, DbCommonObject<?> target, Map<String, String> externalValues,
			com.sqlapp.data.db.dialect.Dialect dialect) {
		this.original = original;
		this.target = target;
		this.externalValues = Map.copyOf(externalValues);
		this.dialect = dialect;
		var oldObjects = index(PostgresSchemaObjects.get((SpecificsProperty<?>) original));
		var newObjects = index(PostgresSchemaObjects.get((SpecificsProperty<?>) target));
		var removed = new ArrayList<>(oldObjects.values());
		java.util.Collections.reverse(removed);
		removed.sort(
				java.util.Comparator.comparingInt((PostgresSchemaObject object) -> object.kind().ordinal()).reversed());
		for (var old : removed)
			if (!newObjects.containsKey(key(old)))
				add(drops, old.dropDefinition(), SqlType.DROP);
		var ordered = new ArrayList<>(newObjects.values());
		// Stable within each kind: the bulk reader already orders inherited foreign
		// parents before children.
		ordered.sort(java.util.Comparator.comparingInt(object -> object.kind().ordinal()));
		for (var next : ordered) {
			var old = oldObjects.get(key(next));
			var destination = switch (next.kind()) {
			case COLLATION, EXTENSION, FOREIGN_DATA_WRAPPER, SERVER, USER_MAPPING -> before;
			case FOREIGN_TABLE -> foreignTables;
			default -> after;
			};
			if (old == null) {
				for (String sql : PostgresSchemaObjects.createSql(next, externalValues))
					add(destination, sql, SqlType.CREATE);
			} else if (!old.equals(next) || externalValues.containsKey(next.id())
					|| externalValues.keySet().stream().anyMatch(k -> k.startsWith(next.id() + ":"))) {
				for (String sql : alter(old, next))
					add(destination, sql, SqlType.ALTER);
			}
		}
	}

	private static String key(PostgresSchemaObject object) {
		// Extension schema is its installation location, not part of its database-wide
		// identity.
		return object.kind() == PostgresSchemaObject.Kind.EXTENSION ? "EXTENSION:" + object.name() : object.id();
	}

	private static Map<String, PostgresSchemaObject> index(List<PostgresSchemaObject> objects) {
		var map = new LinkedHashMap<String, PostgresSchemaObject>();
		for (var object : objects)
			if (map.put(key(object), object) != null)
				throw new IllegalArgumentException("Duplicate PostgreSQL identity: " + key(object));
		return map;
	}

	private void add(List<SqlOperation> list, String sql, SqlType type) {
		list.add(new SqlOperation(sql, type, original, target));
	}

	private List<String> alter(PostgresSchemaObject old, PostgresSchemaObject next) {
		var sql = new ArrayList<String>();
		String a = old.definition().getFirst(), b = next.definition().getFirst();
		String alter = "ALTER " + target(next);
		boolean replaced = false;
		if (!a.equals(b)) {
			var x = body(old);
			var y = body(next);
			switch (next.kind()) {
			case ROW_SECURITY -> sql.add(b);
			case POLICY, STATISTICS -> {
				boolean inPlace = next.kind() == PostgresSchemaObject.Kind.POLICY ? policy(sql, alter, x, y)
						: statistics(sql, alter, x, y);
				if (!inPlace) {
					// PostgreSQL cannot ALTER policy command/permissiveness or statistics
					// expressions/kinds.
					// These objects contain no user data; RESTRICT preserves dependent objects.
					sql.add(old.dropDefinition());
					sql.addAll(PostgresSchemaObjects.createSql(next, externalValues));
					replaced = true;
				}
			}
			case EXTENSION -> {
				if (!Objects.equals(old.attributes().get("owner"), next.attributes().get("owner")))
					fail(next, "extension ownership has no ALTER syntax");
				String version = y.after("VERSION");
				if (!x.after("VERSION").equals(version)) {
					if (version.isEmpty())
						fail(next, "extension update requires an explicit target version");
					sql.add(alter + " UPDATE TO " + version);
				}
				if (!Objects.equals(old.schemaName(), next.schemaName()))
					sql.add(alter + " SET SCHEMA " + quote(next.schemaName()));
			}
			case FOREIGN_DATA_WRAPPER -> {
				for (String keyword : List.of("HANDLER", "VALIDATOR")) {
					String previous = functionClause(x, keyword), value = functionClause(y, keyword);
					if (!previous.equals(value))
						sql.add(alter + " " + value);
				}
				options(sql, alter, optionMap(x), optionMap(y), next);
			}
			case SERVER -> {
				if (!x.clause("TYPE", "VERSION", "FOREIGN DATA WRAPPER")
						.equals(y.clause("TYPE", "VERSION", "FOREIGN DATA WRAPPER"))
						|| !x.clause("FOREIGN DATA WRAPPER", "OPTIONS")
								.equals(y.clause("FOREIGN DATA WRAPPER", "OPTIONS"))) {
					fail(next,
							"server TYPE/foreign data wrapper is immutable; explicitly migrate its foreign tables and mappings");
				}
				String version = y.clause("VERSION", "FOREIGN DATA WRAPPER");
				if (!x.clause("VERSION", "FOREIGN DATA WRAPPER").equals(version))
					sql.add(alter + " VERSION " + (version.isEmpty() ? "NULL" : version));
				options(sql, alter, optionMap(x), optionMap(y), next);
			}
			case USER_MAPPING -> options(sql, alter, optionMap(x), optionMap(y), next);
			case FOREIGN_TABLE -> foreignTable(sql, alter, x, y, next);
			case PUBLICATION -> publication(sql, alter, x, y, next);
			case SUBSCRIPTION -> subscription(sql, alter, x, y, next);
			case COLLATION -> fail(next,
					"collation locale/provider/determinism is immutable; explicitly migrate dependent indexes and columns");
			}
		}
		if (next.kind() == PostgresSchemaObject.Kind.SUBSCRIPTION) {
			if (externalValues.containsKey(next.id())) {
				String connection = externalValues.get(next.id());
				if (connection == null || connection.isBlank() || connection.indexOf('\0') >= 0)
					fail(next, "subscription connection must not be empty or contain NUL");
				sql.add(alter + " CONNECTION " + literal(connection));
			}
			if (!Objects.equals(old.attributes().get("enabled"), next.attributes().get("enabled"))) {
				String enabled = next.attributes().get("enabled");
				if (!List.of("true", "false", "t", "f").contains(enabled == null ? "" : enabled))
					fail(next, "source enabled must be explicitly true or false");
				if (enabled.equals("true") || enabled.equals("t"))
					sql.add(alter + " ENABLE");
				else
					sql.add(0, alter + " DISABLE");
			}
		}
		if (!replaced)
			supplemental(sql, old, next);
		if (a.equals(b) && externalValues.keySet().stream().anyMatch(k -> k.startsWith(next.id() + ":"))) {
			if (List.of(PostgresSchemaObject.Kind.SERVER, PostgresSchemaObject.Kind.USER_MAPPING,
					PostgresSchemaObject.Kind.FOREIGN_DATA_WRAPPER).contains(next.kind())) {
				options(sql, alter, optionMap(body(old)), optionMap(body(next)), next);
			} else if (next.kind() == PostgresSchemaObject.Kind.FOREIGN_TABLE) {
				foreignTable(sql, alter, body(old), body(next), next);
			}
		}
		return sql;
	}

	private static PostgresObjectSql body(PostgresSchemaObject object) {
		int header = switch (object.kind()) {
		case FOREIGN_DATA_WRAPPER -> 5;
		case USER_MAPPING, POLICY -> 7;
		case FOREIGN_TABLE -> 6;
		case COLLATION, STATISTICS -> 5;
		case ROW_SECURITY -> 0;
		default -> 3;
		};
		var sql = new PostgresObjectSql(object.definition().getFirst());
		return header == 0 ? sql : new PostgresObjectSql(sql.skip(header));
	}

	private static String functionClause(PostgresObjectSql sql, String keyword) {
		if (sql.find("NO " + keyword) >= 0)
			return "NO " + keyword;
		String value = sql.clause(keyword, "HANDLER", "NO HANDLER", "VALIDATOR", "NO VALIDATOR", "OPTIONS");
		return value.isEmpty() ? "NO " + keyword : keyword + " " + value;
	}

	private static boolean statistics(List<String> sql, String alter, PostgresObjectSql old, PostgresObjectSql next) {
		var a = old.split(";");
		var b = next.split(";");
		if (!a.getFirst().equals(b.getFirst()))
			return false;
		if (!a.equals(b)) {
			if (b.size() > 1)
				sql.addAll(b.subList(1, b.size()));
			else
				sql.add(alter + " SET STATISTICS -1");
		}
		return true;
	}

	private static boolean policy(List<String> sql, String alter, PostgresObjectSql old, PostgresObjectSql next) {
		if (!old.clause("AS", "FOR", "TO", "USING", "WITH CHECK")
				.equals(next.clause("AS", "FOR", "TO", "USING", "WITH CHECK"))
				|| !old.clause("FOR", "TO", "USING", "WITH CHECK")
						.equals(next.clause("FOR", "TO", "USING", "WITH CHECK")))
			return false;
		var changes = new ArrayList<String>();
		for (String keyword : List.of("TO", "USING", "WITH CHECK")) {
			String a = old.clause(keyword, "TO", "USING", "WITH CHECK"),
					b = next.clause(keyword, "TO", "USING", "WITH CHECK");
			if (!a.equals(b)) {
				if (b.isEmpty() && !keyword.equals("TO"))
					return false;
				changes.add(keyword + " " + (b.isEmpty() ? "PUBLIC" : b));
			}
		}
		if (!changes.isEmpty())
			sql.add(alter + " " + String.join(" ", changes));
		return true;
	}

	private static Map<String, String> optionMap(PostgresObjectSql sql) {
		return pairs(new PostgresObjectSql(sql.after("OPTIONS")).group(), false);
	}

	private static Map<String, String> pairs(String group, boolean equals) {
		var result = new LinkedHashMap<String, String>();
		for (String entry : new PostgresObjectSql(group).split(",")) {
			var parsed = new PostgresObjectSql(entry);
			String key = PostgresObjectSql.identifier(parsed.first());
			String value = parsed.rest();
			if (equals) {
				if (!value.startsWith("="))
					throw new IllegalArgumentException("Expected SQL option assignment: " + entry);
				value = value.substring(1).strip();
			}
			if (value.isEmpty() || result.put(key, value) != null)
				throw new IllegalArgumentException("Invalid or duplicate SQL option: " + key);
		}
		return result;
	}

	private void options(List<String> sql, String alter, Map<String, String> old, Map<String, String> next,
			PostgresSchemaObject object) {
		var changes = new ArrayList<String>();
		for (String key : old.keySet())
			if (!next.containsKey(key))
				changes.add("DROP " + quote(key));
		for (var entry : next.entrySet())
			if (!Objects.equals(old.get(entry.getKey()), entry.getValue())
					|| externalValues.containsKey(object.id() + ":" + entry.getKey())) {
				changes.add((old.containsKey(entry.getKey()) ? "SET " : "ADD ") + quote(entry.getKey()) + " "
						+ entry.getValue());
			}
		if (!changes.isEmpty())
			sql.add(resolve(object, alter + " OPTIONS (" + String.join(", ", changes) + ")"));
	}

	private String resolve(PostgresSchemaObject object, String sql) {
		return PostgresSchemaObjects.createSql(new PostgresSchemaObject(object.kind(), object.schemaName(),
				object.name(), object.parentName(), List.of(sql), object.dropDefinition(), object.attributes()),
				externalValues).getFirst();
	}

	private void publication(List<String> sql, String alter, PostgresObjectSql old, PostgresObjectSql next,
			PostgresSchemaObject object) {
		String a = old.clause("FOR", "WITH"), b = next.clause("FOR", "WITH");
		if (!a.equals(b)) {
			if (a.equalsIgnoreCase("ALL TABLES") || b.equalsIgnoreCase("ALL TABLES"))
				fail(object, "FOR ALL TABLES cannot be switched by ALTER PUBLICATION");
			if (!b.isEmpty())
				sql.add(alter + " SET " + b);
			else if (!a.isEmpty()) {
				var members = new PostgresObjectSql(a).split(",").stream()
						.map(member -> new PostgresObjectSql(new PostgresObjectSql(member).before("WHERE")).before("("))
						.toList();
				sql.add(alter + " DROP " + String.join(", ", members));
			}
		}
		var previous = pairs(new PostgresObjectSql(old.after("WITH")).group(), true);
		var values = pairs(new PostgresObjectSql(next.after("WITH")).group(), true);
		var changes = new ArrayList<String>();
		for (var entry : values.entrySet())
			if (!Objects.equals(previous.get(entry.getKey()), entry.getValue()))
				changes.add(entry.getKey() + " = " + entry.getValue());
		for (String key : previous.keySet())
			if (!values.containsKey(key))
				fail(object, "publication option " + key + " requires an explicit target value");
		if (!changes.isEmpty())
			sql.add(alter + " SET (" + String.join(", ", changes) + ")");
	}

	private void subscription(List<String> sql, String alter, PostgresObjectSql old, PostgresObjectSql next,
			PostgresSchemaObject object) {
		if (!old.clause("CONNECTION", "PUBLICATION").equals(next.clause("CONNECTION", "PUBLICATION"))) {
			sql.add(resolve(object, alter + " CONNECTION " + next.clause("CONNECTION", "PUBLICATION")));
		}
		String publications = next.clause("PUBLICATION", "WITH");
		if (!old.clause("PUBLICATION", "WITH").equals(publications))
			sql.add(alter + " SET PUBLICATION " + publications + " WITH (refresh = false)");
		var previous = pairs(new PostgresObjectSql(old.after("WITH")).group(), true);
		var values = pairs(new PostgresObjectSql(next.after("WITH")).group(), true);
		var changes = new ArrayList<String>();
		for (var entry : values.entrySet())
			if (!Objects.equals(previous.get(entry.getKey()), entry.getValue())) {
				if (List.of("connect", "create_slot", "copy_data", "enabled").contains(entry.getKey()))
					fail(object, "creation-only subscription option " + entry.getKey()
							+ " cannot be changed here; use the enabled attribute for ENABLE/DISABLE");
				if (entry.getKey().equals("two_phase")
						&& dialect.compareTo(com.sqlapp.data.db.dialect.postgres.DialectHolder.postgreSQL180) < 0) {
					fail(object, "ALTER two_phase requires PostgreSQL 18 or later");
				}
				if (List.of("two_phase", "failover", "slot_name").contains(entry.getKey())
						&& !List.of("false", "f").contains(object.attributes().getOrDefault("enabled", "false"))) {
					fail(object, "disable the subscription before changing " + entry.getKey());
				}
				changes.add(entry.getKey() + " = " + entry.getValue());
			}
		for (String key : previous.keySet())
			if (!values.containsKey(key))
				fail(object, "subscription option " + key + " requires an explicit target value");
		if (!changes.isEmpty())
			sql.add(alter + " SET (" + String.join(", ", changes) + ")");
	}

	private void foreignTable(List<String> sql, String alter, PostgresObjectSql old, PostgresObjectSql next,
			PostgresSchemaObject object) {
		if (!old.clause("SERVER", "OPTIONS").equals(next.clause("SERVER", "OPTIONS")))
			fail(object, "foreign server cannot be changed in place");
		if (!old.clause("INHERITS", "SERVER").equals(next.clause("INHERITS", "SERVER"))
				|| !old.clause("PARTITION OF", "SERVER").equals(next.clause("PARTITION OF", "SERVER")))
			fail(object, "inheritance/partition changes require an explicit attachment plan");
		if (old.find("PARTITION OF") < 0 && next.find("PARTITION OF") < 0) {
			var previous = columns(old.group());
			var values = columns(next.group());
			var oldOrder = previous.keySet().stream().filter(k -> k.startsWith("column:") && values.containsKey(k))
					.toList();
			var newOrder = values.keySet().stream().filter(k -> k.startsWith("column:") && previous.containsKey(k))
					.toList();
			if (!oldOrder.equals(newOrder))
				fail(object, "column order cannot be changed by ALTER FOREIGN TABLE");
			boolean added = false;
			for (String key : values.keySet())
				if (key.startsWith("column:")) {
					if (!previous.containsKey(key))
						added = true;
					else if (added)
						fail(object, "new foreign columns must follow existing columns");
				}
			var removals = new ArrayList<>(previous.entrySet());
			removals.sort(java.util.Comparator.comparing(e -> !e.getKey().startsWith("constraint:")));
			for (var entry : removals) {
				var value = values.get(entry.getKey());
				if (value == null || entry.getKey().startsWith("constraint:") && !entry.getValue().equals(value)) {
					var parsed = new PostgresObjectSql(entry.getValue());
					sql.add(alter + " DROP "
							+ (entry.getKey().startsWith("constraint:")
									? "CONSTRAINT " + new PostgresObjectSql(parsed.rest()).first()
									: "COLUMN " + parsed.first()));
				}
			}
			for (var entry : values.entrySet()) {
				String previousValue = previous.get(entry.getKey());
				if (previousValue == null
						|| entry.getKey().startsWith("constraint:") && !entry.getValue().equals(previousValue)) {
					sql.add(resolve(object, alter + " ADD "
							+ (entry.getKey().startsWith("constraint:") ? "" : "COLUMN ") + entry.getValue()));
				} else if (!entry.getValue().equals(previousValue)
						|| externalValues.keySet().stream().anyMatch(k -> k.startsWith(object.id() + ":"))) {
					column(sql, alter, new PostgresObjectSql(previousValue), new PostgresObjectSql(entry.getValue()),
							object);
				}
			}
		}
		options(sql, alter, optionMap(new PostgresObjectSql(old.after("SERVER"))),
				optionMap(new PostgresObjectSql(next.after("SERVER"))), object);
	}

	private static Map<String, String> columns(String group) {
		var result = new LinkedHashMap<String, String>();
		for (String value : new PostgresObjectSql(group).split(",")) {
			var sql = new PostgresObjectSql(value);
			String key = sql.first().equalsIgnoreCase("CONSTRAINT")
					? "constraint:" + PostgresObjectSql.identifier(new PostgresObjectSql(sql.rest()).first())
					: "column:" + PostgresObjectSql.identifier(sql.first());
			if (result.put(key, value) != null)
				throw new IllegalArgumentException("Duplicate foreign table component: " + key);
		}
		return result;
	}

	private void column(List<String> sql, String alter, PostgresObjectSql old, PostgresObjectSql next,
			PostgresSchemaObject object) {
		String prefix = alter + " ALTER COLUMN " + next.first();
		var a = new PostgresObjectSql(old.rest());
		var b = new PostgresObjectSql(next.rest());
		if (!a.after("GENERATED").equals(b.after("GENERATED")))
			fail(object, "generated column changes require an explicit expression migration");
		String typeA = type(a), typeB = type(b);
		if (!typeA.equals(typeB))
			sql.add(prefix + " TYPE " + typeB);
		String defaultA = a.clause("DEFAULT", "NOT NULL"), defaultB = b.clause("DEFAULT", "NOT NULL");
		if (!defaultA.equals(defaultB))
			sql.add(prefix + (defaultB.isEmpty() ? " DROP DEFAULT" : " SET DEFAULT " + defaultB));
		if ((a.find("NOT NULL") >= 0) != (b.find("NOT NULL") >= 0))
			sql.add(prefix + (b.find("NOT NULL") >= 0 ? " SET NOT NULL" : " DROP NOT NULL"));
		options(sql, prefix, optionMap(a), optionMap(b), object);
	}

	private static String type(PostgresObjectSql sql) {
		String value = sql.before("OPTIONS");
		for (String keyword : List.of("DEFAULT", "NOT NULL", "GENERATED"))
			value = new PostgresObjectSql(value).before(keyword);
		return value;
	}

	private void supplemental(List<String> sql, PostgresSchemaObject old, PostgresSchemaObject next) {
		var a = old.definition().subList(1, old.definition().size());
		var b = next.definition().subList(1, next.definition().size());
		if (!Objects.equals(old.attributes().get("owner"), next.attributes().get("owner"))
				&& next.attributes().get("owner") == null)
			fail(next, "target owner is required");
		if (next.kind() == PostgresSchemaObject.Kind.EXTENSION
				&& !Objects.equals(old.attributes().get("owner"), next.attributes().get("owner")))
			fail(next, "extension ownership has no ALTER syntax");
		if (old.attributes().containsKey("remarks") && !next.attributes().containsKey("remarks"))
			sql.add("COMMENT ON " + target(next) + " IS NULL");
		var oldGrants = a.stream().filter(s -> s.startsWith("GRANT ") || s.startsWith("REVOKE ")).toList();
		var newGrants = b.stream().filter(s -> s.startsWith("GRANT ") || s.startsWith("REVOKE ")).toList();
		if (!oldGrants.equals(newGrants)) {
			var revokes = new ArrayList<String>();
			for (String grant : oldGrants)
				if (grant.startsWith("GRANT ")) {
					var parsed = new PostgresObjectSql(grant);
					revokes.add("REVOKE " + parsed.clause("GRANT", "TO") + " FROM "
							+ new PostgresObjectSql(parsed.after("TO")).before("WITH GRANT OPTION"));
				}
			sql.addAll(0, revokes); // Removed columns must still exist when their old grants are revoked.
			for (String grant : newGrants)
				sql.add(resolve(next, grant));
		}
		var oldOptions = supplementalOptions(a);
		var newOptions = supplementalOptions(b);
		var prefixes = new java.util.LinkedHashSet<>(oldOptions.keySet());
		prefixes.addAll(newOptions.keySet());
		for (String prefix : prefixes)
			options(sql, prefix, oldOptions.getOrDefault(prefix, Map.of()), newOptions.getOrDefault(prefix, Map.of()),
					next);
		for (String statement : b) {
			if (statement.startsWith("GRANT ") || statement.startsWith("REVOKE "))
				continue;
			if (isSupplementalOptions(statement))
				continue;
			if (!a.contains(statement))
				sql.add(resolve(next, statement));
		}
		// Removed column comments/defaults must not silently survive.
		for (String statement : a)
			if (statement.startsWith("COMMENT ON COLUMN ") && !b.contains(statement)) {
				String prefix = new PostgresObjectSql(statement).before("IS");
				String column = PostgresObjectSql.identifier(
						new PostgresObjectSql(new PostgresObjectSql(statement).clause("COLUMN", "IS")).last());
				if (sql.stream().map(PostgresObjectSql::new).filter(s -> s.find("DROP COLUMN") >= 0)
						.anyMatch(s -> PostgresObjectSql
								.identifier(new PostgresObjectSql(s.after("DROP COLUMN")).first()).equals(column)))
					continue;
				if (b.stream().noneMatch(s -> new PostgresObjectSql(s).before("IS").equals(prefix)))
					sql.add(prefix + " IS NULL");
			}
	}

	private static boolean isSupplementalOptions(String sql) {
		return sql.startsWith("ALTER FOREIGN TABLE ") && new PostgresObjectSql(sql).find("OPTIONS") >= 0;
	}

	private static Map<String, Map<String, String>> supplementalOptions(List<String> statements) {
		var result = new LinkedHashMap<String, Map<String, String>>();
		for (String statement : statements)
			if (isSupplementalOptions(statement)) {
				var parsed = new PostgresObjectSql(statement);
				var entries = new ArrayList<String>();
				for (String entry : new PostgresObjectSql(new PostgresObjectSql(parsed.after("OPTIONS")).group())
						.split(",")) {
					var option = new PostgresObjectSql(entry);
					entries.add(option.first().equalsIgnoreCase("ADD") ? option.rest() : entry);
				}
				result.put(parsed.before("OPTIONS"), pairs(String.join(", ", entries), false));
			}
		return result;
	}

	private static String target(PostgresSchemaObject object) {
		String qualified = object.schemaName() == null ? quote(object.name())
				: quote(object.schemaName()) + "." + quote(object.name());
		return switch (object.kind()) {
		case FOREIGN_DATA_WRAPPER -> "FOREIGN DATA WRAPPER " + quote(object.name());
		case FOREIGN_TABLE -> "FOREIGN TABLE " + qualified;
		case POLICY ->
			"POLICY " + quote(object.name()) + " ON " + quote(object.schemaName()) + "." + quote(object.parentName());
		case USER_MAPPING -> "USER MAPPING FOR " + (object.name().equals("PUBLIC") ? "PUBLIC" : quote(object.name()))
				+ " SERVER " + quote(object.parentName());
		case ROW_SECURITY -> "TABLE " + qualified;
		case EXTENSION -> "EXTENSION " + quote(object.name());
		default -> object.kind().name() + " " + qualified;
		};
	}

	private static String quote(String value) {
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private static String literal(String value) {
		return "E'" + value.replace("\\", "\\\\").replace("'", "''") + "'";
	}

	private static void fail(PostgresSchemaObject object, String message) {
		throw new IllegalArgumentException(object.id() + ": " + message);
	}
}
