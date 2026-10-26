/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.firebird.FirebirdSchemaDependencies;
import com.sqlapp.data.db.dialect.firebird.FirebirdSchemaDependencies.*;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.*;

/**
 * Stable object-level ordering, composing existing factories without rewriting
 * their DDL.
 */
final class FirebirdSchemaCreateOrder {
	static List<SqlOperation> order(Schema schema, List<SqlOperation> operations) {
		var dependencies = dependencies(schema);
		var result = new ArrayList<SqlOperation>();
		var groups = new LinkedHashMap<ObjectId, List<SqlOperation>>();
		for (var operation : operations) {
			ObjectId key = identity(operation.getOriginal());
			if (key == null)
				result.add(operation);
			else
				groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(operation);
		}
		var required = new LinkedHashMap<ObjectId, Set<ObjectId>>();
		groups.keySet().forEach(key -> required.put(key, new LinkedHashSet<>()));
		for (var edge : dependencies)
			if (!edge.dependent().equals(edge.prerequisite()) && groups.containsKey(edge.dependent())
					&& groups.containsKey(edge.prerequisite()))
				required.get(edge.dependent()).add(edge.prerequisite());
		result.addAll(sorted(groups, required, "CREATE"));
		return result;
	}

	static List<Dependency> dependencies(Schema schema) {
		var dependencies = new ArrayList<>(FirebirdSchemaDependencies.get(schema));
		for (Table table : schema.getTables())
			for (var fk : table.getConstraints().getForeignKeyConstraints())
				dependencies.add(new Dependency(new ObjectId(Kind.TABLE, table.getName()),
						new ObjectId(Kind.TABLE, fk.getRelatedTableName())));
		for (var body : schema.getPackageBodies())
			dependencies.add(new Dependency(new ObjectId(Kind.PACKAGE_BODY, body.getName()),
					new ObjectId(Kind.PACKAGE, body.getName())));
		return dependencies;
	}

	static List<SqlOperation> sorted(Map<ObjectId, List<SqlOperation>> groups, Map<ObjectId, Set<ObjectId>> required,
			String scope) {
		var result = new ArrayList<SqlOperation>();
		while (!required.isEmpty()) {
			ObjectId ready = required.entrySet().stream().filter(entry -> entry.getValue().isEmpty())
					.map(Map.Entry::getKey).findFirst()
					.orElseThrow(() -> new UnsupportedOperationException("Firebird schema " + scope
							+ " has cyclic dependencies; supply an explicit staged migration: " + required));
			result.addAll(groups.get(ready));
			required.remove(ready);
			required.values().forEach(set -> set.remove(ready));
		}
		return result;
	}

	static ObjectId identity(DbCommonObject<?> object) {
		ObjectId key = direct(object);
		if (key == null && object != null) {
			DbCommonObject<?> owner = object.getAncestor(candidate -> direct(candidate) != null);
			key = direct(owner);
		}
		return key;
	}

	static Map<ObjectId, AbstractSchemaObject<?>> objects(Schema schema) {
		var result = new LinkedHashMap<ObjectId, AbstractSchemaObject<?>>();
		Set<AbstractSchemaObject<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Object value : schema.toMap().values())
			if (value instanceof AbstractSchemaObjectCollection<?> collection)
				for (var child : collection)
					collect(child, result, visited);
		return result;
	}

	private static void collect(AbstractSchemaObject<?> object, Map<ObjectId, AbstractSchemaObject<?>> result,
			Set<AbstractSchemaObject<?>> visited) {
		if (!visited.add(object))
			return;
		ObjectId key = direct(object);
		if (key != null)
			result.put(key, object);
		for (Object value : object.toMap().values())
			if (value instanceof AbstractSchemaObjectCollection<?> collection)
				for (var child : collection)
					collect(child, result, visited);
	}

	private static ObjectId direct(DbCommonObject<?> object) {
		Kind kind;
		if (object instanceof View)
			kind = Kind.VIEW;
		else if (object instanceof Table)
			kind = Kind.TABLE;
		else if (object instanceof Procedure)
			kind = Kind.PROCEDURE;
		else if (object instanceof Function)
			kind = Kind.FUNCTION;
		else if (object instanceof Domain)
			kind = Kind.DOMAIN;
		else if (object instanceof Sequence)
			kind = Kind.SEQUENCE;
		else if (object instanceof Trigger)
			kind = Kind.TRIGGER;
		else if (object instanceof com.sqlapp.data.schemas.Package)
			kind = Kind.PACKAGE;
		else if (object instanceof PackageBody)
			kind = Kind.PACKAGE_BODY;
		else
			return null;
		return new ObjectId(kind, ((AbstractSchemaObject<?>) object).getName());
	}

	private FirebirdSchemaCreateOrder() {
	}
}
