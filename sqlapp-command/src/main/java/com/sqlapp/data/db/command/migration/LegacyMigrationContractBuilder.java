/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.sqlapp.data.schemas.migration.LegacyMigrationContract;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.AncestorKey;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.DataSet;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.Field;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.KeyColumn;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.IndexedSource;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ColumnAction;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ColumnMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.RelationshipMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.TableMapping;
import com.sqlapp.exceptions.CommandException;

/**
 * Builds the extraction and load contract from the composed lineage mapping.
 */
public class LegacyMigrationContractBuilder {

	public LegacyMigrationContract build(File mappingFile, LegacyMigrationMapping mapping) {
		new LegacyMigrationMappingValidator().validate(mapping);
		LegacyMigrationContract contract = new LegacyMigrationContract();
		contract.setMigrationId(mapping.getMigration().getId());
		contract.setMappingFile(mappingFile.getPath());
		contract.setMappingFingerprint(new LegacyMigrationMappingValidator().fingerprint(mappingFile));

		Map<String, TableMapping> tables = new LinkedHashMap<>();
		mapping.getTables().forEach(table -> tables.put(table.getId(), table));
		Map<String, RelationshipMapping> parentRelationships = parentRelationships(mapping);
		for (TableMapping table : mapping.getTables()) {
			if (table.getOperation() == LegacyMigrationMapping.TableOperation.SKIP) {
				continue;
			}
			contract.getDataSets().add(dataSet(table, tables, parentRelationships));
		}
		contract.getDataSets().sort(Comparator.comparingInt(DataSet::getLoadOrder)
				.thenComparing(DataSet::getId));
		return contract;
	}

	private DataSet dataSet(TableMapping table, Map<String, TableMapping> tables,
			Map<String, RelationshipMapping> parentRelationships) {
		DataSet dataSet = new DataSet();
		dataSet.setId(table.getId());
		dataSet.setSourcePath(table.getSource().getPath());
		dataSet.setTargetCatalog(table.getTarget().getCatalog());
		dataSet.setTargetSchema(table.getTarget().getSchema());
		dataSet.setTargetTable(table.getTarget().getTable());
		dataSet.setFileName(fileName(table) + ".csv");
		dataSet.setStagingTable("TMP_" + table.getTarget().getTable());
		dataSet.setTargetPrimaryKey(new ArrayList<>(table.getKeys().getTargetPrimaryKey()));
		dataSet.setSourceBusinessKey(sourceBusinessKey(table));
		Object occurrence = table.getDetails().get("occurrence");
		if (occurrence instanceof Map<?, ?> values) {
			dataSet.setOccurrenceColumn(string(values.get("column")));
			Object maximum = values.get("maximum");
			if (maximum instanceof Number number) {
				dataSet.setMaximumOccurrences(number.intValue());
			}
		}
		RelationshipMapping directParent = parentRelationships.get(table.getId());
		if (directParent != null) {
			dataSet.setParentDataSetId(directParent.getParentMappingId());
			dataSet.setHierarchyDepth(directParent.getDepth());
			dataSet.setLoadOrder(directParent.getLoadOrder() > 0
					? directParent.getLoadOrder() : directParent.getDepth());
		}
		for (ColumnMapping column : table.getColumns()) {
			Field field = field(column, dataSet.getFields().size() + 1);
			if (!field.isExtracted() && !field.isGenerated()) {
				continue;
			}
			dataSet.getFields().add(field);
		}
		if (dataSet.getFields().stream()
				.anyMatch(field -> !field.getIndexedSources().isEmpty())) {
			dataSet.setOccurrenceSourceMode("NUMBERED_COLUMNS");
		}
		addAncestorKeys(dataSet, table, tables, parentRelationships);
		return dataSet;
	}

	private Field field(ColumnMapping column, int position) {
		Field field = new Field();
		field.setPosition(position);
		field.setSourcePath(column.getSourcePath());
		field.setSourceColumn(column.getSource());
		field.setStagingColumn(column.getSource() == null ? column.getTarget() : column.getSource());
		field.setTargetColumn(column.getTarget());
		field.setAction(column.getAction().name());
		field.setGenerated(column.getAction() == ColumnAction.GENERATE
				|| column.getAction() == ColumnAction.CONSTANT);
		field.setOccurrenceIndex("OCCURRENCE_NUMBER".equals(column.getConversion().get("type")));
		field.setExtracted(field.isOccurrenceIndex() || (column.getSourcePath() != null
				&& !field.isGenerated()));
		column.getSourceColumns().forEach(source -> {
			IndexedSource indexed = new IndexedSource();
			indexed.setIndex(source.getIndex());
			indexed.setSourceColumn(source.getColumn());
			indexed.setSourcePath(tableSourcePath(column, source.getColumn()));
			field.getIndexedSources().add(indexed);
		});
		if (!field.getIndexedSources().isEmpty()) {
			field.setExtracted(true);
		}
		field.setRemarks(string(column.getConversion().get("remarks")));
		if (column.getTargetDefinition() != null) {
			field.setTargetDataType(column.getTargetDefinition().getDataType());
			field.setLength(column.getTargetDefinition().getLength());
			field.setScale(column.getTargetDefinition().getScale());
			field.setNullable(column.getTargetDefinition().getNullable());
		}
		return field;
	}

	private String tableSourcePath(ColumnMapping column, String sourceColumn) {
		if (column.getSourcePath() != null) {
			int index = column.getSourcePath().lastIndexOf('.');
			return (index < 0 ? "" : column.getSourcePath().substring(0, index + 1))
					+ sourceColumn;
		}
		return sourceColumn;
	}

	private void addAncestorKeys(DataSet dataSet, TableMapping table, Map<String, TableMapping> tables,
			Map<String, RelationshipMapping> parentRelationships) {
		String currentId = table.getId();
		int ancestorDepth = 1;
		Set<String> visited = new HashSet<>();
		while (parentRelationships.containsKey(currentId)) {
			if (!visited.add(currentId)) {
				throw new CommandException("Cyclic hierarchical relationship at data set: " + currentId);
			}
			RelationshipMapping relationship = parentRelationships.get(currentId);
			TableMapping ancestor = tables.get(relationship.getParentMappingId());
			if (ancestor == null) {
				throw new CommandException("Unknown ancestor table mapping: " + relationship.getParentMappingId());
			}
			AncestorKey key = new AncestorKey();
			key.setAncestorDataSetId(ancestor.getId());
			key.setAncestorTable(ancestor.getTarget().getTable());
			key.setDepth(ancestorDepth++);
			relationship.getSourceKeys().forEach(pair -> {
				String targetColumn = relationship.getTargetKeys().stream()
						.filter(target -> equals(target.getParentColumn(), pair.getParentColumn()))
						.map(target -> target.getChildColumn()).findFirst().orElse(pair.getChildColumn());
				key.getColumns().add(new KeyColumn(pair.getParentColumn(), pair.getChildColumn(), targetColumn));
				addExtractedAncestorField(dataSet, table, ancestor, pair, targetColumn);
			});
			dataSet.getAncestorKeys().add(key);
			currentId = ancestor.getId();
		}
	}

	private void addExtractedAncestorField(DataSet dataSet, TableMapping table,
			TableMapping ancestor, LegacyMigrationMapping.ColumnPair pair,
			String targetColumn) {
		if (dataSet.getFields().stream().anyMatch(field ->
				equals(field.getStagingColumn(), pair.getChildColumn()))) {
			return;
		}
		ColumnMapping ancestorColumn = ancestor.getColumns().stream()
				.filter(column -> equals(column.getSource(), pair.getParentColumn())
						|| equals(column.getTarget(), pair.getParentColumn()))
				.findFirst().orElse(null);
		Field field = new Field();
		field.setPosition(dataSet.getFields().size() + 1);
		field.setSourceColumn(pair.getChildColumn());
		field.setStagingColumn(pair.getChildColumn());
		field.setSourcePath(ancestorColumn != null && ancestorColumn.getSourcePath() != null
				? ancestorColumn.getSourcePath()
				: ancestor.getSource().getPath() + "." + pair.getParentColumn());
		ColumnMapping target = table.getColumns().stream()
				.filter(column -> equals(column.getSource(), pair.getChildColumn())
						&& equals(column.getTarget(), targetColumn))
				.findFirst().orElse(null);
		field.setTargetColumn(target == null ? null : targetColumn);
		field.setAction(target == null ? ColumnAction.DROP.name() : target.getAction().name());
		field.setExtracted(true);
		if (ancestorColumn != null && ancestorColumn.getTargetDefinition() != null) {
			field.setTargetDataType(ancestorColumn.getTargetDefinition().getDataType());
			field.setLength(ancestorColumn.getTargetDefinition().getLength());
			field.setScale(ancestorColumn.getTargetDefinition().getScale());
			field.setNullable(ancestorColumn.getTargetDefinition().getNullable());
		}
		dataSet.getFields().add(field);
	}

	private Map<String, RelationshipMapping> parentRelationships(LegacyMigrationMapping mapping) {
		Map<String, RelationshipMapping> result = new LinkedHashMap<>();
		for (RelationshipMapping relationship : mapping.getRelationships()) {
			if (relationship.getType() != LegacyMigrationMapping.RelationshipType.HIERARCHICAL) {
				continue;
			}
			RelationshipMapping duplicate = result.put(relationship.getChildMappingId(), relationship);
			if (duplicate != null) {
				throw new CommandException("Multiple hierarchical parents are not supported for data set: "
						+ relationship.getChildMappingId());
			}
		}
		return result;
	}

	private List<String> sourceBusinessKey(TableMapping table) {
		if (!table.getKeys().getSourcePrimaryKey().isEmpty()) {
			return new ArrayList<>(table.getKeys().getSourcePrimaryKey());
		}
		if (!table.getKeys().getBusinessKey().isEmpty()) {
			return new ArrayList<>(table.getKeys().getBusinessKey());
		}
		return table.getColumns().stream().filter(column -> column.getSource() != null)
				.map(ColumnMapping::getSource).distinct().toList();
	}

	private String fileName(TableMapping table) {
		String name = table.getTarget().getTable() == null ? table.getId() : table.getTarget().getTable();
		return name.toLowerCase(Locale.ROOT);
	}

	private boolean equals(String left, String right) {
		return left == null ? right == null : left.equalsIgnoreCase(right);
	}

	private String string(Object value) {
		return value == null ? null : String.valueOf(value);
	}
}
