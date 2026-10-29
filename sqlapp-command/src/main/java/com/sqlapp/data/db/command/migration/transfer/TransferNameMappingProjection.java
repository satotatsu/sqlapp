/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.util.*;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.schemas.properties.object.ReferenceColumnsProperty;
import com.sqlapp.data.schemas.properties.IncludeColumnsProperty;

/**
 * Projects selected table definitions without changing the canonical source.
 */
final class TransferNameMappingProjection {
	record Projection(Schema schema, Map<String, Table> bySourceName, boolean renamedIdentifiers) {
	}

	private TransferNameMappingProjection() {
	}

	static Projection project(Schema source, List<Table> selected, TransferNameMappingIO.Configuration mapping) {
		var rules = new LinkedHashMap<String, TransferNameMappingIO.TableMapping>();
		for (var entry : mapping.tables().entrySet()) {
			final Table table;
			try {
				table = TransferSchemaOnlyPreparation.select(source, List.of(entry.getKey())).getFirst();
			} catch (IllegalArgumentException failure) {
				throw new TransferSchemaPreparation.ValidationException(
						"Mapping source table must resolve uniquely in the captured Schema: " + entry.getKey());
			}
			if (!selected.contains(table))
				throw new TransferSchemaPreparation.ValidationException(
						"Mapping table is outside the selected scope: " + entry.getKey());
			if (rules.putIfAbsent(table.getName(), entry.getValue()) != null)
				throw new TransferSchemaPreparation.ValidationException(
						"Duplicate resolved source table mapping: " + table.getName());
		}
		var projected = source.clone();
		projected.getTables().clear();
		var targets = new LinkedHashMap<String, Table>();
		var columnNames = new LinkedHashMap<String, Map<String, String>>();
		var targetNames = new HashSet<String>();
		boolean renamed = false;
		for (var table : selected) {
			var rule = rules.getOrDefault(table.getName(), new TransferNameMappingIO.TableMapping(null, Map.of()));
			String targetName = rule.targetTable() == null ? table.getName() : rule.targetTable();
			renamed |= !targetName.equals(table.getName());
			if (!targetNames.add(targetName.toUpperCase(Locale.ROOT)))
				throw new TransferSchemaPreparation.ValidationException("Target table mappings collide: " + targetName);
			var names = new LinkedHashMap<String, String>();
			table.getColumns().forEach(column -> names.put(column.getName(), column.getName()));
			var configuredColumns = new HashSet<String>();
			for (var entry : rule.columns().entrySet()) {
				var column = table.getColumns().get(entry.getKey());
				if (column == null || !configuredColumns.add(column.getName()))
					throw new TransferSchemaPreparation.ValidationException(
							"Source column is missing or mapped twice: " + table.getName() + "." + entry.getKey());
				names.put(column.getName(), entry.getValue());
			}
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
				target.getColumns().add(column.clone().setName(name));
			}
			projected.getTables().add(target);
			targets.put(table.getName(), target);
			columnNames.put(table.getName(), names);
		}
		for (var table : selected) {
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
		return new Projection(projected, Collections.unmodifiableMap(targets), renamed);
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
