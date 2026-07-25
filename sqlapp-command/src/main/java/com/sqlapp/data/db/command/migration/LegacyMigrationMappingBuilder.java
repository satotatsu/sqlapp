/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ColumnAction;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ColumnDefinition;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ColumnMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ColumnPair;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.Diagnostic;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.DiagnosticSeverity;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.GeneratedKey;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.IndexedSourceColumn;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ParentMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.RelationshipMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.TableMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.TableOperation;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.TableReference;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.TableRole;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.TransformationRecord;

/**
 * Converts existing normalization command results into the common migration
 * lineage model.
 */
public class LegacyMigrationMappingBuilder {

	public LegacyMigrationMapping build(File sourceFile, File targetFile, Map<String, Object> normalizationLog) {
		LegacyMigrationMapping mapping = createBaseMapping(sourceFile, targetFile);
		DbCommonObject<?> sourceRoot = read(sourceFile);
		DbCommonObject<?> targetRoot = read(targetFile);
		Map<String, TableMapping> mappingsByTarget = indexMappings(mapping);
		appendNormalization(mapping, normalizationLog, targetRoot, mappingsByTarget);
		appendSurrogateKeys(mapping, normalizationLog, targetRoot, mappingsByTarget);
		setStepFingerprints(mapping);
		updateStatistics(mapping, sourceRoot, targetRoot);
		new LegacyMigrationMappingValidator().validate(mapping);
		return mapping;
	}

	public LegacyMigrationMapping buildSurrogateKeyMapping(File sourceFile, File targetFile,
			Map<String, Object> surrogateLog) {
		LegacyMigrationMapping mapping = createBaseMapping(sourceFile, targetFile);
		DbCommonObject<?> sourceRoot = read(sourceFile);
		DbCommonObject<?> targetRoot = read(targetFile);
		appendSurrogateKeys(mapping, Map.of("surrogateKeyConversion", surrogateLog), targetRoot,
				indexMappings(mapping));
		setStepFingerprints(mapping);
		updateStatistics(mapping, sourceRoot, targetRoot);
		new LegacyMigrationMappingValidator().validate(mapping);
		return mapping;
	}

	public LegacyMigrationMapping buildColumnTransformMapping(File sourceFile, File targetFile,
			Map<String, Object> transformLog) {
		LegacyMigrationMapping mapping = createBaseMapping(sourceFile, targetFile);
		DbCommonObject<?> sourceRoot = read(sourceFile);
		DbCommonObject<?> targetRoot = read(targetFile);
		Map<String, TableMapping> indexed = indexMappings(mapping);
		TransformationRecord record = new TransformationRecord();
		record.setSequence(10);
		record.setCommand("ColumnRuleTransformCommand");
		record.setStatus("SUCCESS");
		mapping.getTransformations().add(record);
		List<String> transformedColumns = new ArrayList<>();
		for (Map<String, Object> match : listOfMaps(transformLog.get("matches"))) {
			Map<String, Object> source = map(match.get("source"));
			TableMapping table = indexed.get(qualifiedName(string(source.get("schema")), string(source.get("table"))));
			if (table == null) {
				continue;
			}
			String columnName = string(source.get("column"));
			ColumnMapping column = table.getColumns().stream()
					.filter(item -> columnName.equalsIgnoreCase(item.getSource())).findFirst().orElse(null);
			if (column == null) {
				continue;
			}
			column.setAction("candidate".equals(match.get("result")) ? ColumnAction.COPY : ColumnAction.CAST);
			column.getConversion().put("ruleId", match.get("ruleId"));
			column.getConversion().put("mode", match.get("mode"));
			column.getConversion().put("matchedRuleIds", match.get("matchedRuleIds"));
			column.getConversion().put("result", match.get("result"));
			Map<String, Object> target = map(match.get("target"));
			ColumnDefinition targetDefinition = new ColumnDefinition();
			targetDefinition.setDataType(string(target.get("dataType")));
			if (target.get("length") instanceof Number length) {
				targetDefinition.setLength(length.longValue());
			}
			column.setTargetDefinition(targetDefinition);
			table.setOperation(TableOperation.TRANSFORM);
			transformedColumns.add(table.getTarget().getTable() + "." + column.getTarget());
		}
		record.getChanges().put("columns", transformedColumns);
		setStepFingerprints(mapping);
		updateStatistics(mapping, sourceRoot, targetRoot);
		new LegacyMigrationMappingValidator().validate(mapping);
		return mapping;
	}

	public LegacyMigrationMapping buildPliImportMapping(File sourceFile, File targetFile, String encoding,
			String schemaName, List<Map<String, Object>> tableLogs, List<String> warnings) {
		LegacyMigrationMapping mapping = new LegacyMigrationMapping();
		String baseName = baseName(sourceFile.getName());
		mapping.getMigration().setId(baseName + "-migration");
		mapping.getMigration().setTitle(baseName + " PL/I legacy schema migration");
		mapping.getMigration().setGeneratedAt(OffsetDateTime.now().toString());
		LegacyMigrationMappingValidator validator = new LegacyMigrationMappingValidator();
		mapping.getSource().setSystem("PL/I");
		mapping.getSource().setSchemaFile(sourceFile.getName());
		mapping.getSource().setSchemaFingerprint(validator.fingerprint(sourceFile));
		LegacyMigrationMapping.DefinitionFile definitionFile = new LegacyMigrationMapping.DefinitionFile();
		definitionFile.setPath(sourceFile.getPath());
		definitionFile.setType("PLI");
		mapping.getSource().getDefinitionFiles().add(definitionFile);
		mapping.getTarget().setSchemaFile(targetFile.getName());
		mapping.getTarget().setSchemaFingerprint(validator.fingerprint(targetFile));
		DbCommonObject<?> targetRoot = read(targetFile);
		Map<String, TableMapping> byName = new LinkedHashMap<>();
		for (Map<String, Object> tableLog : tableLogs) {
			String tableName = string(tableLog.get("table"));
			Table table = findTable(targetRoot, null, schemaName, tableName);
			if (table == null) {
				throw new IllegalStateException("PL/I import mapping table was not found: " + tableName);
			}
			TableMapping tableMapping = new TableMapping();
			tableMapping.setId(uniqueId(table));
			tableMapping.setTarget(reference(table));
			tableMapping.getSource().setSchema(schemaName);
			tableMapping.getSource().setTable(string(tableLog.get("declaration")));
			tableMapping.getSource().setPath(string(tableLog.get("sourcePath")));
			tableMapping.setRole(tableLog.get("occurrence") == null ? TableRole.ROOT : TableRole.CHILD);
			tableMapping.setOperation(TableOperation.TRANSFORM);
			tableMapping.getKeys().setTargetPrimaryKey(
					table.getPrimaryKeyConstraint() == null ? List.of() : columnNames(table.getPrimaryKeyConstraint()));
			for (Map<String, Object> columnLog : listOfMaps(tableLog.get("columns"))) {
				ColumnMapping column = new ColumnMapping();
				column.setSource(string(columnLog.get("sourceName")));
				column.setSourcePath(string(columnLog.get("sourcePath")));
				column.setTarget(string(columnLog.get("name")));
				column.setAction(ColumnAction.COPY);
				ColumnDefinition sourceDefinition = new ColumnDefinition();
				sourceDefinition.setDataType(string(columnLog.get("pliType")));
				if (columnLog.get("sourceLength") instanceof Number length) {
					sourceDefinition.setLength(length.longValue());
				}
				if (columnLog.get("sourceScale") instanceof Number scale) {
					sourceDefinition.setScale(scale.intValue());
				}
				column.setSourceDefinition(sourceDefinition);
				column.setTargetDefinition(definition(table.getColumns().get(column.getTarget())));
				column.getConversion().put("language", "PLI");
				column.getConversion().put("level", columnLog.get("level"));
				column.getConversion().put("declaration", columnLog.get("declaration"));
				column.getConversion().put("remarks", columnLog.get("remarks"));
				tableMapping.getColumns().add(column);
			}
			Map<String, Object> occurrence = map(tableLog.get("occurrence"));
			if (!occurrence.isEmpty()) {
				String occurrenceColumn = string(occurrence.get("column"));
				ColumnMapping column = new ColumnMapping();
				column.setSourcePath(string(tableLog.get("sourcePath")) + ".$index");
				column.setTarget(occurrenceColumn);
				column.setAction(ColumnAction.GENERATE);
				column.setTargetDefinition(definition(table.getColumns().get(occurrenceColumn)));
				column.getConversion().put("type", "OCCURRENCE_NUMBER");
				column.getConversion().put("minimum", 1);
				column.getConversion().put("maximum", occurrence.get("maximum"));
				tableMapping.getColumns().add(column);
				tableMapping.getDetails().put("occurrence", occurrence);
			}
			mapping.getTables().add(tableMapping);
			byName.put(tableName.toLowerCase(Locale.ROOT), tableMapping);
		}
		for (Table table : SchemaUtils.toTables(targetRoot)) {
			TableMapping child = byName.get(table.getName().toLowerCase(Locale.ROOT));
			if (child == null) {
				continue;
			}
			table.getConstraints().getForeignKeyConstraints().forEach(foreignKey -> {
				TableMapping parent = byName.get(foreignKey.getRelatedTableName().toLowerCase(Locale.ROOT));
				if (parent == null) {
					return;
				}
				ParentMapping parentMapping = new ParentMapping();
				parentMapping.setMappingId(parent.getId());
				child.setParent(parentMapping);
				RelationshipMapping relationship = new RelationshipMapping();
				relationship.setId("rel-" + parent.getId() + "-" + child.getId());
				relationship.setParentMappingId(parent.getId());
				relationship.setChildMappingId(child.getId());
				relationship.setDepth(depth(parent, byName) + 1);
				for (int i = 0; i < foreignKey.getColumns().size(); i++) {
					String childColumn = foreignKey.getColumns().get(i).getName();
					String parentColumn = foreignKey.getRelatedColumns().get(i).getName();
					ColumnPair pair = new ColumnPair(parentColumn, childColumn);
					relationship.getSourceKeys().add(pair);
					relationship.getTargetKeys().add(new ColumnPair(parentColumn, childColumn));
					parentMapping.getSourceReference().add(new ColumnPair(parentColumn, childColumn));
					parentMapping.getResolvedReference().add(new ColumnPair(parentColumn, childColumn));
				}
				mapping.getRelationships().add(relationship);
			});
		}
		for (String warning : warnings) {
			Diagnostic diagnostic = new Diagnostic();
			diagnostic.setCode("PLI_IMPORT_WARNING");
			diagnostic.setSeverity(DiagnosticSeverity.WARNING);
			diagnostic.setAction("REVIEW");
			diagnostic.setMessage(warning);
			mapping.getDiagnostics().getWarnings().add(diagnostic);
		}
		TransformationRecord record = new TransformationRecord();
		record.setSequence(10);
		record.setCommand("PliSchemaImportCommand");
		record.setStatus("SUCCESS");
		record.setInputFingerprint(mapping.getSource().getSchemaFingerprint());
		record.setOutputFingerprint(mapping.getTarget().getSchemaFingerprint());
		record.getConfiguration().put("encoding", encoding);
		record.getConfiguration().put("schema", schemaName);
		record.getChanges().put("createdTables",
				mapping.getTables().stream().map(item -> item.getTarget().getTable()).toList());
		mapping.getTransformations().add(record);
		mapping.getStatistics().setSourceTableCount((int) mapping.getTables().stream()
				.filter(item -> item.getRole() == TableRole.ROOT).count());
		mapping.getStatistics().setTargetTableCount(mapping.getTables().size());
		mapping.getStatistics().setTransformedTableCount(mapping.getTables().size());
		mapping.getStatistics().setWarningCount(warnings.size());
		validator.validate(mapping);
		return mapping;
	}

	private int depth(TableMapping table, Map<String, TableMapping> byName) {
		int depth = 0;
		TableMapping current = table;
		while (current != null && current.getParent() != null) {
			depth++;
			String parentId = current.getParent().getMappingId();
			current = byName.values().stream().filter(item -> item.getId().equals(parentId)).findFirst().orElse(null);
		}
		return depth;
	}

	private void setStepFingerprints(LegacyMigrationMapping mapping) {
		if (mapping.getTransformations().isEmpty()) {
			return;
		}
		mapping.getTransformations().getFirst().setInputFingerprint(mapping.getSource().getSchemaFingerprint());
		mapping.getTransformations().getLast().setOutputFingerprint(mapping.getTarget().getSchemaFingerprint());
	}

	private LegacyMigrationMapping createBaseMapping(File sourceFile, File targetFile) {
		LegacyMigrationMapping mapping = new LegacyMigrationMapping();
		String baseName = baseName(sourceFile.getName());
		mapping.getMigration().setId(baseName + "-migration");
		mapping.getMigration().setTitle(baseName + " legacy schema migration");
		mapping.getMigration().setGeneratedAt(OffsetDateTime.now().toString());
		mapping.getSource().setSchemaFile(sourceFile.getName());
		mapping.getTarget().setSchemaFile(targetFile.getName());
		LegacyMigrationMappingValidator validator = new LegacyMigrationMappingValidator();
		mapping.getSource().setSchemaFingerprint(validator.fingerprint(sourceFile));
		mapping.getTarget().setSchemaFingerprint(validator.fingerprint(targetFile));

		DbCommonObject<?> sourceRoot = read(sourceFile);
		DbCommonObject<?> targetRoot = read(targetFile);
		for (Table sourceTable : SchemaUtils.toTables(sourceRoot)) {
			Table targetTable = findTable(targetRoot, sourceTable.getCatalogName(), sourceTable.getSchemaName(),
					sourceTable.getName());
			TableMapping tableMapping = createBaseTableMapping(sourceTable, targetTable);
			mapping.getTables().add(tableMapping);
		}
		return mapping;
	}

	private Map<String, TableMapping> indexMappings(LegacyMigrationMapping mapping) {
		Map<String, TableMapping> result = new LinkedHashMap<>();
		for (TableMapping table : mapping.getTables()) {
			result.put(qualifiedName(table.getTarget().getSchema(), table.getTarget().getTable()), table);
		}
		return result;
	}

	private void appendNormalization(LegacyMigrationMapping mapping, Map<String, Object> log,
			DbCommonObject<?> targetRoot, Map<String, TableMapping> mappingsByTarget) {
		TransformationRecord record = new TransformationRecord();
		record.setSequence(10);
		record.setCommand("FirstNormalFormCommand");
		record.setStatus("SUCCESS");
		record.getConfiguration().putAll(map(log.get("configuration")));
		mapping.getTransformations().add(record);
		for (Map<String, Object> tableLog : listOfMaps(log.get("tables"))) {
			Map<String, Object> sourceLog = map(tableLog.get("sourceTable"));
			String sourceName = string(sourceLog.get("name"));
			String sourceSchema = string(sourceLog.get("schema"));
			TableMapping parent = mappingsByTarget.get(qualifiedName(sourceSchema, sourceName));
			if ("skipped".equals(tableLog.get("result"))) {
				Diagnostic diagnostic = new Diagnostic();
				Map<String, Object> reason = map(tableLog.get("reason"));
				diagnostic.setCode(string(reason.get("code")));
				diagnostic.setSeverity(DiagnosticSeverity.WARNING);
				diagnostic.setTableMappingId(parent == null ? null : parent.getId());
				diagnostic.setAction("SKIPPED");
				diagnostic.setMessage(string(reason.get("message")));
				mapping.getDiagnostics().getSkipped().add(diagnostic);
				continue;
			}
			if (parent != null && !listOfMaps(tableLog.get("generatedTables")).isEmpty()) {
				parent.setOperation(TableOperation.SPLIT);
			}
			int childNumber = 0;
			for (Map<String, Object> generated : listOfMaps(tableLog.get("generatedTables"))) {
				childNumber++;
				String childName = string(generated.get("name"));
				Table childTable = findTable(targetRoot, null, sourceSchema, childName);
				TableMapping child = createGeneratedTableMapping(parent, childTable, generated);
				mapping.getTables().add(child);
				mappingsByTarget.put(qualifiedName(childTable), child);
				RelationshipMapping relationship = createRelationship(parent, child, generated, childNumber);
				mapping.getRelationships().add(relationship);
			}
		}
		record.getChanges().put("createdTables",
				mapping.getTables().stream().filter(table -> table.getRole() == TableRole.DETAIL)
						.map(table -> table.getTarget().getTable()).toList());
	}

	private TableMapping createGeneratedTableMapping(TableMapping parent, Table table, Map<String, Object> generated) {
		TableMapping result = new TableMapping();
		result.setId(uniqueId(table));
		result.setRole(TableRole.DETAIL);
		result.setOperation(TableOperation.SPLIT);
		result.setSource(copy(parent.getSource()));
		result.setTarget(reference(table));
		result.getSource().setPath(parent.getSource().getPath() + "." + table.getName());
		ParentMapping parentMapping = new ParentMapping();
		parentMapping.setMappingId(parent.getId());
		result.setParent(parentMapping);
		Map<String, Object> primaryKey = map(generated.get("primaryKey"));
		result.getKeys().setTargetPrimaryKey(strings(primaryKey.get("columns")));
		Map<String, Object> keyMapping = map(generated.get("keyMapping"));
		Map<String, Object> sequence = map(keyMapping.get("sequenceColumn"));
		String sequenceColumn = string(sequence.get("name"));
		if (sequenceColumn != null) {
			ColumnMapping generatedColumn = new ColumnMapping();
			generatedColumn.setTarget(sequenceColumn);
			generatedColumn.setAction(ColumnAction.GENERATE);
			generatedColumn.setTargetDefinition(definition(table.getColumns().get(sequenceColumn)));
			generatedColumn.getConversion().put("type", "OCCURRENCE_NUMBER");
			generatedColumn.getConversion().put("start", 1);
			result.getColumns().add(generatedColumn);
		}
		for (Map<String, Object> item : listOfMaps(generated.get("columnMappings"))) {
			ColumnMapping column = new ColumnMapping();
			column.setTarget(string(item.get("targetColumn")));
			column.setAction(ColumnAction.SPLIT);
			column.setTargetDefinition(definition(table.getColumns().get(column.getTarget())));
			for (Map<String, Object> source : listOfMaps(item.get("sourceColumns"))) {
				IndexedSourceColumn indexed = new IndexedSourceColumn();
				indexed.setIndex(integer(source.get("index")));
				indexed.setColumn(string(source.get("column")));
				column.getSourceColumns().add(indexed);
			}
			result.getColumns().add(column);
		}
		int maximum = result.getColumns().stream()
				.flatMap(column -> column.getSourceColumns().stream())
				.map(IndexedSourceColumn::getIndex)
				.filter(java.util.Objects::nonNull).mapToInt(Integer::intValue)
				.max().orElse(0);
		if (maximum > 0) {
			Map<String, Object> occurrence = new LinkedHashMap<>();
			occurrence.put("column", sequenceColumn);
			occurrence.put("maximum", maximum);
			result.getDetails().put("occurrence", occurrence);
		}
		return result;
	}

	private RelationshipMapping createRelationship(TableMapping parent, TableMapping child,
			Map<String, Object> generated, int number) {
		RelationshipMapping relationship = new RelationshipMapping();
		relationship.setId("rel-" + parent.getId() + "-" + child.getId());
		relationship.setDepth(1);
		relationship.setParentMappingId(parent.getId());
		relationship.setChildMappingId(child.getId());
		relationship.setLoadOrder(number * 10);
		Map<String, Object> foreignKey = map(generated.get("foreignKey"));
		List<String> parentColumns = strings(foreignKey.get("targetColumns"));
		List<String> childColumns = strings(foreignKey.get("sourceColumns"));
		for (int i = 0; i < Math.min(parentColumns.size(), childColumns.size()); i++) {
			relationship.getSourceKeys().add(new ColumnPair(parentColumns.get(i), childColumns.get(i)));
			relationship.getTargetKeys().add(new ColumnPair(parentColumns.get(i), childColumns.get(i)));
			child.getParent().getSourceReference().add(new ColumnPair(parentColumns.get(i), childColumns.get(i)));
			child.getParent().getResolvedReference().add(new ColumnPair(parentColumns.get(i), childColumns.get(i)));
		}
		return relationship;
	}

	private void appendSurrogateKeys(LegacyMigrationMapping mapping, Map<String, Object> normalizationLog,
			DbCommonObject<?> targetRoot, Map<String, TableMapping> mappingsByTarget) {
		Map<String, Object> surrogate = map(normalizationLog.get("surrogateKeyConversion"));
		if (surrogate.isEmpty()) {
			return;
		}
		TransformationRecord record = new TransformationRecord();
		record.setSequence(20);
		record.setCommand("CompositePrimaryKeyToSurrogateKeyCommand");
		record.setStatus("SUCCESS");
		record.getConfiguration().put("generationType", surrogate.get("generationType"));
		mapping.getTransformations().add(record);
		List<String> converted = new ArrayList<>();
		for (Map<String, Object> tableLog : listOfMaps(surrogate.get("tables"))) {
			String tableName = string(tableLog.get("table"));
			Table table = findTable(targetRoot, null, null, tableName);
			TableMapping tableMapping = mappingsByTarget.get(qualifiedName(table));
			if (tableMapping == null) {
				continue;
			}
			tableMapping.setOperation(tableMapping.getOperation() == TableOperation.SPLIT
					? TableOperation.SPLIT : TableOperation.TRANSFORM);
			tableMapping.getKeys().setSourcePrimaryKey(strings(tableLog.get("oldPrimaryKey")));
			Map<String, Object> newPrimaryKey = map(tableLog.get("newPrimaryKey"));
			String idColumn = string(newPrimaryKey.get("column"));
			tableMapping.getKeys().setTargetPrimaryKey(List.of(idColumn));
			tableMapping.getKeys().setBusinessKey(strings(tableLog.get("businessKey")));
			tableMapping.getKeys().setTargetUniqueKey(strings(tableLog.get("businessKey")));
			GeneratedKey generatedKey = new GeneratedKey();
			generatedKey.setColumn(idColumn);
			generatedKey.setDataType(string(newPrimaryKey.get("dataType")));
			generatedKey.setGenerationType(string(surrogate.get("generationType")));
			Column actual = table.getColumns().get(idColumn);
			if (actual != null && actual.getSequence() != null) {
				generatedKey.setSequence(actual.getSequence().getName());
			}
			tableMapping.getKeys().setGeneratedKey(generatedKey);
			ColumnMapping idMapping = new ColumnMapping();
			idMapping.setTarget(idColumn);
			idMapping.setAction(ColumnAction.GENERATE);
			idMapping.setTargetDefinition(definition(actual));
			idMapping.getConversion().put("type", generatedKey.getGenerationType());
			tableMapping.getColumns().add(0, idMapping);
			for (Map<String, Object> foreignKey : listOfMaps(tableLog.get("foreignKeyReplacements"))) {
				String parentName = string(foreignKey.get("referencedTable"));
				Table parentTable = findTable(targetRoot, null, null, parentName);
				TableMapping parent = mappingsByTarget.get(qualifiedName(parentTable));
				if (parent == null) {
					continue;
				}
				String newColumn = string(foreignKey.get("newColumn"));
				ColumnMapping reference = new ColumnMapping();
				reference.setTarget(newColumn);
				reference.setAction(ColumnAction.REFERENCE);
				reference.setTargetDefinition(definition(table.getColumns().get(newColumn)));
				reference.getConversion().put("parentMappingId", parent.getId());
				reference.getConversion().put("parentColumn", foreignKey.get("referencedColumn"));
				reference.getConversion().put("replaces", foreignKey.get("oldColumns"));
				tableMapping.getColumns().add(reference);
				if (tableMapping.getParent() == null) {
					tableMapping.setParent(new ParentMapping());
				}
				tableMapping.getParent().setMappingId(parent.getId());
				List<String> replacedColumns = strings(foreignKey.get("oldColumns"));
				tableMapping.getParent().getResolvedReference()
						.removeIf(pair -> replacedColumns.contains(pair.getChildColumn()));
				tableMapping.getParent().getResolvedReference()
						.add(new ColumnPair(string(foreignKey.get("referencedColumn")), newColumn));
				RelationshipMapping relationship = mapping.getRelationships().stream()
						.filter(rel -> rel.getParentMappingId().equals(parent.getId())
								&& rel.getChildMappingId().equals(tableMapping.getId()))
						.findFirst().orElse(null);
				if (relationship == null) {
					relationship = new RelationshipMapping();
					relationship.setId("rel-" + parent.getId() + "-" + tableMapping.getId());
					relationship.setParentMappingId(parent.getId());
					relationship.setChildMappingId(tableMapping.getId());
					List<String> parentSourceKeys = parent.getKeys().getSourcePrimaryKey();
					for (int i = 0; i < Math.min(parentSourceKeys.size(), replacedColumns.size()); i++) {
						relationship.getSourceKeys()
								.add(new ColumnPair(parentSourceKeys.get(i), replacedColumns.get(i)));
					}
					mapping.getRelationships().add(relationship);
				}
				relationship.getTargetKeys().clear();
				relationship.getTargetKeys()
						.add(new ColumnPair(string(foreignKey.get("referencedColumn")), newColumn));
				relationship.setParentIdPropagation(true);
			}
			converted.add(tableName);
		}
		record.getChanges().put("convertedTables", converted);
	}

	private TableMapping createBaseTableMapping(Table source, Table target) {
		TableMapping mapping = new TableMapping();
		mapping.setId(uniqueId(source));
		mapping.setSource(reference(source));
		mapping.setTarget(target == null ? reference(source) : reference(target));
		UniqueConstraint sourcePk = source.getPrimaryKeyConstraint();
		if (sourcePk != null) {
			mapping.getKeys().setSourcePrimaryKey(columnNames(sourcePk));
		}
		if (target != null && target.getPrimaryKeyConstraint() != null) {
			mapping.getKeys().setTargetPrimaryKey(columnNames(target.getPrimaryKeyConstraint()));
		}
		for (Column sourceColumn : source.getColumns()) {
			Column targetColumn = target == null ? null : target.getColumns().get(sourceColumn.getName());
			ColumnMapping column = new ColumnMapping();
			column.setSource(sourceColumn.getName());
			column.setTarget(targetColumn == null ? null : targetColumn.getName());
			column.setAction(targetColumn == null ? ColumnAction.DROP : ColumnAction.COPY);
			column.setSourceDefinition(definition(sourceColumn));
			column.setTargetDefinition(definition(targetColumn));
			mapping.getColumns().add(column);
		}
		return mapping;
	}

	private void updateStatistics(LegacyMigrationMapping mapping, DbCommonObject<?> source, DbCommonObject<?> target) {
		mapping.getStatistics().setSourceTableCount(SchemaUtils.toTables(source).size());
		mapping.getStatistics().setTargetTableCount(SchemaUtils.toTables(target).size());
		mapping.getStatistics().setTransformedTableCount((int) mapping.getTables().stream()
				.filter(table -> table.getOperation() != TableOperation.COPY).count());
		mapping.getStatistics().setWarningCount(mapping.getDiagnostics().getWarnings().size());
		mapping.getStatistics().setSkippedCount(mapping.getDiagnostics().getSkipped().size());
		mapping.getStatistics().setErrorCount(mapping.getDiagnostics().getErrors().size());
	}

	private DbCommonObject<?> read(File file) {
		try {
			return SchemaUtils.readXml(file);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to read schema XML: " + file, e);
		}
	}

	private Table findTable(DbCommonObject<?> root, String catalog, String schema, String name) {
		return SchemaUtils.toTables(root).stream()
				.filter(table -> equalsNullable(catalog, table.getCatalogName())
						&& equalsNullable(schema, table.getSchemaName()) && name.equalsIgnoreCase(table.getName()))
				.findFirst().orElse(null);
	}

	private boolean equalsNullable(String expected, String actual) {
		return expected == null || expected.equalsIgnoreCase(actual == null ? "" : actual);
	}

	private TableReference reference(Table table) {
		TableReference reference = new TableReference();
		reference.setCatalog(table.getCatalogName());
		reference.setSchema(table.getSchemaName());
		reference.setTable(table.getName());
		reference.setPath(table.getName());
		return reference;
	}

	private TableReference copy(TableReference source) {
		TableReference copy = new TableReference();
		copy.setCatalog(source.getCatalog());
		copy.setSchema(source.getSchema());
		copy.setTable(source.getTable());
		copy.setPath(source.getPath());
		return copy;
	}

	private ColumnDefinition definition(Column column) {
		if (column == null) {
			return null;
		}
		ColumnDefinition definition = new ColumnDefinition();
		definition.setDataType(column.getDataType() == null ? null : column.getDataType().name());
		definition.setLength(column.getLength());
		definition.setScale(column.getScale());
		definition.setNullable(!column.isNotNull());
		return definition;
	}

	private List<String> columnNames(UniqueConstraint constraint) {
		return constraint.getColumns().toColumns().stream().map(Column::getName).toList();
	}

	private String uniqueId(Table table) {
		return "table-" + qualifiedName(table).replace('.', '-').toLowerCase(java.util.Locale.ROOT);
	}

	private String qualifiedName(Table table) {
		return qualifiedName(table.getSchemaName(), table.getName());
	}

	private String qualifiedName(String schema, String table) {
		return schema == null || schema.isBlank() ? table : schema + "." + table;
	}

	private String baseName(String name) {
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> map(Object value) {
		return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> listOfMaps(Object value) {
		return value instanceof List<?> ? (List<Map<String, Object>>) value : List.of();
	}

	private List<String> strings(Object value) {
		if (!(value instanceof List<?> list)) {
			return new ArrayList<>();
		}
		return list.stream().map(String::valueOf).toList();
	}

	private String string(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	private Integer integer(Object value) {
		return value instanceof Number number ? number.intValue() : value == null ? null : Integer.valueOf(value.toString());
	}
}
