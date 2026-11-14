/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.util.*;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationColumnType;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.schemas.properties.object.ReferenceColumnsProperty;
import com.sqlapp.data.schemas.properties.IncludeColumnsProperty;

/**
 * Projects selected table definitions without changing the canonical source.
 */
final class TransferNameMappingProjection {
	record Projection(Schema schema, Map<String, Table> bySourceName, boolean renamedIdentifiers,
			Map<String, String> sourceKeys, Map<String, Map<String, String>> conversions,
			Map<String, Map<String, String>> copyTypes) {
		/** Resolved MVEL conversions by canonical source column name. */
		Map<String, String> conversions(String sourceTable) {
			return conversions.getOrDefault(sourceTable, Map.of());
		}

		/**
		 * Canonical target types of non-key columns by canonical source column name.
		 * Key columns keep their source type while copying; their mapped type changes
		 * only the target definition.
		 */
		Map<String, String> copyTypes(String sourceTable) {
			return copyTypes.getOrDefault(sourceTable, Map.of());
		}
	}

	private TransferNameMappingProjection() {
	}

	/**
	 * Shared identity/alias validation for projection and offline scope authoring.
	 */
	static Map<String, Table> mappingSources(Schema source, TransferNameMappingIO.Configuration mapping) {
		var resolved = new LinkedHashMap<String, Table>();
		var identities = new HashSet<String>();
		for (var name : mapping.tables().keySet()) {
			final Table table;
			try {
				table = TransferSchemaOnlyPreparation.select(source, List.of(name)).getFirst();
			} catch (IllegalArgumentException failure) {
				throw new TransferSchemaPreparation.ValidationException(
						"Mapping source table must resolve uniquely in the captured Schema: " + name);
			}
			if (!identities.add(table.getName()))
				throw new TransferSchemaPreparation.ValidationException(
						"Duplicate resolved source table mapping: " + table.getName());
			resolved.put(name, table);
		}
		return Collections.unmodifiableMap(resolved);
	}

	static Projection project(Schema source, List<Table> selected, TransferNameMappingIO.Configuration mapping) {
		// Runtime full-scan tables carry a lazy row handler. Resolve their identity
		// back to the canonical metadata rather than comparing or cloning live rows.
		var canonicalSelection = selected.stream()
				.map(input -> source.getTables().stream()
						.filter(table -> Objects.equals(table.getName(), input.getName())
								&& Objects.equals(table.getSchemaName(), input.getSchemaName())
								&& Objects.equals(table.getCatalogName(), input.getCatalogName()))
						.findFirst().orElseThrow(() -> new TransferSchemaPreparation.ValidationException(
								"Selected source table is outside the captured Schema: " + input.getName())))
				.toList();
		var rules = new LinkedHashMap<String, TransferNameMappingIO.TableMapping>();
		var sources = mappingSources(source, mapping);
		var sourceKeys = new LinkedHashMap<String, String>();
		sources.forEach((key, table) -> sourceKeys.put(table.getName(), key));
		for (var entry : mapping.tables().entrySet()) {
			final Table table = sources.get(entry.getKey());
			if (!canonicalSelection.contains(table))
				throw new TransferSchemaPreparation.ValidationException(
						"Mapping table is outside the selected scope: " + entry.getKey());
			rules.put(table.getName(), entry.getValue());
		}
		var projected = source.clone();
		projected.getTables().clear();
		var targets = new LinkedHashMap<String, Table>();
		var columnNames = new LinkedHashMap<String, Map<String, String>>();
		var targetNames = new HashSet<String>();
		var conversions = new LinkedHashMap<String, Map<String, String>>();
		var copyTypes = new LinkedHashMap<String, Map<String, String>>();
		boolean renamed = false;
		for (var table : canonicalSelection) {
			var rule = rules.getOrDefault(table.getName(), new TransferNameMappingIO.TableMapping(null, null));
			String targetName = rule.targetTable() == null ? table.getName() : rule.targetTable();
			renamed |= !targetName.equals(table.getName());
			if (!targetNames.add(targetName.toUpperCase(Locale.ROOT)))
				throw new TransferSchemaPreparation.ValidationException("Target table mappings collide: " + targetName);
			var names = new LinkedHashMap<String, String>();
			table.getColumns().forEach(column -> names.put(column.getName(), column.getName()));
			var configuredColumns = new HashSet<String>();
			var types = new HashMap<String, BulkMigrationColumnType>();
			var converted = new LinkedHashMap<String, String>();
			var copied = new LinkedHashMap<String, String>();
			var keys = new HashSet<String>();
			if (table.getPrimaryKeyConstraint() != null)
				table.getPrimaryKeyConstraint().getColumns().forEach(key -> keys.add(key.getName()));
			for (var entry : rule.columns().entrySet()) {
				var column = table.getColumns().get(entry.getKey());
				if (column == null || !configuredColumns.add(column.getName()))
					throw new TransferSchemaPreparation.ValidationException(
							"Source column is missing or mapped twice: " + table.getName() + "." + entry.getKey());
				var definition = entry.getValue();
				if (definition.name() != null)
					names.put(column.getName(), definition.name());
				if (definition.type() != null) {
					var type = BulkMigrationColumnType.parse(definition.type());
					types.put(column.getName(), type);
					if (!keys.contains(column.getName()))
						copied.put(column.getName(), type.toString());
				}
				if (definition.conversion() != null)
					converted.put(column.getName(), definition.conversion());
			}
			if (!converted.isEmpty())
				conversions.put(table.getName(), Collections.unmodifiableMap(converted));
			if (!copied.isEmpty())
				copyTypes.put(table.getName(), Collections.unmodifiableMap(copied));
			var unique = new HashSet<String>();
			for (var name : names.values())
				if (!unique.add(name.toUpperCase(Locale.ROOT)))
					throw new TransferSchemaPreparation.ValidationException(
							"Target column mappings collide in table " + targetName + ": " + name);
			var target = table.clone().setName(targetName);
			target.getColumns().clear();
			for (var column : table.getColumns()) {
				String name = names.get(column.getName());
				renamed |= !column.getName().equals(name);
				var output = column.clone().setName(name);
				if (types.containsKey(column.getName()))
					types.get(column.getName()).applyTo(output);
				target.getColumns().add(output);
			}
			projected.getTables().add(target);
			targets.put(table.getName(), target);
			columnNames.put(table.getName(), names);
		}
		for (var table : canonicalSelection) {
			var target = targets.get(table.getName());
			var names = columnNames.get(table.getName());
			for (int i = 0; i < table.getConstraints().size(); i++) {
				var original = table.getConstraints().get(i);
				var copied = target.getConstraints().get(i);
				if (original instanceof AbstractColumnConstraint<?> columns
						&& copied instanceof AbstractColumnConstraint<?> output) {
					var resolved = columns.getColumns().stream().map(column -> column(target, names, column.getName()))
							.toList();
					output.getColumns().clear();
					output.getColumns().addAll(resolved);
				}
				if (original instanceof ReferenceColumnsProperty<?> refs
						&& copied instanceof ReferenceColumnsProperty<?> output)
					references(refs.getColumns(), output.getColumns(), target, names);
				if (original instanceof IncludeColumnsProperty<?> refs
						&& copied instanceof IncludeColumnsProperty<?> output)
					references(refs.getIncludes(), output.getIncludes(), target, names);
				if (original instanceof ForeignKeyConstraint fk && copied instanceof ForeignKeyConstraint output) {
					if (!Objects.equals(fk.getRelatedTableSchemaName(), source.getName())
							&& fk.getRelatedTableSchemaName() != null && !fk.getRelatedTableSchemaName().isBlank())
						throw new TransferSchemaPreparation.ValidationException(
								"Select referenced tables from the same source schema before applying mappings.");
					var parent = targets.get(fk.getRelatedTableName());
					if (parent == null)
						throw new TransferSchemaPreparation.ValidationException(
								"Include referenced source table before applying mappings: "
										+ fk.getRelatedTableName());
					output.setRelatedTableName(parent.getName()).setRelatedTableSchemaName(source.getName());
					references(fk.getRelatedColumns(), output.getRelatedColumns(), parent,
							columnNames.get(fk.getRelatedTableName()));
				}
			}
			for (int i = 0; i < table.getIndexes().size(); i++) {
				var index = table.getIndexes().get(i);
				var copy = target.getIndexes().get(i);
				references(index.getColumns(), copy.getColumns(), target, names);
				references(index.getIncludes(), copy.getIncludes(), target, names);
			}
		}
		SchemaUtils.validate(projected);
		return new Projection(projected, Collections.unmodifiableMap(targets), renamed,
				Collections.unmodifiableMap(sourceKeys), Collections.unmodifiableMap(conversions),
				Collections.unmodifiableMap(copyTypes));
	}

	private static Column column(Table table, Map<String, String> names, String sourceName) {
		var name = names.get(sourceName);
		var column = name == null ? null : table.getColumns().get(name);
		if (column == null)
			throw new TransferSchemaPreparation.ValidationException("Mapped column cannot be resolved: " + sourceName);
		return column;
	}

	private static void references(ReferenceColumnCollection source, ReferenceColumnCollection target, Table table,
			Map<String, String> names) {
		var refs = source.stream()
				.map(ref -> new ReferenceColumn(column(table, names, ref.getName())).setOrder(ref.getOrder())
						.setLength(ref.getLength()).setNullsOrder(ref.getNullsOrder())
						.setIncludedColumn(ref.isIncludedColumn()).setWith(ref.getWith()))
				.toList();
		target.clear();
		target.addAll(refs);
	}
}
