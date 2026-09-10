/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.sqlapp.data.db.command.AbstractDataSourceCommand;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract;
import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlan;
import com.sqlapp.exceptions.CommandException;

import lombok.Getter;
import lombok.Setter;

/**
 * Executes a generated legacy hierarchy load plan.
 */
@Getter
@Setter
public class LoadLegacyHierarchyCommand extends AbstractDataSourceCommand {

	private File loadPlanFile;

	private File schemaFile;

	@Override
	protected void doRun() {
		if (loadPlanFile == null || !loadPlanFile.isFile()) {
			throw new CommandException("Legacy load plan file does not exist: " + loadPlanFile);
		}
		var plan = new LegacyMigrationLoadPlanIO().read(loadPlanFile);
		validateContract(plan);
		File targetSchemaFile = schemaFile == null ? new File(plan.getSchemaFile()) : schemaFile;
		if (!targetSchemaFile.isFile()) {
			throw new CommandException("Target schema XML file does not exist: " + targetSchemaFile);
		}
		String fingerprint = new LegacyMigrationMappingValidator().fingerprint(targetSchemaFile);
		if (plan.getSchemaFingerprint() != null
				&& !plan.getSchemaFingerprint().equals(fingerprint)) {
			throw new CommandException("Target schema fingerprint does not match the load plan: "
					+ targetSchemaFile);
		}
		validateViewpointsFingerprint(plan);
		DbCommonObject<?> schema = readSchema(targetSchemaFile);
		execute(getDataSource(), connection -> {
			long roots = new JdbcTreeStagingLoader(connection, schema, plan).load();
			info("Legacy hierarchy load completed. roots=", roots);
		});
	}

	private void validateContract(LegacyMigrationLoadPlan plan) {
		File contractFile = resolveReferencedFile(plan.getContractFile());
		if (!contractFile.isFile()) {
			throw new CommandException("Migration contract in load plan does not exist: "
					+ contractFile);
		}
		String fingerprint = new LegacyMigrationMappingValidator().fingerprint(contractFile);
		if (!fingerprint.equalsIgnoreCase(plan.getContractFingerprint())) {
			throw new CommandException("Migration contract fingerprint does not match the load plan: "
					+ contractFile);
		}
		LegacyMigrationContract contract = new LegacyMigrationContractIO().read(contractFile);
		new LegacyMigrationContractValidator().validateReferencedMapping(contract, contractFile);
		if (!Objects.equals(plan.getMigrationId(), contract.getMigrationId())) {
			throw new CommandException("Migration contract migrationId does not match the load plan.");
		}
		Map<String, LegacyMigrationContract.DataSet> contractDataSets = new LinkedHashMap<>();
		contract.getDataSets().forEach(dataSet -> contractDataSets.put(dataSet.getId(), dataSet));
		for (LegacyMigrationLoadPlan.LoadDataSet dataSet : plan.getDataSets()) {
			LegacyMigrationContract.DataSet source = contractDataSets.get(dataSet.getId());
			if (source == null) {
				throw new CommandException("Load plan data set is absent from its contract: "
						+ dataSet.getId());
			}
			validateDataSet(source, dataSet);
		}
	}

	private void validateDataSet(LegacyMigrationContract.DataSet source,
			LegacyMigrationLoadPlan.LoadDataSet target) {
		if (!Objects.equals(source.getFileName(), target.getFileName())
				|| !Objects.equals(source.getTargetSchema(), target.getTargetSchema())
				|| !Objects.equals(source.getTargetTable(), target.getTargetTable())
				|| !Objects.equals(source.getParentDataSetId(), target.getParentDataSetId())
				|| source.getHierarchyDepth() != target.getHierarchyDepth()
				|| source.getLoadOrder() != target.getLoadOrder()
				|| !Objects.equals(source.getSourceBusinessKey(), target.getSourceBusinessKey())
				|| !Objects.equals(source.getTargetPrimaryKey(), target.getTargetPrimaryKey())) {
			throw new CommandException("Load plan data set disagrees with its contract: "
					+ target.getId());
		}
		List<LegacyMigrationContract.Field> fields = source.getFields().stream()
				.filter(field -> field.isExtracted() || field.isGenerated()).toList();
		if (fields.size() != target.getFields().size()) {
			throw new CommandException("Load plan field count disagrees with its contract: "
					+ target.getId());
		}
		int csvPosition = 1;
		for (int i = 0; i < fields.size(); i++) {
			LegacyMigrationContract.Field contractField = fields.get(i);
			LegacyMigrationLoadPlan.LoadField planField = target.getFields().get(i);
			int expectedCsvPosition = contractField.isExtracted() ? csvPosition++ : 0;
			if (expectedCsvPosition != planField.getCsvPosition()
					|| !Objects.equals(contractField.getStagingColumn(),
							planField.getStagingColumn())
					|| !Objects.equals(contractField.getTargetColumn(), planField.getTargetColumn())
					|| !Objects.equals(contractField.getTargetDataType(), planField.getDataType())
					|| !Objects.equals(contractField.getLength(), planField.getLength())
					|| !Objects.equals(contractField.getScale(), planField.getScale())
					|| contractField.isExtracted() != planField.isExtracted()
					|| (contractField.isGenerated() && !contractField.isOccurrenceIndex())
							!= planField.isTargetGenerated()
					|| !Objects.equals(contractField.getAction(), planField.getAction())) {
				throw new CommandException("Load plan field disagrees with its contract: "
						+ target.getId() + "[" + i + "]");
			}
		}
	}

	private File resolveReferencedFile(String value) {
		File file = new File(value);
		if (file.isAbsolute()) {
			return file;
		}
		File parent = loadPlanFile.getAbsoluteFile().getParentFile();
		return new File(parent, value);
	}

	private void validateViewpointsFingerprint(
			com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlan plan) {
		if (plan.getViewpointsFile() == null || plan.getViewpointsFingerprint() == null) {
			return;
		}
		File file = new File(plan.getViewpointsFile());
		if (!file.isFile()) {
			throw new CommandException("Schema viewpoints file in load plan does not exist: " + file);
		}
		String fingerprint = new LegacyMigrationMappingValidator().fingerprint(file);
		if (!plan.getViewpointsFingerprint().equals(fingerprint)) {
			throw new CommandException("Schema viewpoints fingerprint does not match the load plan: " + file);
		}
	}

	private DbCommonObject<?> readSchema(File file) {
		try {
			return SchemaUtils.readXml(file);
		} catch (Exception e) {
			throw new CommandException("Failed to read target schema XML: " + file, e);
		}
	}
}
