/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
		if (contract.getDataSets().isEmpty()) {
			throw new CommandException("The legacy migration contract contains no data sets.");
		}
		Set<String> ids = new HashSet<>();
		Set<String> files = new HashSet<>();
		Map<String, DataSet> byId = new HashMap<>();
		for (DataSet dataSet : contract.getDataSets()) {
			if (blank(dataSet.getId()) || blank(dataSet.getSourcePath()) || blank(dataSet.getFileName())) {
				throw new CommandException("Data set id, sourcePath and fileName are required.");
			}
			if (!ids.add(dataSet.getId())) {
				throw new CommandException("Duplicate data set id: " + dataSet.getId());
			}
			byId.put(dataSet.getId(), dataSet);
			if (!files.add(dataSet.getFileName().toLowerCase(Locale.ROOT))) {
				throw new CommandException("Duplicate CSV file name: " + dataSet.getFileName());
			}
			int expectedPosition = 1;
			List<Field> extractedFields = dataSet.getFields().stream().filter(Field::isExtracted).toList();
			if (extractedFields.isEmpty()) {
				throw new CommandException("Data set contains no extracted fields: " + dataSet.getId());
			}
			for (Field field : extractedFields) {
				if (blank(field.getSourcePath()) || blank(field.getStagingColumn())) {
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

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
