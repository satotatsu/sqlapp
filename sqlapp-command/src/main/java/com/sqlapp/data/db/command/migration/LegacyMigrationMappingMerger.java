/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.sqlapp.data.schemas.migration.LegacyMigrationMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ColumnMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.RelationshipMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.TableMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.TableOperation;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.TransformationRecord;
import com.sqlapp.exceptions.CommandException;

/**
 * Composes one schema transformation step into an existing migration mapping.
 */
public class LegacyMigrationMappingMerger {

	public LegacyMigrationMapping merge(LegacyMigrationMapping existing, LegacyMigrationMapping step) {
		LegacyMigrationMappingValidator validator = new LegacyMigrationMappingValidator();
		validator.validate(existing);
		validator.validate(step);
		verifyContinuity(existing, step);
		verifyNotApplied(existing, step);

		Map<String, TableMapping> existingByTarget = indexByTarget(existing.getTables());
		Map<String, String> resultingIds = new LinkedHashMap<>();
		for (TableMapping stepTable : step.getTables()) {
			TableMapping current = stepTable.getRole() == LegacyMigrationMapping.TableRole.DETAIL
					? null
					: existingByTarget.get(key(stepTable.getSource().getSchema(),
							stepTable.getSource().getTable()));
			if (current == null) {
				existing.getTables().add(stepTable);
				resultingIds.put(stepTable.getId(), stepTable.getId());
				continue;
			}
			resultingIds.put(stepTable.getId(), current.getId());
			composeTable(current, stepTable);
		}
		translateParents(existing.getTables(), resultingIds);
		mergeRelationships(existing, step, resultingIds);
		appendTransformations(existing, step);
		existing.getOptions().putAll(step.getOptions());
		existing.setTarget(step.getTarget());
		existing.getDiagnostics().getWarnings().addAll(step.getDiagnostics().getWarnings());
		existing.getDiagnostics().getSkipped().addAll(step.getDiagnostics().getSkipped());
		existing.getDiagnostics().getErrors().addAll(step.getDiagnostics().getErrors());
		existing.getStatistics().setTargetTableCount(step.getStatistics().getTargetTableCount());
		existing.getStatistics().setTransformedTableCount((int) existing.getTables().stream()
				.filter(table -> table.getOperation() != TableOperation.COPY).count());
		existing.getStatistics().setWarningCount(existing.getDiagnostics().getWarnings().size());
		existing.getStatistics().setSkippedCount(existing.getDiagnostics().getSkipped().size());
		existing.getStatistics().setErrorCount(existing.getDiagnostics().getErrors().size());
		validator.validate(existing);
		return existing;
	}

	private void verifyContinuity(LegacyMigrationMapping existing, LegacyMigrationMapping step) {
		String previousTarget = existing.getTarget().getSchemaFingerprint();
		String nextSource = step.getSource().getSchemaFingerprint();
		if (!Objects.equals(previousTarget, nextSource)) {
			throw new CommandException("Migration mapping is not continuous: previous target fingerprint="
					+ previousTarget + ", next source fingerprint=" + nextSource);
		}
	}

	private void verifyNotApplied(LegacyMigrationMapping existing, LegacyMigrationMapping step) {
		for (TransformationRecord next : step.getTransformations()) {
			boolean duplicate = existing.getTransformations().stream()
					.anyMatch(current -> Objects.equals(current.getCommand(), next.getCommand())
							&& Objects.equals(current.getInputFingerprint(), next.getInputFingerprint())
							&& Objects.equals(current.getOutputFingerprint(), next.getOutputFingerprint()));
			if (duplicate) {
				throw new CommandException("Transformation is already recorded: " + next.getCommand());
			}
		}
	}

	private void composeTable(TableMapping current, TableMapping step) {
		var consumedMappings = new HashSet<ColumnMapping>();
		for (ColumnMapping currentColumn : current.getColumns()) {
			if (currentColumn.getTarget() == null) {
				continue;
			}
			ColumnMapping next = step.getColumns().stream()
					.filter(column -> currentColumn.getTarget().equalsIgnoreCase(column.getSource()))
					.findFirst().orElse(null);
			if (next == null) {
				if (step.getOperation() == TableOperation.SPLIT
						&& step.getColumns().stream().noneMatch(column ->
								currentColumn.getTarget().equalsIgnoreCase(column.getTarget()))) {
					currentColumn.setTarget(null);
					currentColumn.setTargetDefinition(null);
					currentColumn.setAction(LegacyMigrationMapping.ColumnAction.DROP);
				}
				continue;
			}
			consumedMappings.add(next);
			currentColumn.setTarget(next.getTarget());
			currentColumn.setTargetDefinition(next.getTargetDefinition());
			if (next.getAction() != LegacyMigrationMapping.ColumnAction.COPY) {
				currentColumn.setAction(next.getAction());
				currentColumn.getConversion().putAll(next.getConversion());
				currentColumn.setReason(next.getReason());
			}
		}
		for (ColumnMapping next : step.getColumns()) {
			boolean alreadyPresent = next.getTarget() != null && current.getColumns().stream()
					.anyMatch(column -> next.getTarget().equalsIgnoreCase(column.getTarget()));
			if (!consumedMappings.contains(next) && !alreadyPresent) {
				current.getColumns().add(next);
			}
		}
		current.setTarget(step.getTarget());
		current.setKeys(step.getKeys());
		current.setParent(step.getParent());
		current.setConstraints(step.getConstraints());
		current.getDetails().putAll(step.getDetails());
		if (step.getOperation() != TableOperation.COPY) {
			current.setOperation(step.getOperation());
		}
		if (step.getRole() != LegacyMigrationMapping.TableRole.ROOT) {
			current.setRole(step.getRole());
		}
	}

	private void translateParents(List<TableMapping> tables, Map<String, String> ids) {
		for (TableMapping table : tables) {
			if (table.getParent() != null && ids.containsKey(table.getParent().getMappingId())) {
				table.getParent().setMappingId(ids.get(table.getParent().getMappingId()));
			}
		}
	}

	private void mergeRelationships(LegacyMigrationMapping existing, LegacyMigrationMapping step,
			Map<String, String> ids) {
		for (RelationshipMapping relationship : step.getRelationships()) {
			relationship.setParentMappingId(ids.getOrDefault(relationship.getParentMappingId(),
					relationship.getParentMappingId()));
			relationship.setChildMappingId(ids.getOrDefault(relationship.getChildMappingId(),
					relationship.getChildMappingId()));
			RelationshipMapping current = existing.getRelationships().stream()
					.filter(item -> item.getParentMappingId().equals(relationship.getParentMappingId())
							&& item.getChildMappingId().equals(relationship.getChildMappingId()))
					.findFirst().orElse(null);
			if (current == null) {
				existing.getRelationships().add(relationship);
			} else {
				current.setSourceKeys(relationship.getSourceKeys());
				current.setTargetKeys(relationship.getTargetKeys());
				current.setParentIdPropagation(relationship.isParentIdPropagation());
				current.setLoadOrder(relationship.getLoadOrder());
			}
		}
	}

	private void appendTransformations(LegacyMigrationMapping existing, LegacyMigrationMapping step) {
		int sequence = existing.getTransformations().stream().mapToInt(TransformationRecord::getSequence)
				.max().orElse(0);
		for (TransformationRecord record : step.getTransformations()) {
			record.setSequence(sequence += 10);
			existing.getTransformations().add(record);
		}
	}

	private Map<String, TableMapping> indexByTarget(List<TableMapping> tables) {
		Map<String, TableMapping> result = new LinkedHashMap<>();
		for (TableMapping table : tables) {
			result.put(key(table.getTarget().getSchema(), table.getTarget().getTable()), table);
		}
		return result;
	}

	private String key(String schema, String table) {
		return ((schema == null ? "" : schema) + "." + table).toLowerCase(Locale.ROOT);
	}
}
