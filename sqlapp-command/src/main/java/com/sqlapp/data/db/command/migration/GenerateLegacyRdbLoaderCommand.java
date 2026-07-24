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

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.SchemaUtils;
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

	private String runnerClassName = "LegacyMigrationLoader";

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
		if (runnerClassName == null || !runnerClassName.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
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
		String baseName = baseName(contractFile.getName());
		String ddl = generator.stagingDdl(plan, resolveDialect());
		String importConfiguration = generator.importConfiguration(plan, contract);
		String runner = generator.runnerTemplate(plan, runnerClassName);
		new LegacyMigrationLoadPlanIO().write(new File(outputDirectory, baseName + "-load-plan.yaml"), plan);
		write(new File(outputDirectory, baseName + "-staging.sql"), ddl);
		write(new File(outputDirectory, baseName + "-csv-import.yaml"), importConfiguration);
		write(new File(outputDirectory, runnerClassName + ".java.template"), runner);
		info("Legacy RDB loader artifacts: ", outputDirectory.getAbsolutePath());
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
