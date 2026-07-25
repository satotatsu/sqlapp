/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.viewpoint.SchemaViewpointCommandSupport;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlan;
import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlan.LoadDataSet;
import com.sqlapp.data.schemas.viewpoint.SchemaViewpointResolver;
import com.sqlapp.exceptions.CommandException;

import lombok.Getter;
import lombok.Setter;

/**
 * Generates persistent staging DDL, CSV import settings, a load plan and a
 * JdbcTreeDataSession runner template.
 */
@Getter
@Setter
public class GenerateLegacyRdbLoaderCommand extends AbstractCommand {

	private File contractFile;

	private File schemaFile;

	private File outputDirectory = new File("./");

	private String tableOperationMode = "INSERT_IGNORE";

	private int rootBatchSize = 500;

	private long commitEveryRootBatches = 500;

	private boolean deleteCommittedRoots = true;

	private String stagingTablePrefix = "TMP_";

	private String rootCursorStrategy = "DIALECT";

	private String databaseProductName;

	private int databaseProductMajorVersion;

	private int databaseProductMinorVersion;

	private boolean generateRunnerTemplate;

	private String runnerClassName = "LegacyMigrationLoader";

	private File viewpointsFile;

	private String viewpointId;

	private boolean includeViewpointAncestors = true;

	@Override
	protected void doRun() {
		if (contractFile == null || !contractFile.isFile()) {
			throw new CommandException("Migration contract file does not exist: " + contractFile);
		}
		if (schemaFile == null || !schemaFile.isFile()) {
			throw new CommandException("Target schema XML file does not exist: " + schemaFile);
		}
		if (outputDirectory == null) {
			throw new CommandException("Output directory is required.");
		}
		if (generateRunnerTemplate
				&& (runnerClassName == null || !runnerClassName.matches("[A-Za-z_$][A-Za-z0-9_$]*"))) {
			throw new CommandException("Invalid Java runner class name: " + runnerClassName);
		}
		if (stagingTablePrefix == null) {
			throw new CommandException("stagingTablePrefix is required.");
		}
		String mode = tableOperationMode == null ? null : tableOperationMode.toUpperCase(Locale.ROOT);
		String cursorStrategy = rootCursorStrategy == null ? null
				: rootCursorStrategy.toUpperCase(Locale.ROOT);
		var contract = new LegacyMigrationContractIO().read(contractFile);
		var generator = new LegacyRdbLoaderGenerator();
		var plan = generator.plan(contractFile, schemaFile, contract, mode, rootBatchSize,
				commitEveryRootBatches, deleteCommittedRoots, stagingTablePrefix, cursorStrategy);
		if (viewpointsFile != null || viewpointId != null) {
			applyViewpoint(plan);
		}
		String baseName = baseName(contractFile.getName());
		String ddl = generator.stagingDdl(plan, resolveDialect());
		String importConfiguration = generator.importConfiguration(plan, contract);
		new LegacyMigrationLoadPlanIO().write(new File(outputDirectory, baseName + "-load-plan.yaml"), plan);
		write(new File(outputDirectory, baseName + "-staging.sql"), ddl);
		write(new File(outputDirectory, baseName + "-csv-import.yaml"), importConfiguration);
		if (generateRunnerTemplate) {
			write(new File(outputDirectory, runnerClassName + ".java.template"),
					generator.runnerTemplate(plan, runnerClassName));
		}
		info("Legacy RDB loader artifacts: ", outputDirectory.getAbsolutePath());
	}

	private void applyViewpoint(LegacyMigrationLoadPlan plan) {
		Catalog catalog = readCatalog();
		var resolution = new SchemaViewpointCommandSupport().resolve(catalog, viewpointsFile,
				viewpointId);
		Set<String> selectedIds = new LinkedHashSet<>();
		for (Table table : resolution.tables()) {
			List<LoadDataSet> matches = plan.getDataSets().stream()
					.filter(dataSet -> equalsName(dataSet.getTargetSchema(), table.getSchemaName())
							&& equalsName(dataSet.getTargetTable(), table.getName()))
					.toList();
			if (matches.isEmpty()) {
				throw new CommandException("Viewpoint table has no migration data set: "
						+ new SchemaViewpointResolver().qualifiedName(table));
			}
			matches.forEach(dataSet -> selectedIds.add(dataSet.getId()));
		}
		Map<String, LoadDataSet> byId = new LinkedHashMap<>();
		plan.getDataSets().forEach(dataSet -> byId.put(dataSet.getId(), dataSet));
		for (String id : new ArrayList<>(selectedIds)) {
			LoadDataSet current = byId.get(id);
			while (current != null && current.getParentDataSetId() != null) {
				LoadDataSet parent = byId.get(current.getParentDataSetId());
				if (parent == null) {
					throw new CommandException("Unknown parent data set: " + current.getParentDataSetId());
				}
				if (!selectedIds.contains(parent.getId())) {
					if (!includeViewpointAncestors) {
						throw new CommandException("Viewpoint selection omits required ancestor data set '"
								+ parent.getId() + "' for '" + current.getId() + "'.");
					}
					selectedIds.add(parent.getId());
				}
				current = parent;
			}
		}
		plan.getDataSets().removeIf(dataSet -> !selectedIds.contains(dataSet.getId()));
		plan.setViewpointId(resolution.viewpoint().getId());
		plan.setViewpointsFile(viewpointsFile.getAbsolutePath());
		plan.setViewpointsFingerprint(new LegacyMigrationMappingValidator().fingerprint(viewpointsFile));
		var resolver = new SchemaViewpointResolver();
		plan.setResolvedTableIds(resolution.tables().stream().map(resolver::qualifiedName).toList());
		plan.setResolvedDataSetIds(plan.getDataSets().stream().map(LoadDataSet::getId).toList());
	}

	private Catalog readCatalog() {
		try {
			DbCommonObject<?> object = SchemaUtils.readXml(schemaFile);
			if (object instanceof Catalog catalog) {
				return catalog;
			}
			if (object instanceof Schema schema) {
				return schema.toCatalog();
			}
			if (object instanceof com.sqlapp.data.schemas.SchemaCollection schemas) {
				return schemas.toCatalog();
			}
			throw new CommandException("Target schema XML must contain Catalog, SchemaCollection, or Schema.");
		} catch (CommandException e) {
			throw e;
		} catch (Exception e) {
			throw new CommandException("Failed to read target schema XML: " + schemaFile, e);
		}
	}

	private boolean equalsName(String left, String right) {
		return left != null && right != null && left.equalsIgnoreCase(right);
	}

	private Dialect resolveDialect() {
		if (databaseProductName != null && !databaseProductName.isBlank()) {
			return DialectResolver.getInstance().getDialect(databaseProductName,
					databaseProductMajorVersion, databaseProductMinorVersion, null);
		}
		try {
			DbCommonObject<?> schema = SchemaUtils.readXml(schemaFile);
			Dialect dialect = SchemaUtils.getDialect(schema);
			return dialect == null ? DialectResolver.getInstance().getDefaultDialect() : dialect;
		} catch (Exception e) {
			throw new CommandException("Failed to resolve Dialect from target schema XML: " + schemaFile, e);
		}
	}

	private String baseName(String name) {
		String suffix = "-contract.yaml";
		if (name.endsWith(suffix)) {
			return name.substring(0, name.length() - suffix.length());
		}
		int index = name.lastIndexOf('.');
		return index < 0 ? name : name.substring(0, index);
	}

	private void write(File file, String value) {
		File directory = file.getAbsoluteFile().getParentFile();
		if (directory != null && !directory.exists() && !directory.mkdirs()) {
			throw new CommandException("Failed to create RDB loader output directory: " + directory);
		}
		File temporary = new File(directory, file.getName() + ".tmp");
		try {
			Files.writeString(temporary.toPath(), value, StandardCharsets.UTF_8);
			Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new CommandException("Failed to write RDB loader artifact: " + file, e);
		} finally {
			if (temporary.exists()) {
				temporary.delete();
			}
		}
	}
}
