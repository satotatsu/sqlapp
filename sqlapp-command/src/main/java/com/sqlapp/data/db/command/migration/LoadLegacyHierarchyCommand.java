/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.io.File;

import com.sqlapp.data.db.command.AbstractDataSourceCommand;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.SchemaUtils;
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
		DbCommonObject<?> schema = readSchema(targetSchemaFile);
		execute(getDataSource(), connection -> {
			long roots = new JdbcTreeStagingLoader(connection, schema, plan).load();
			info("Legacy hierarchy load completed. roots=", roots);
		});
	}

	private DbCommonObject<?> readSchema(File file) {
		try {
			return SchemaUtils.readXml(file);
		} catch (Exception e) {
			throw new CommandException("Failed to read target schema XML: " + file, e);
		}
	}
}
