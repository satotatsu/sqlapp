/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.schemas.migration.LegacyMigrationContract;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.DataSet;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.Field;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping;
import com.sqlapp.exceptions.CommandException;

/**
 * Validates the portable extraction and load contract.
 */
public class LegacyMigrationContractValidator {

	public void validate(LegacyMigrationContract contract) {
		if (contract == null || !LegacyMigrationContract.FORMAT.equals(contract.getFormat())) {
			throw new CommandException("Unsupported legacy migration contract format.");
		}
		if (contract.getVersion() != LegacyMigrationContract.CURRENT_VERSION) {
			throw new CommandException("Unsupported legacy migration contract version: " + contract.getVersion());
		}
		if (blank(contract.getMigrationId())) {
			throw new CommandException("The legacy migration contract requires migrationId.");
		}
		validateCsv(contract);
		if (contract.getDataSets() == null || contract.getDataSets().isEmpty()) {
			throw new CommandException("The legacy migration contract contains no data sets.");
		}
		Set<String> ids = new HashSet<>();
		Set<String> files = new HashSet<>();
		Map<String, DataSet> byId = new HashMap<>();
		for (DataSet dataSet : contract.getDataSets()) {
			if (dataSet == null || blank(dataSet.getId()) || blank(dataSet.getSourcePath())
					|| blank(dataSet.getFileName()) || blank(dataSet.getTargetTable()) || dataSet.getFields() == null
					|| dataSet.getAncestorKeys() == null) {
				throw new CommandException("Data set identity and collection structure are required.");
			}
			if (!ids.add(dataSet.getId())) {
				throw new CommandException("Duplicate data set id: " + dataSet.getId());
			}
			byId.put(dataSet.getId(), dataSet);
			if (!files.add(dataSet.getFileName().toLowerCase(Locale.ROOT))) {
				throw new CommandException("Duplicate CSV file name: " + dataSet.getFileName());
			}
			if (dataSet.getHierarchyDepth() < 0 || dataSet.getLoadOrder() < 0
					|| invalidNames(dataSet.getSourceBusinessKey()) || invalidNames(dataSet.getTargetPrimaryKey())) {
				throw new CommandException("Data set hierarchy, load order or keys are invalid: " + dataSet.getId());
			}
			if (dataSet.getFields().stream().anyMatch(field -> field == null)) {
				throw new CommandException("Data set contains a null field: " + dataSet.getId());
			}
			Set<String> stagingColumns = new HashSet<>();
			for (int i = 0; i < dataSet.getFields().size(); i++) {
				Field field = dataSet.getFields().get(i);
				if (field.getAction() == null) {
					throw new CommandException(
							"Unsupported field action in data set: " + dataSet.getId() + "." + field.getAction());
				}
				validateFieldSemantics(dataSet, field, i);
				if (field.getPosition() != i + 1) {
					throw new CommandException(
							"Field positions must match contract order in data set: " + dataSet.getId());
				}
				if (!field.isExtracted() && !field.isGenerated()) {
					throw new CommandException(
							"Field must be extracted or generated in data set: " + dataSet.getId() + "[" + i + "]");
				}
				if (field.isExtracted()) {
					if (blank(field.getStagingColumn())) {
						throw new CommandException(
								"Extracted field requires sourcePath and stagingColumn: " + dataSet.getId());
					}
					String stagingColumn = normalize(field.getStagingColumn());
					if (!stagingColumns.add(stagingColumn) || isReservedStagingColumn(field.getStagingColumn())) {
						throw new CommandException("Extracted staging columns must be unique and non-reserved: "
								+ dataSet.getId() + "." + field.getStagingColumn());
					}
				}
				validateIndexedSources(dataSet, field, i);
			}
			List<Field> extractedFields = dataSet.getFields().stream()
					.filter(field -> field != null && field.isExtracted()).toList();
			if (extractedFields.isEmpty()) {
				throw new CommandException("Data set contains no extracted fields: " + dataSet.getId());
			}
			for (Field field : extractedFields) {
				if ((blank(field.getSourcePath())
						&& (field.getIndexedSources() == null || field.getIndexedSources().isEmpty())
						&& !field.isOccurrenceIndex()) || blank(field.getStagingColumn())) {
					throw new CommandException(
							"Extracted field requires sourcePath and stagingColumn: " + dataSet.getId());
				}
			}
			validateOccurrence(dataSet);
		}
		List<String> orderedIds = contract.getDataSets().stream()
				.sorted(Comparator.comparingInt(DataSet::getLoadOrder).thenComparing(DataSet::getId))
				.map(DataSet::getId).toList();
		if (!orderedIds.equals(contract.getDataSets().stream().map(DataSet::getId).toList())) {
			throw new CommandException("Data sets must be ordered by loadOrder and id.");
		}
		for (DataSet dataSet : contract.getDataSets()) {
			if (dataSet.getParentDataSetId() != null && !ids.contains(dataSet.getParentDataSetId())) {
				throw new CommandException("Unknown parent data set: " + dataSet.getParentDataSetId());
			}
			if (dataSet.getParentDataSetId() != null) {
				DataSet parent = byId.get(dataSet.getParentDataSetId());
				if (dataSet.getHierarchyDepth() <= parent.getHierarchyDepth()
						|| dataSet.getLoadOrder() <= parent.getLoadOrder() || dataSet.getAncestorKeys() == null
						|| dataSet.getAncestorKeys().isEmpty()) {
					throw new CommandException("Child data set hierarchy is inconsistent: " + dataSet.getId());
				}
			} else {
				if (dataSet.getHierarchyDepth() != 0) {
					throw new CommandException("Root data set hierarchy depth must be zero: " + dataSet.getId());
				}
				if (dataSet.getSourceBusinessKey().isEmpty()) {
					throw new CommandException(
							"Root data set requires sourceBusinessKey for restart: " + dataSet.getId());
				}
			}
			Set<String> visited = new HashSet<>();
			DataSet current = dataSet;
			while (current != null && current.getParentDataSetId() != null) {
				if (!visited.add(current.getId())) {
					throw new CommandException("Cyclic data set hierarchy at: " + current.getId());
				}
				current = byId.get(current.getParentDataSetId());
			}
			validateAncestorKeys(dataSet, byId);
			validateReferencedColumns(dataSet, byId);
		}
	}

	private void validateReferencedColumns(DataSet dataSet, Map<String, DataSet> byId) {
		Set<String> extracted = dataSet.getFields().stream().filter(Field::isExtracted).map(Field::getStagingColumn)
				.map(this::normalize).collect(java.util.stream.Collectors.toSet());
		for (String key : dataSet.getSourceBusinessKey()) {
			if (!extracted.contains(normalize(key))) {
				throw new CommandException(
						"Source business key does not reference an extracted column: " + dataSet.getId() + "." + key);
			}
		}
		if (dataSet.getParentDataSetId() == null) {
			return;
		}
		for (var ancestorKey : dataSet.getAncestorKeys()) {
			DataSet ancestor = byId.get(ancestorKey.getAncestorDataSetId());
			Set<String> ancestorExtracted = ancestor.getFields().stream().filter(Field::isExtracted)
					.map(Field::getStagingColumn).map(this::normalize).collect(java.util.stream.Collectors.toSet());
			for (var column : ancestorKey.getColumns()) {
				if (!ancestorExtracted.contains(normalize(column.getAncestorColumn()))
						|| !extracted.contains(normalize(column.getSourceColumn()))) {
					throw new CommandException("Ancestor key references an unknown field: " + dataSet.getId() + "."
							+ ancestorKey.getAncestorDataSetId());
				}
			}
		}
	}

	private void validateFieldSemantics(DataSet dataSet, Field field, int fieldIndex) {
		boolean generatedAction = field.getAction() == LegacyMigrationMapping.ColumnAction.GENERATE
				|| field.getAction() == LegacyMigrationMapping.ColumnAction.CONSTANT;
		if (generatedAction != field.isGenerated()) {
			throw new CommandException(
					"Field action and generated flag disagree: " + dataSet.getId() + "[" + fieldIndex + "]");
		}
		if (field.isExtracted() && field.isGenerated() && !field.isOccurrenceIndex()) {
			throw new CommandException("Only an occurrence-index field may be both extracted and generated: "
					+ dataSet.getId() + "[" + fieldIndex + "]");
		}
		if (field.isOccurrenceIndex() && (!field.isExtracted() || !field.isGenerated())) {
			throw new CommandException("Occurrence-index field must be extracted and generated: " + dataSet.getId()
					+ "[" + fieldIndex + "]");
		}
		if (field.getAction() != LegacyMigrationMapping.ColumnAction.DROP && blank(field.getTargetColumn())) {
			throw new CommandException(
					"Non-drop field requires targetColumn: " + dataSet.getId() + "[" + fieldIndex + "]");
		}
		if (field.getAction() == LegacyMigrationMapping.ColumnAction.DROP
				&& (!field.isExtracted() || field.isGenerated())) {
			throw new CommandException(
					"Drop field must only be extracted: " + dataSet.getId() + "[" + fieldIndex + "]");
		}
	}

	private void validateCsv(LegacyMigrationContract contract) {
		var csv = contract.getCsv();
		if (csv == null || blank(csv.getEncoding()) || csv.getDelimiter() == null || csv.getQuote() == null
				|| csv.getNullValue() == null || csv.getRecordSeparator() == null
				|| csv.getDelimiter().codePointCount(0, csv.getDelimiter().length()) != 1
				|| csv.getQuote().codePointCount(0, csv.getQuote().length()) != 1
				|| csv.getDelimiter().equals(csv.getQuote())) {
			throw new CommandException("The legacy migration contract CSV format is invalid.");
		}
		try {
			if (!Charset.isSupported(csv.getEncoding())) {
				throw new CommandException("Unsupported CSV encoding: " + csv.getEncoding());
			}
		} catch (IllegalCharsetNameException e) {
			throw new CommandException("Unsupported CSV encoding: " + csv.getEncoding(), e);
		}
	}

	private void validateOccurrence(DataSet dataSet) {
		boolean hasIndexedSources = dataSet.getFields().stream()
				.anyMatch(field -> field.getIndexedSources() != null && !field.getIndexedSources().isEmpty());
		List<Field> occurrenceFields = dataSet.getFields().stream().filter(Field::isOccurrenceIndex).toList();
		boolean configured = dataSet.getMaximumOccurrences() != null || !blank(dataSet.getOccurrenceColumn())
				|| dataSet.getOccurrenceSourceMode() != null || hasIndexedSources || !occurrenceFields.isEmpty();
		if (!configured) {
			return;
		}
		if (dataSet.getMaximumOccurrences() == null || dataSet.getMaximumOccurrences() <= 0
				|| blank(dataSet.getOccurrenceColumn()) || occurrenceFields.size() != 1) {
			throw new CommandException("Data set occurrence configuration is invalid: " + dataSet.getId());
		}
		Field occurrence = occurrenceFields.getFirst();
		if (!occurrence.isExtracted() || !occurrence.isGenerated()
				|| !equalsName(dataSet.getOccurrenceColumn(), occurrence.getStagingColumn())
				|| !equalsName(dataSet.getOccurrenceColumn(), occurrence.getTargetColumn())) {
			throw new CommandException("Data set occurrence field is invalid: " + dataSet.getId());
		}
		if ((dataSet
				.getOccurrenceSourceMode() == LegacyMigrationContract.OccurrenceSourceMode.NUMBERED_COLUMNS) != hasIndexedSources) {
			throw new CommandException("Data set occurrence source mode is inconsistent: " + dataSet.getId());
		}
		for (Field field : dataSet.getFields()) {
			if (field.getIndexedSources().stream()
					.anyMatch(source -> source.getIndex() > dataSet.getMaximumOccurrences())) {
				throw new CommandException("Indexed source exceeds occurrence maximum: " + dataSet.getId());
			}
		}
	}

	private boolean equalsName(String left, String right) {
		return left != null && right != null && left.equalsIgnoreCase(right);
	}

	private void validateIndexedSources(DataSet dataSet, Field field, int fieldIndex) {
		if (field.getIndexedSources() == null) {
			throw new CommandException(
					"Indexed source collection is required: " + dataSet.getId() + "[" + fieldIndex + "]");
		}
		if (field.getIndexedSources().isEmpty()) {
			return;
		}
		if (!field.isExtracted()) {
			throw new CommandException(
					"Indexed sources require an extracted field: " + dataSet.getId() + "[" + fieldIndex + "]");
		}
		Set<Integer> indexes = new HashSet<>();
		for (var source : field.getIndexedSources()) {
			if (source == null || source.getIndex() <= 0 || !indexes.add(source.getIndex())
					|| blank(source.getSourceColumn()) || blank(source.getSourcePath())) {
				throw new CommandException("Indexed sources are invalid: " + dataSet.getId() + "[" + fieldIndex + "]");
			}
		}
	}

	private boolean isReservedStagingColumn(String name) {
		return "SQLAPP_LOAD_STATUS".equalsIgnoreCase(name) || "SQLAPP_LOADED_AT".equalsIgnoreCase(name);
	}

	private void validateAncestorKeys(DataSet dataSet, Map<String, DataSet> byId) {
		if (dataSet.getParentDataSetId() == null) {
			if (dataSet.getAncestorKeys() != null && !dataSet.getAncestorKeys().isEmpty()) {
				throw new CommandException("Root data set must not define ancestor keys: " + dataSet.getId());
			}
			return;
		}
		if (dataSet.getAncestorKeys() == null) {
			throw new CommandException("Child data set ancestor keys are invalid: " + dataSet.getId());
		}
		DataSet expectedAncestor = byId.get(dataSet.getParentDataSetId());
		for (int i = 0; i < dataSet.getAncestorKeys().size(); i++) {
			var key = dataSet.getAncestorKeys().get(i);
			if (key == null || expectedAncestor == null || key.getDepth() != i + 1
					|| !Objects.equals(key.getAncestorDataSetId(), expectedAncestor.getId())
					|| !Objects.equals(key.getAncestorTable(), expectedAncestor.getTargetTable())
					|| invalidNames(key.getTargetForeignKey()) || key.getTargetForeignKey().isEmpty()
					|| key.getColumns() == null || key.getColumns().isEmpty()) {
				throw new CommandException("Child data set ancestor chain is invalid: " + dataSet.getId());
			}
			Set<String> ancestorColumns = new HashSet<>();
			Set<String> sourceColumns = new HashSet<>();
			for (var column : key.getColumns()) {
				if (column == null || blank(column.getAncestorColumn()) || blank(column.getSourceColumn())
						|| !ancestorColumns.add(normalize(column.getAncestorColumn()))
						|| !sourceColumns.add(normalize(column.getSourceColumn()))) {
					throw new CommandException(
							"Child data set ancestor key columns are invalid: " + dataSet.getId() + "[" + i + "]");
				}
			}
			expectedAncestor = expectedAncestor.getParentDataSetId() == null ? null
					: byId.get(expectedAncestor.getParentDataSetId());
		}
		if (expectedAncestor != null) {
			throw new CommandException("Child data set ancestor chain is incomplete: " + dataSet.getId());
		}
	}

	private String normalize(String value) {
		return value.toLowerCase(Locale.ROOT);
	}

	public void validateReferencedMapping(LegacyMigrationContract contract, File contractFile) {
		validate(contract);
		boolean hasFile = !blank(contract.getMappingFile());
		boolean hasFingerprint = !blank(contract.getMappingFingerprint());
		if (!hasFile && !hasFingerprint) {
			return;
		}
		if (!hasFile || !hasFingerprint) {
			throw new CommandException(
					"Migration contract mappingFile and mappingFingerprint must be specified together.");
		}
		File mappingFile = resolve(contractFile, contract.getMappingFile());
		if (!mappingFile.isFile()) {
			throw new CommandException("Migration mapping in contract does not exist: " + mappingFile);
		}
		LegacyMigrationMappingValidator validator = new LegacyMigrationMappingValidator();
		String actual = validator.fingerprint(mappingFile);
		if (!contract.getMappingFingerprint().equalsIgnoreCase(actual)) {
			throw new CommandException("Migration mapping fingerprint does not match the contract: " + mappingFile);
		}
		var mapping = new LegacyMigrationMappingIO().read(mappingFile);
		if (!Objects.equals(contract.getMigrationId(), mapping.getMigration().getId())) {
			throw new CommandException("Migration mapping migrationId does not match the contract.");
		}
		validateMappingDataSets(contract, mappingFile, mapping);
	}

	private void validateMappingDataSets(LegacyMigrationContract contract, File mappingFile,
			LegacyMigrationMapping mapping) {
		LegacyMigrationContract expected = new LegacyMigrationContractBuilder().build(mappingFile, mapping);
		Map<String, DataSet> expectedDataSets = new HashMap<>();
		expected.getDataSets().forEach(dataSet -> expectedDataSets.put(dataSet.getId(), dataSet));
		if (expectedDataSets.size() != contract.getDataSets().size()) {
			throw new CommandException("Migration contract data sets do not match the referenced mapping.");
		}
		for (DataSet dataSet : contract.getDataSets()) {
			DataSet expectedDataSet = expectedDataSets.get(dataSet.getId());
			if (expectedDataSet == null || !sameProvenance(dataSet, expectedDataSet)) {
				throw new CommandException(
						"Migration contract data set disagrees with the referenced mapping: " + dataSet.getId());
			}
		}
	}

	private boolean sameProvenance(DataSet left, DataSet right) {
		return Objects.equals(left.getId(), right.getId())
				&& Objects.equals(left.getSourcePath(), right.getSourcePath())
				&& Objects.equals(left.getTargetCatalog(), right.getTargetCatalog())
				&& Objects.equals(left.getTargetSchema(), right.getTargetSchema())
				&& Objects.equals(left.getTargetTable(), right.getTargetTable())
				&& left.getHierarchyDepth() == right.getHierarchyDepth() && left.getLoadOrder() == right.getLoadOrder()
				&& Objects.equals(left.getParentDataSetId(), right.getParentDataSetId())
				&& Objects.equals(left.getMaximumOccurrences(), right.getMaximumOccurrences())
				&& Objects.equals(left.getOccurrenceColumn(), right.getOccurrenceColumn())
				&& left.getOccurrenceSourceMode() == right.getOccurrenceSourceMode()
				&& Objects.equals(left.getSourceBusinessKey(), right.getSourceBusinessKey())
				&& Objects.equals(left.getTargetPrimaryKey(), right.getTargetPrimaryKey())
				&& sameFields(left.getFields(), right.getFields())
				&& sameAncestorKeys(left.getAncestorKeys(), right.getAncestorKeys());
	}

	private boolean sameFields(List<Field> left, List<Field> right) {
		if (left.size() != right.size()) {
			return false;
		}
		for (int i = 0; i < left.size(); i++) {
			Field first = left.get(i);
			Field second = right.get(i);
			if (first.getPosition() != second.getPosition()
					|| !Objects.equals(first.getSourcePath(), second.getSourcePath())
					|| !Objects.equals(first.getSourceColumn(), second.getSourceColumn())
					|| !Objects.equals(first.getStagingColumn(), second.getStagingColumn())
					|| !Objects.equals(first.getTargetColumn(), second.getTargetColumn())
					|| !Objects.equals(first.getTargetDataType(), second.getTargetDataType())
					|| !Objects.equals(first.getLength(), second.getLength())
					|| !Objects.equals(first.getScale(), second.getScale())
					|| !Objects.equals(first.getNullable(), second.getNullable())
					|| first.getAction() != second.getAction() || first.isExtracted() != second.isExtracted()
					|| first.isGenerated() != second.isGenerated()
					|| first.isOccurrenceIndex() != second.isOccurrenceIndex()
					|| !Objects.equals(first.getRemarks(), second.getRemarks())
					|| !sameIndexedSources(first.getIndexedSources(), second.getIndexedSources())) {
				return false;
			}
		}
		return true;
	}

	private boolean sameIndexedSources(List<LegacyMigrationContract.IndexedSource> left,
			List<LegacyMigrationContract.IndexedSource> right) {
		if (left.size() != right.size()) {
			return false;
		}
		for (int i = 0; i < left.size(); i++) {
			var first = left.get(i);
			var second = right.get(i);
			if (first.getIndex() != second.getIndex()
					|| !Objects.equals(first.getSourceColumn(), second.getSourceColumn())
					|| !Objects.equals(first.getSourcePath(), second.getSourcePath())) {
				return false;
			}
		}
		return true;
	}

	private boolean sameAncestorKeys(List<LegacyMigrationContract.AncestorKey> left,
			List<LegacyMigrationContract.AncestorKey> right) {
		if (left.size() != right.size()) {
			return false;
		}
		for (int i = 0; i < left.size(); i++) {
			var first = left.get(i);
			var second = right.get(i);
			if (!Objects.equals(first.getAncestorDataSetId(), second.getAncestorDataSetId())
					|| !Objects.equals(first.getAncestorTable(), second.getAncestorTable())
					|| first.getDepth() != second.getDepth()
					|| !Objects.equals(first.getTargetForeignKey(), second.getTargetForeignKey())
					|| first.getColumns().size() != second.getColumns().size()) {
				return false;
			}
			for (int j = 0; j < first.getColumns().size(); j++) {
				var firstColumn = first.getColumns().get(j);
				var secondColumn = second.getColumns().get(j);
				if (!Objects.equals(firstColumn.getAncestorColumn(), secondColumn.getAncestorColumn())
						|| !Objects.equals(firstColumn.getSourceColumn(), secondColumn.getSourceColumn())) {
					return false;
				}
			}
		}
		return true;
	}

	private File resolve(File owner, String value) {
		File file = new File(value);
		if (file.isAbsolute()) {
			return file;
		}
		File parent = owner.getAbsoluteFile().getParentFile();
		return new File(parent, value);
	}

	private boolean invalidNames(List<String> values) {
		return values == null || values.stream().anyMatch(this::blank)
				|| values.stream().map(this::normalize).distinct().count() != values.size();
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
