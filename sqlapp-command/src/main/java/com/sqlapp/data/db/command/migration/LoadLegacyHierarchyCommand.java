/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.sqlapp.data.db.command.AbstractDataSourceCommand;
import com.sqlapp.data.db.command.viewpoint.SchemaViewpointCommandSupport;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaCollection;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract;
import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlan;
import com.sqlapp.data.schemas.viewpoint.SchemaViewpointResolver;
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
		File targetSchemaFile = schemaFile == null ? resolveReferencedFile(plan.getSchemaFile()) : schemaFile;
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
		validateViewpointResolution(plan, schema);
		LegacyMigrationLoadPlanIO.validateSchema(plan, SchemaUtils.toTables(schema));
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
		var contractIds = new LinkedHashSet<>(contractDataSets.keySet());
		List<String> orderedPlanIds = plan.getDataSets().stream()
				.map(LegacyMigrationLoadPlan.LoadDataSet::getId).toList();
		var planIds = new LinkedHashSet<>(orderedPlanIds);
		var unexpected = new LinkedHashSet<>(planIds);
		unexpected.removeAll(contractIds);
		if (!unexpected.isEmpty()) {
			throw new CommandException("Load plan data sets disagree with its contract: unexpected="
					+ unexpected);
		}
		if (!hasText(plan.getViewpointId()) && !contractIds.equals(planIds)) {
			var missing = new LinkedHashSet<>(contractIds);
			missing.removeAll(planIds);
			throw new CommandException("Load plan data sets disagree with its contract: missing="
					+ missing + ", unexpected=[]");
		}
		for (LegacyMigrationLoadPlan.LoadDataSet dataSet : plan.getDataSets()) {
			LegacyMigrationContract.DataSet source = contractDataSets.get(dataSet.getId());
			if (source == null) {
				throw new CommandException("Load plan data set is absent from its contract: "
						+ dataSet.getId());
			}
			validateDataSet(source, dataSet, plan.getStagingTablePrefix());
		}
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private void validateDataSet(LegacyMigrationContract.DataSet source,
			LegacyMigrationLoadPlan.LoadDataSet target, String stagingTablePrefix) {
		if (!Objects.equals(source.getFileName(), target.getFileName())
				|| !Objects.equals(expectedStagingTable(source, stagingTablePrefix),
						target.getStagingTable())
				|| !Objects.equals(source.getTargetCatalog(), target.getTargetCatalog())
				|| !Objects.equals(source.getTargetSchema(), target.getTargetSchema())
				|| !Objects.equals(source.getTargetTable(), target.getTargetTable())
				|| !Objects.equals(source.getParentDataSetId(), target.getParentDataSetId())
				|| source.getHierarchyDepth() != target.getHierarchyDepth()
				|| source.getLoadOrder() != target.getLoadOrder()
				|| !Objects.equals(source.getSourceBusinessKey(), target.getSourceBusinessKey())
				|| !Objects.equals(source.getTargetPrimaryKey(), target.getTargetPrimaryKey())
				|| !Objects.equals(source.getAncestorKeys().isEmpty() ? List.of()
						: source.getAncestorKeys().getFirst().getTargetForeignKey(),
						target.getTargetForeignKey())) {
			throw new CommandException("Load plan data set disagrees with its contract: "
					+ target.getId());
		}
		validateJoinKeys(source, target);
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

	private String expectedStagingTable(LegacyMigrationContract.DataSet source,
			String stagingTablePrefix) {
		if (stagingTablePrefix != null) {
			return stagingTablePrefix + source.getTargetTable();
		}
		if (hasText(source.getStagingTable())) {
			return source.getStagingTable();
		}
		return "TMP_" + source.getTargetTable();
	}

	private void validateJoinKeys(LegacyMigrationContract.DataSet source,
			LegacyMigrationLoadPlan.LoadDataSet target) {
		var expected = source.getAncestorKeys().isEmpty() ? List
				.<LegacyMigrationContract.KeyColumn>of()
				: source.getAncestorKeys().getFirst().getColumns();
		if (expected.size() != target.getParentJoinKeys().size()) {
			throw new CommandException("Load plan parent join keys disagree with its contract: "
					+ target.getId());
		}
		for (int i = 0; i < expected.size(); i++) {
			var contractKey = expected.get(i);
			var planKey = target.getParentJoinKeys().get(i);
			if (!Objects.equals(contractKey.getAncestorColumn(), planKey.getParentStagingColumn())
					|| !Objects.equals(contractKey.getSourceColumn(), planKey.getChildStagingColumn())) {
				throw new CommandException("Load plan parent join key disagrees with its contract: "
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
		boolean hasFile = hasText(plan.getViewpointsFile());
		boolean hasFingerprint = hasText(plan.getViewpointsFingerprint());
		if (!hasFile && !hasFingerprint) {
			return;
		}
		if (!hasFile || !hasFingerprint) {
			throw new CommandException(
					"Load plan viewpoint metadata requires both viewpointsFile and viewpointsFingerprint.");
		}
		File file = resolveReferencedFile(plan.getViewpointsFile());
		if (!file.isFile()) {
			throw new CommandException("Schema viewpoints file in load plan does not exist: " + file);
		}
		String fingerprint = new LegacyMigrationMappingValidator().fingerprint(file);
		if (!plan.getViewpointsFingerprint().equals(fingerprint)) {
			throw new CommandException("Schema viewpoints fingerprint does not match the load plan: " + file);
		}
	}

	private void validateViewpointResolution(LegacyMigrationLoadPlan plan,
			DbCommonObject<?> schema) {
		if (!hasText(plan.getViewpointId())) {
			return;
		}
		File viewpointsFile = resolveReferencedFile(plan.getViewpointsFile());
		var resolution = new SchemaViewpointCommandSupport().resolve(toCatalog(schema),
				viewpointsFile, plan.getViewpointId());
		var resolver = new SchemaViewpointResolver();
		List<String> resolvedTableIds = resolution.tables().stream()
				.map(resolver::qualifiedName).toList();
		if (!Objects.equals(plan.getResolvedTableIds(), resolvedTableIds)) {
			throw new CommandException(
					"Viewpoint resolvedTableIds disagree with current schema selection: "
							+ plan.getViewpointId());
		}
		Map<String, LegacyMigrationLoadPlan.LoadDataSet> byId = new LinkedHashMap<>();
		plan.getDataSets().forEach(dataSet -> byId.put(dataSet.getId(), dataSet));
		var selectedIds = new LinkedHashSet<String>();
		for (Table table : resolution.tables()) {
			List<LegacyMigrationLoadPlan.LoadDataSet> matches = plan.getDataSets().stream()
					.filter(dataSet -> matchesQualifier(dataSet.getTargetCatalog(), table.getCatalogName())
							&& equalsName(dataSet.getTargetSchema(), table.getSchemaName())
							&& equalsName(dataSet.getTargetTable(), table.getName()))
					.toList();
			if (matches.isEmpty()) {
				throw new CommandException("Viewpoint table has no selected migration data set: "
						+ resolver.qualifiedName(table));
			}
			matches.forEach(dataSet -> selectedIds.add(dataSet.getId()));
		}
		for (String id : new ArrayList<>(selectedIds)) {
			var current = byId.get(id);
			while (current != null && current.getParentDataSetId() != null) {
				current = byId.get(current.getParentDataSetId());
				if (current != null) {
					selectedIds.add(current.getId());
				}
			}
		}
		var planIds = new LinkedHashSet<>(plan.getResolvedDataSetIds());
		if (!selectedIds.equals(planIds)) {
			throw new CommandException(
					"Viewpoint data set selection disagrees with current schema selection: "
							+ plan.getViewpointId());
		}
	}

	private Catalog toCatalog(DbCommonObject<?> object) {
		if (object instanceof Catalog catalog) {
			return catalog;
		}
		if (object instanceof Schema schema) {
			return schema.toCatalog();
		}
		if (object instanceof SchemaCollection schemas) {
			return schemas.toCatalog();
		}
		throw new CommandException("Target schema XML cannot be resolved as a catalog.");
	}

	private boolean equalsName(String left, String right) {
		return left != null && right != null && left.equalsIgnoreCase(right);
	}

	private boolean matchesQualifier(String expected, String actual) {
		return !hasText(expected) || equalsName(expected, actual);
	}

	private DbCommonObject<?> readSchema(File file) {
		try {
			return SchemaUtils.readXml(file);
		} catch (Exception e) {
			throw new CommandException("Failed to read target schema XML: " + file, e);
		}
	}
}
