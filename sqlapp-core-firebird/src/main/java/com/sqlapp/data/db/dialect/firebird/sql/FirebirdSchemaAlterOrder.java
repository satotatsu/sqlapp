/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.firebird.FirebirdSchemaDependencies;
import com.sqlapp.data.db.dialect.firebird.FirebirdSchemaDependencies.*;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/**
 * Orders existing factory operations without reconstructing or parsing SQL
 * bodies.
 */
final class FirebirdSchemaAlterOrder {
	static List<SqlOperation> order(Schema original, Schema target, List<SqlOperation> operations) {
		var before = FirebirdSchemaCreateOrder.objects(original);
		var after = FirebirdSchemaCreateOrder.objects(target);
		var oldEdges = FirebirdSchemaCreateOrder.dependencies(original);
		var compiledEdges = new HashSet<>(FirebirdSchemaDependencies.get(original));
		var newEdges = new HashSet<>(FirebirdSchemaCreateOrder.dependencies(target));
		var groups = new LinkedHashMap<ObjectId, List<SqlOperation>>();
		var result = new ArrayList<SqlOperation>();
		for (var operation : operations) {
			ObjectId key = FirebirdSchemaCreateOrder.identity(operation.getOriginal());
			if (key == null)
				key = FirebirdSchemaCreateOrder.identity(operation.getTarget());
			if (key == null)
				result.add(operation);
			else
				groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(operation);
		}
		var removed = new HashSet<>(before.keySet());
		removed.removeAll(after.keySet());
		var risky = new HashSet<ObjectId>();
		for (var key : groups.keySet())
			if (before.containsKey(key) && after.containsKey(key) && changesShape(before.get(key), after.get(key)))
				risky.add(key);
		if (original.getSpecifics().get(FirebirdSchemaDependencies.SPECIFICS_KEY) == null)
			for (var dependent : before.entrySet())
				if (hasBody(dependent.getValue()))
					for (var provider : groups.keySet())
						if ((removed.contains(provider) || risky.contains(provider))
								&& canReference(dependent.getKey(), provider)
								&& !oldEdges.contains(new Dependency(dependent.getKey(), provider)))
							throw unsafe(new Dependency(dependent.getKey(), provider),
									"original body dependencies are unknown; read or record the original graph");
		if (target.getSpecifics().get(FirebirdSchemaDependencies.SPECIFICS_KEY) == null)
			for (var dependent : after.entrySet())
				if (groups.containsKey(dependent.getKey()) && hasBody(dependent.getValue())
						&& structural(groups.get(dependent.getKey())))
					for (var provider : groups.keySet())
						if (structural(groups.get(provider))
								&& (removed.contains(provider) || !before.containsKey(provider)
										|| risky.contains(provider)
										|| addsMembers(before.get(provider), after.get(provider)))
								&& canReference(dependent.getKey(), provider)
								&& !newEdges.contains(new Dependency(dependent.getKey(), provider)))
							throw unsafe(new Dependency(dependent.getKey(), provider),
									"target body dependencies are unknown; read or record the target graph");
		var required = new LinkedHashMap<ObjectId, Set<ObjectId>>();
		groups.keySet().forEach(key -> required.put(key, new LinkedHashSet<>()));
		for (var edge : newEdges) {
			if (edge.dependent().equals(edge.prerequisite()) || !after.containsKey(edge.dependent()))
				continue;
			if (removed.contains(edge.prerequisite()))
				throw unsafe(edge, "target still references a removed object");
			if (groups.containsKey(edge.dependent()) && groups.containsKey(edge.prerequisite()))
				required.get(edge.dependent()).add(edge.prerequisite());
		}
		for (var edge : oldEdges) {
			ObjectId provider = edge.prerequisite(), dependent = edge.dependent();
			if (provider.equals(dependent) || !groups.containsKey(provider)
					|| !removed.contains(provider) && !risky.contains(provider))
				continue;
			if (!removed.contains(dependent)) {
				if (!after.containsKey(dependent))
					throw unsafe(edge, "dependent outside the target plan remains installed");
				if (newEdges.contains(edge))
					throw unsafe(edge,
							"retained dependency cannot be proved compatible with this shape change/removal");
				if (compiledEdges.contains(edge)
						&& target.getSpecifics().get(FirebirdSchemaDependencies.SPECIFICS_KEY) == null)
					throw unsafe(edge, "record the target dependency graph, including an explicitly empty graph");
				if (!groups.containsKey(dependent)
						|| groups.get(dependent).stream().allMatch(op -> op.getSqlType() == SqlType.SET_COMMENT))
					throw unsafe(edge, "no structural operation releases the old dependency");
			}
			if (!groups.containsKey(dependent))
				throw unsafe(edge, "dependent removal is missing from the plan");
			required.get(provider).add(dependent);
		}
		result.addAll(FirebirdSchemaCreateOrder.sorted(groups, required, "ALTER"));
		return result;
	}

	private static boolean canReference(ObjectId dependent, ObjectId provider) {
		return !dependent.equals(provider) && provider.kind() != Kind.TRIGGER && provider.kind() != Kind.PACKAGE_BODY;
	}

	private static boolean structural(List<SqlOperation> operations) {
		return operations.stream().anyMatch(operation -> operation.getSqlType() != SqlType.SET_COMMENT);
	}

	private static boolean addsMembers(AbstractSchemaObject<?> before, AbstractSchemaObject<?> after) {
		if (!(before instanceof Table old) || !(after instanceof Table next))
			return false;
		return next.getColumns().stream().anyMatch(
				column -> old.getColumns().stream().noneMatch(previous -> previous.getName().equals(column.getName())))
				|| next.getIndexes().stream().anyMatch(index -> old.getIndexes().stream()
						.noneMatch(previous -> previous.getName().equals(index.getName())));
	}

	private static boolean hasBody(AbstractSchemaObject<?> object) {
		if (object instanceof View || object instanceof ArgumentRoutine<?> || object instanceof Trigger
				|| object instanceof com.sqlapp.data.schemas.Package || object instanceof PackageBody)
			return true;
		if (object instanceof Domain domain)
			return domain.getCheck() != null || domain.getDefaultValue() != null;
		if (object instanceof Table table)
			return table.getColumns().stream()
					.anyMatch(column -> column.getFormula() != null || column.getDefaultValue() != null
							|| column.getCheckConstraint() != null)
					|| table.getConstraints().stream().anyMatch(constraint -> constraint instanceof CheckConstraint)
					|| table.getIndexes().stream()
							.anyMatch(index -> index.getIndexType() == IndexType.Function || index.getWhere() != null);
		return false;
	}

	private static UnsupportedOperationException unsafe(Dependency edge, String reason) {
		return new UnsupportedOperationException(
				"Firebird schema ALTER requires an explicit staged migration: " + reason + ": " + edge);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static boolean changesShape(AbstractSchemaObject<?> before, AbstractSchemaObject<?> after) {
		var changes = ((AbstractSchemaObject) before).diff((AbstractSchemaObject) after).getChangedProperties();
		if (before instanceof Table && !(before instanceof View)) {
			if (changes.get("columns") instanceof DbObjectDifferenceCollection columns)
				for (var column : columns.getList())
					if (column.getOriginal() != null && (column.getTarget() == null || column.getChangedProperties()
							.keySet().stream()
							.anyMatch(key -> Set
									.of("name", "dataType", "dataTypeName", "length", "scale", "formula", "domainName")
									.contains(key))))
						return true;
			if (changes.get("indexes") instanceof DbObjectDifferenceCollection indexes)
				for (var index : indexes.getList())
					if (index.getOriginal() != null && (index.getTarget() == null
							|| index.getChangedProperties().keySet().stream().anyMatch(key -> !key.equals("remarks"))))
						return true;
			if (changes.get("constraints") instanceof DbObjectDifferenceCollection constraints)
				for (var constraint : constraints.getList())
					if (constraint.getOriginal() instanceof UniqueConstraint && constraint.getState().isChanged())
						return true;
		}
		if (before instanceof View && changes.get("columns") instanceof DbObjectDifferenceCollection columns)
			for (var column : columns.getList())
				if (column.getOriginal() == null || column.getTarget() == null
						|| column.getChangedProperties().keySet().stream().anyMatch(key -> !key.equals("remarks")))
					return true;
		if (before instanceof ArgumentRoutine<?>) {
			if (changes.get("arguments") instanceof DbObjectDifferenceCollection arguments)
				for (var argument : arguments.getList())
					if (argument.getOriginal() == null || argument.getTarget() == null || argument
							.getChangedProperties().keySet().stream().anyMatch(key -> !key.equals("remarks")))
						return true;
			if (changes.get("returning") instanceof DbObjectDifference returning
					&& !returning.getChangedProperties().isEmpty())
				return true;
		}
		if (before instanceof Domain && changes.keySet().stream().anyMatch(key -> Set
				.of("dataType", "dataTypeName", "length", "scale", "characterSet", "collation", "arrayDimension")
				.contains(key)))
			return true;
		return before instanceof com.sqlapp.data.schemas.Package
				&& (changes.containsKey("statement") || changes.containsKey("definition"));
	}

	private FirebirdSchemaAlterOrder() {
	}
}
