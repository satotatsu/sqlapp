/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.firebird.*;
import com.sqlapp.data.db.dialect.firebird.FirebirdSchemaDependencies.*;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.Schema;

/**
 * Native prerequisites first, dependency-checked removals/replacements last.
 */
final class FirebirdNativeSchemaAlter {
	record Plan(List<SqlOperation> before, List<SqlOperation> after) {
	}

	static Plan plan(Schema original, Schema target, List<SqlOperation> shared) {
		var before = objects(original);
		var after = objects(target);
		var prefix = new ArrayList<SqlOperation>();
		var replacements = new LinkedHashMap<ObjectId, List<SqlOperation>>();
		for (var entry : after.entrySet())
			if (!before.containsKey(entry.getKey()))
				addDefinitions(prefix, entry.getValue(), target);
		for (var entry : before.entrySet()) {
			var old = entry.getValue();
			var next = after.get(entry.getKey());
			if (old.equals(next))
				continue;
			validate(old);
			if (next != null) {
				validate(next);
				if (old.definition().getFirst().equals(next.definition().getFirst())) {
					comment(prefix, old, next, target);
					continue;
				}
				if (old.kind() == FirebirdSchemaObject.Kind.EXCEPTION) {
					prefix.add(new SqlOperation("ALTER" + next.definition().getFirst().substring("CREATE".length()),
							SqlType.ALTER, target));
					comment(prefix, old, next, target);
					continue;
				}
			}
			var operations = new ArrayList<SqlOperation>();
			operations.add(new SqlOperation(old.dropDefinition(), SqlType.DROP, original));
			if (next != null)
				addDefinitions(operations, next, target);
			replacements.put(entry.getKey(), operations);
		}
		if (replacements.isEmpty())
			return new Plan(prefix, List.of());
		if (original.getSpecifics().get(FirebirdSchemaDependencies.SPECIFICS_KEY) == null
				|| target.getSpecifics().get(FirebirdSchemaDependencies.SPECIFICS_KEY) == null)
			throw unsafe("native removal/replacement requires recorded original and target dependency graphs");
		for (var edge : FirebirdSchemaCreateOrder.dependencies(target))
			if (replacements.containsKey(edge.prerequisite()))
				throw unsafe("target still references a removed/replaced native object: " + edge);
		var oldObjects = FirebirdSchemaCreateOrder.objects(original);
		var newObjects = FirebirdSchemaCreateOrder.objects(target);
		var required = new LinkedHashMap<ObjectId, Set<ObjectId>>();
		replacements.keySet().forEach(key -> required.put(key, new LinkedHashSet<>()));
		for (var edge : FirebirdSchemaCreateOrder.dependencies(original)) {
			if (!replacements.containsKey(edge.prerequisite()))
				continue;
			if (replacements.containsKey(edge.dependent())) {
				required.get(edge.prerequisite()).add(edge.dependent());
				continue;
			}
			if (!oldObjects.containsKey(edge.dependent()))
				throw unsafe("native dependent is outside the migration: " + edge);
			boolean removed = !newObjects.containsKey(edge.dependent());
			boolean releases = shared.stream().anyMatch(
					operation -> edge.dependent().equals(FirebirdSchemaCreateOrder.identity(operation.getOriginal()))
							&& (removed ? operation.getSqlType() == SqlType.DROP
									: operation.getSqlType() != SqlType.SET_COMMENT));
			if (!releases)
				throw unsafe("no structural operation releases the native dependency: " + edge);
		}
		return new Plan(prefix, FirebirdSchemaCreateOrder.sorted(replacements, required, "native ALTER"));
	}

	private static Map<ObjectId, FirebirdSchemaObject> objects(Schema schema) {
		var result = new LinkedHashMap<ObjectId, FirebirdSchemaObject>();
		for (var object : FirebirdSchemaObjects.get(schema))
			result.put(new ObjectId(Kind.valueOf(object.kind().name()), object.name()), object);
		return result;
	}

	private static String type(FirebirdSchemaObject object) {
		return object.kind() == FirebirdSchemaObject.Kind.BLOB_FILTER ? "FILTER" : object.kind().name();
	}

	private static String name(FirebirdSchemaObject object) {
		return "\"" + object.name().replace("\"", "\"\"") + "\"";
	}

	private static void validate(FirebirdSchemaObject object) {
		String identity = type(object) + " " + name(object);
		String create = (object.kind() == FirebirdSchemaObject.Kind.BLOB_FILTER ? "DECLARE " : "CREATE ") + identity
				+ " ";
		if (!object.definition().getFirst().startsWith(create) || !object.dropDefinition().equals("DROP " + identity)
				|| object.definition().size() > 2 || object.definition().size() == 2
						&& !object.definition().get(1).startsWith("COMMENT ON " + identity + " IS "))
			throw unsafe("native ALTER requires the canonical CREATE/DROP/COMMENT representation: " + object.name());
	}

	private static void comment(List<SqlOperation> result, FirebirdSchemaObject old, FirebirdSchemaObject next,
			Schema target) {
		String previous = old.definition().size() == 2 ? old.definition().get(1) : null;
		String current = next.definition().size() == 2 ? next.definition().get(1) : null;
		if (!Objects.equals(previous, current))
			result.add(new SqlOperation(
					current == null ? "COMMENT ON " + type(next) + " " + name(next) + " IS NULL" : current,
					SqlType.SET_COMMENT, target));
	}

	private static void addDefinitions(List<SqlOperation> result, FirebirdSchemaObject object, Schema target) {
		for (String definition : object.definition())
			result.add(new SqlOperation(definition, SqlType.CREATE, target));
	}

	private static UnsupportedOperationException unsafe(String reason) {
		return new UnsupportedOperationException(
				"Firebird native Schema ALTER requires an explicit staged migration: " + reason);
	}

	private FirebirdNativeSchemaAlter() {
	}
}
