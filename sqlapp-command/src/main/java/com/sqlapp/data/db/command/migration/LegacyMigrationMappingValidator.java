/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.schemas.migration.LegacyMigrationMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ColumnAction;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ColumnMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ColumnPair;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.GeneratedKey;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.GeneratedKeyGenerationType;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.Diagnostic;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.DiagnosticSeverity;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.RelationshipMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.TableMapping;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.MessageDigests;

/**
 * Validates mapping structure and protects mappings from being applied to a
 * different schema artifact.
 */
public class LegacyMigrationMappingValidator {

	public void validate(LegacyMigrationMapping mapping) {
		if (mapping == null) {
			throw new CommandException("legacy migration mapping is required.");
		}
		if (!LegacyMigrationMapping.FORMAT.equals(mapping.getFormat())) {
			throw new CommandException("Unsupported legacy migration mapping format: " + mapping.getFormat());
		}
		if (mapping.getVersion() != LegacyMigrationMapping.CURRENT_VERSION) {
			throw new CommandException("Unsupported legacy migration mapping version: " + mapping.getVersion());
		}
		if (mapping.getMigration() == null || mapping.getSource() == null || mapping.getTarget() == null
				|| mapping.getTables() == null || mapping.getRelationships() == null
				|| mapping.getTransformations() == null || mapping.getDiagnostics() == null
				|| mapping.getStatistics() == null || mapping.getOptions() == null) {
			throw new CommandException("Legacy migration mapping structure is incomplete.");
		}
		Set<String> ids = new HashSet<>();
		Map<String, TableMapping> byId = new HashMap<>();
		for (TableMapping table : mapping.getTables()) {
			if (table == null || table.getId() == null || table.getId().isBlank()) {
				throw new CommandException("Every table mapping requires an id.");
			}
			if (!ids.add(table.getId())) {
				throw new CommandException("Duplicate table mapping id: " + table.getId());
			}
			byId.put(table.getId(), table);
			if (table.getTarget() == null || table.getTarget().getTable() == null
					|| table.getTarget().getTable().isBlank()) {
				throw new CommandException("Every table mapping requires target.table: " + table.getId());
			}
			if (table.getSource() == null || table.getRole() == null || table.getOperation() == null
					|| table.getKeys() == null || table.getColumns() == null || table.getConstraints() == null
					|| table.getDetails() == null) {
				throw new CommandException("Table mapping structure is incomplete: " + table.getId());
			}
			validateNames(table.getKeys().getSourcePrimaryKey(), "sourcePrimaryKey", table.getId());
			validateNames(table.getKeys().getTargetPrimaryKey(), "targetPrimaryKey", table.getId());
			validateNames(table.getKeys().getBusinessKey(), "businessKey", table.getId());
			validateNames(table.getKeys().getTargetUniqueKey(), "targetUniqueKey", table.getId());
			validateGeneratedKey(table);
			for (ColumnMapping column : table.getColumns()) {
				validateColumn(column, table.getId());
			}
		}
		for (TableMapping table : mapping.getTables()) {
			if (table.getParent() != null) {
				if (!ids.contains(table.getParent().getMappingId())) {
					throw new CommandException("Unknown parent mapping id: " + table.getParent().getMappingId());
				}
				if (table.getId().equals(table.getParent().getMappingId())) {
					throw new CommandException("Table mapping cannot be its own parent: " + table.getId());
				}
			}
			validateParentCycle(table, byId);
		}
		Set<String> relationshipIds = new HashSet<>();
		Set<String> hierarchicalChildren = new HashSet<>();
		for (RelationshipMapping relationship : mapping.getRelationships()) {
			if (relationship == null) {
				throw new CommandException("Legacy migration mapping contains a null relationship.");
			}
			if (relationship.getId() == null || relationship.getId().isBlank()
					|| !relationshipIds.add(relationship.getId())) {
				throw new CommandException("Relationship ids must be non-empty and unique: " + relationship.getId());
			}
			if (!ids.contains(relationship.getParentMappingId()) || !ids.contains(relationship.getChildMappingId())) {
				throw new CommandException("Relationship refers to an unknown table mapping: " + relationship.getId());
			}
			if (relationship.getParentMappingId().equals(relationship.getChildMappingId())
					|| relationship.getType() == null || relationship.getDepth() < 0
					|| relationship.getLoadOrder() < 0) {
				throw new CommandException("Relationship structure is invalid: " + relationship.getId());
			}
			validatePairs(relationship.getSourceKeys(), "sourceKeys", relationship.getId());
			validatePairs(relationship.getTargetKeys(), "targetKeys", relationship.getId());
			if (relationship.getType() == LegacyMigrationMapping.RelationshipType.HIERARCHICAL
					&& !hierarchicalChildren.add(relationship.getChildMappingId())) {
				throw new CommandException(
						"Multiple hierarchical parents are not supported: " + relationship.getChildMappingId());
			}
			TableMapping child = byId.get(relationship.getChildMappingId());
			if (child.getParent() != null
					&& !relationship.getParentMappingId().equals(child.getParent().getMappingId())) {
				throw new CommandException("Relationship disagrees with child parent mapping: " + relationship.getId());
			}
		}
		validateHierarchyConsistency(mapping, byId);
		validateDiagnostics(mapping, ids);
		validateTransformations(mapping);
	}

	private void validateHierarchyConsistency(LegacyMigrationMapping mapping, Map<String, TableMapping> byId) {
		for (TableMapping table : mapping.getTables()) {
			List<RelationshipMapping> parents = mapping.getRelationships().stream()
					.filter(relationship -> relationship.getType() == LegacyMigrationMapping.RelationshipType.HIERARCHICAL
							&& table.getId().equals(relationship.getChildMappingId()))
					.toList();
			if (table.getParent() == null) {
				if (!parents.isEmpty()) {
					throw new CommandException("Hierarchical relationship requires child parent mapping: " + table.getId());
				}
				continue;
			}
			if (parents.size() != 1 || !table.getParent().getMappingId().equals(parents.getFirst().getParentMappingId())) {
				throw new CommandException("Child parent mapping requires one matching hierarchical relationship: "
						+ table.getId());
			}
			if (!byId.containsKey(table.getParent().getMappingId())) {
				throw new CommandException("Unknown parent mapping id: " + table.getParent().getMappingId());
			}
			validatePairs(table.getParent().getSourceReference(), "parent.sourceReference", table.getId());
			validatePairs(table.getParent().getResolvedReference(), "parent.resolvedReference", table.getId());
			RelationshipMapping relationship = parents.getFirst();
			if (!samePairs(table.getParent().getSourceReference(), relationship.getSourceKeys())
					|| !samePairs(table.getParent().getResolvedReference(), relationship.getTargetKeys())) {
				throw new CommandException("Child parent references disagree with hierarchical relationship: "
						+ table.getId());
			}
		}
	}

	private boolean samePairs(List<ColumnPair> left, List<ColumnPair> right) {
		if (left.size() != right.size()) {
			return false;
		}
		for (int i = 0; i < left.size(); i++) {
			if (!equalsName(left.get(i).getParentColumn(), right.get(i).getParentColumn())
					|| !equalsName(left.get(i).getChildColumn(), right.get(i).getChildColumn())) {
				return false;
			}
		}
		return true;
	}

	private void validateDiagnostics(LegacyMigrationMapping mapping, Set<String> tableIds) {
		var diagnostics = mapping.getDiagnostics();
		if (diagnostics.getWarnings() == null || diagnostics.getSkipped() == null || diagnostics.getErrors() == null) {
			throw new CommandException("Legacy migration diagnostic collections are required.");
		}
		validateDiagnostics(diagnostics.getWarnings(), DiagnosticSeverity.WARNING, tableIds, "warning");
		validateDiagnostics(diagnostics.getSkipped(), null, tableIds, "skipped");
		validateDiagnostics(diagnostics.getErrors(), DiagnosticSeverity.ERROR, tableIds, "error");
		var statistics = mapping.getStatistics();
		if (statistics.getSourceTableCount() < 0 || statistics.getTargetTableCount() < 0
				|| statistics.getTransformedTableCount() < 0 || statistics.getWarningCount() < 0
				|| statistics.getSkippedCount() < 0 || statistics.getErrorCount() < 0
				|| statistics.getWarningCount() != diagnostics.getWarnings().size()
				|| statistics.getSkippedCount() != diagnostics.getSkipped().size()
				|| statistics.getErrorCount() != diagnostics.getErrors().size()) {
			throw new CommandException("Legacy migration statistics disagree with diagnostics.");
		}
	}

	private void validateDiagnostics(List<Diagnostic> values, DiagnosticSeverity requiredSeverity, Set<String> tableIds,
			String category) {
		for (Diagnostic diagnostic : values) {
			if (diagnostic == null || blank(diagnostic.getCode()) || diagnostic.getSeverity() == null
					|| requiredSeverity != null && diagnostic.getSeverity() != requiredSeverity
					|| diagnostic.getTableMappingId() != null && !tableIds.contains(diagnostic.getTableMappingId())
					|| blank(diagnostic.getAction()) || blank(diagnostic.getMessage()) || diagnostic.getDetails() == null) {
				throw new CommandException("Legacy migration " + category + " diagnostic is invalid.");
			}
		}
	}

	private void validateGeneratedKey(TableMapping table) {
		GeneratedKey key = table.getKeys().getGeneratedKey();
		if (key == null) {
			return;
		}
		if (blank(key.getColumn()) || blank(key.getDataType()) || key.getGenerationType() == null
				|| key.getGenerationType() == GeneratedKeyGenerationType.SEQUENCE && blank(key.getSequence())
				|| key.getGenerationType() == GeneratedKeyGenerationType.IDENTITY && !blank(key.getSequence())) {
			throw new CommandException("Generated key mapping is incomplete or inconsistent: " + table.getId());
		}
		if (table.getKeys().getTargetPrimaryKey().stream().noneMatch(name -> equalsName(name, key.getColumn()))) {
			throw new CommandException("Generated key must belong to targetPrimaryKey: " + table.getId());
		}
		ColumnMapping generatedColumn = table.getColumns().stream()
				.filter(column -> equalsName(column.getTarget(), key.getColumn())).findFirst().orElse(null);
		if (generatedColumn == null || generatedColumn.getAction() != ColumnAction.GENERATE
				|| !key.getGenerationType().name().equals(generatedColumn.getConversion().get("type"))) {
			throw new CommandException("Generated key column mapping is inconsistent: " + table.getId());
		}
	}

	private void validateColumn(ColumnMapping column, String tableId) {
		if (column == null || column.getAction() == null || column.getConversion() == null
				|| column.getSourceColumns() == null) {
			throw new CommandException("Column mapping structure is incomplete: " + tableId);
		}
		ColumnAction action = column.getAction();
		if ((action == ColumnAction.GENERATE || action == ColumnAction.CONSTANT || action == ColumnAction.REFERENCE)
				&& blank(column.getTarget())
				|| action == ColumnAction.DROP && blank(column.getSource())
				|| (action == ColumnAction.COPY || action == ColumnAction.RENAME || action == ColumnAction.CAST)
						&& (blank(column.getSource()) || blank(column.getTarget()))
				|| (action == ColumnAction.DERIVE || action == ColumnAction.SPLIT || action == ColumnAction.COMBINE)
						&& (blank(column.getTarget())
								|| blank(column.getSource()) && column.getSourceColumns().isEmpty())) {
			throw new CommandException("Column mapping endpoints are invalid for " + action + ": " + tableId);
		}
		if (action == ColumnAction.REFERENCE && (blankValue(column.getConversion().get("parentMappingId"))
				|| blankValue(column.getConversion().get("parentColumn")))) {
			throw new CommandException("Reference column mapping is incomplete: " + tableId);
		}
		Set<Integer> indexes = new HashSet<>();
		for (var source : column.getSourceColumns()) {
			if (source == null || source.getIndex() == null || source.getIndex() <= 0 || blank(source.getColumn())
					|| !indexes.add(source.getIndex())) {
				throw new CommandException("Indexed source columns are invalid: " + tableId);
			}
		}
	}

	private void validateParentCycle(TableMapping table, Map<String, TableMapping> byId) {
		Set<String> visited = new HashSet<>();
		TableMapping current = table;
		while (current.getParent() != null) {
			if (!visited.add(current.getId())) {
				throw new CommandException("Cyclic table mapping hierarchy at: " + current.getId());
			}
			current = byId.get(current.getParent().getMappingId());
		}
	}

	private void validateNames(List<String> values, String role, String tableId) {
		if (values == null || values.stream().anyMatch(this::blank) || new HashSet<>(values).size() != values.size()) {
			throw new CommandException("Table mapping " + role + " is invalid: " + tableId);
		}
	}

	private void validatePairs(List<ColumnPair> pairs, String role, String relationshipId) {
		if (pairs == null || pairs.stream()
				.anyMatch(pair -> pair == null || blank(pair.getParentColumn()) || blank(pair.getChildColumn()))) {
			throw new CommandException("Relationship " + role + " is invalid: " + relationshipId);
		}
	}

	private void validateTransformations(LegacyMigrationMapping mapping) {
		Set<Integer> sequences = new HashSet<>();
		int previousSequence = 0;
		String previousOutput = null;
		for (var transformation : mapping.getTransformations()) {
			if (transformation == null || transformation.getSequence() <= 0
					|| transformation.getSequence() <= previousSequence
					|| !sequences.add(transformation.getSequence()) || blank(transformation.getCommand())
					|| transformation.getStatus() == null || transformation.getConfiguration() == null
					|| transformation.getChanges() == null) {
				throw new CommandException("Legacy migration transformation is invalid.");
			}
			if (!blank(previousOutput) && !blank(transformation.getInputFingerprint())
					&& !Objects.equals(previousOutput, transformation.getInputFingerprint())) {
				throw new CommandException("Legacy migration transformation fingerprint chain is discontinuous.");
			}
			previousSequence = transformation.getSequence();
			previousOutput = transformation.getOutputFingerprint();
		}
		if (!mapping.getTransformations().isEmpty()) {
			var first = mapping.getTransformations().getFirst();
			var last = mapping.getTransformations().getLast();
			if (!Objects.equals(mapping.getSource().getSchemaFingerprint(), first.getInputFingerprint())
					|| !Objects.equals(mapping.getTarget().getSchemaFingerprint(), last.getOutputFingerprint())) {
				throw new CommandException("Legacy migration transformation endpoints disagree with schema fingerprints.");
			}
		}
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private boolean equalsName(String left, String right) {
		return left != null && right != null && left.equalsIgnoreCase(right);
	}

	private boolean blankValue(Object value) {
		return value == null || String.valueOf(value).isBlank();
	}

	public void validateSourceFingerprint(LegacyMigrationMapping mapping, File sourceSchema) {
		validateFingerprint("source", mapping.getSource().getSchemaFingerprint(), sourceSchema);
	}

	public void validateTargetFingerprint(LegacyMigrationMapping mapping, File targetSchema) {
		validateFingerprint("target", mapping.getTarget().getSchemaFingerprint(), targetSchema);
	}

	public String fingerprint(File file) {
		return "sha256:" + MessageDigests.SHA256.checksumAsString(file);
	}

	private void validateFingerprint(String endpoint, String expected, File file) {
		if (expected == null || expected.isBlank()) {
			throw new CommandException(endpoint + " schema fingerprint is not defined.");
		}
		String actual = fingerprint(file);
		if (!expected.equalsIgnoreCase(actual)) {
			throw new CommandException(endpoint + " schema fingerprint mismatch: expected=" + expected + ", actual="
					+ actual + ", file=" + file);
		}
	}
}
