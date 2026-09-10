/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.io.File;
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
		if (contract.getCsv() == null || blank(contract.getCsv().getEncoding())
				|| blank(contract.getCsv().getDelimiter()) || contract.getCsv().getQuote() == null
				|| blank(contract.getCsv().getRecordSeparator())) {
			throw new CommandException("The legacy migration contract CSV format is invalid.");
		}
		if (contract.getDataSets() == null || contract.getDataSets().isEmpty()) {
			throw new CommandException("The legacy migration contract contains no data sets.");
		}
		Set<String> ids = new HashSet<>();
		Set<String> files = new HashSet<>();
		Map<String, DataSet> byId = new HashMap<>();
		for (DataSet dataSet : contract.getDataSets()) {
			if (dataSet == null || blank(dataSet.getId()) || blank(dataSet.getSourcePath())
					|| blank(dataSet.getFileName()) || blank(dataSet.getTargetTable())
					|| dataSet.getFields() == null) {
				throw new CommandException("Data set id, sourcePath and fileName are required.");
			}
			if (!ids.add(dataSet.getId())) {
				throw new CommandException("Duplicate data set id: " + dataSet.getId());
			}
			byId.put(dataSet.getId(), dataSet);
			if (!files.add(dataSet.getFileName().toLowerCase(Locale.ROOT))) {
				throw new CommandException("Duplicate CSV file name: " + dataSet.getFileName());
			}
			if (dataSet.getHierarchyDepth() < 0 || dataSet.getLoadOrder() < 0
					|| invalidNames(dataSet.getSourceBusinessKey())
					|| invalidNames(dataSet.getTargetPrimaryKey())) {
				throw new CommandException("Data set hierarchy, load order or keys are invalid: "
						+ dataSet.getId());
			}
			if (dataSet.getFields().stream().anyMatch(field -> field == null)) {
				throw new CommandException("Data set contains a null field: " + dataSet.getId());
			}
			int expectedPosition = 1;
			List<Field> extractedFields = dataSet.getFields().stream()
					.filter(field -> field != null && field.isExtracted()).toList();
			if (extractedFields.isEmpty()) {
				throw new CommandException("Data set contains no extracted fields: " + dataSet.getId());
			}
			for (Field field : extractedFields) {
				if ((blank(field.getSourcePath()) && (field.getIndexedSources() == null
						|| field.getIndexedSources().isEmpty())
						&& !field.isOccurrenceIndex())
						|| blank(field.getStagingColumn())) {
					throw new CommandException("Extracted field requires sourcePath and stagingColumn: "
							+ dataSet.getId());
				}
				if (field.getPosition() < expectedPosition) {
					throw new CommandException("Invalid field order in data set: " + dataSet.getId());
				}
				expectedPosition = field.getPosition() + 1;
			}
		}
		for (DataSet dataSet : contract.getDataSets()) {
			if (dataSet.getParentDataSetId() != null && !ids.contains(dataSet.getParentDataSetId())) {
				throw new CommandException("Unknown parent data set: " + dataSet.getParentDataSetId());
			}
			if (dataSet.getParentDataSetId() != null) {
				DataSet parent = byId.get(dataSet.getParentDataSetId());
				if (dataSet.getHierarchyDepth() <= parent.getHierarchyDepth()
						|| dataSet.getLoadOrder() <= parent.getLoadOrder()
						|| dataSet.getAncestorKeys() == null
						|| dataSet.getAncestorKeys().isEmpty()) {
					throw new CommandException("Child data set hierarchy is inconsistent: "
							+ dataSet.getId());
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
		}
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
			throw new CommandException("Migration mapping fingerprint does not match the contract: "
					+ mappingFile);
		}
		var mapping = new LegacyMigrationMappingIO().read(mappingFile);
		if (!Objects.equals(contract.getMigrationId(), mapping.getMigration().getId())) {
			throw new CommandException("Migration mapping migrationId does not match the contract.");
		}
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
				|| new HashSet<>(values).size() != values.size();
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
