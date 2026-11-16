/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration.model;

import java.io.File;
import java.util.List;
import java.util.Set;

import com.sqlapp.data.db.command.AbstractDataSourceCommand;
import com.sqlapp.data.db.command.migration.verification.MigrationNodeManifestIO;
import com.sqlapp.data.db.command.migration.verification.MigrationTransformationTestResult;
import com.sqlapp.data.schemas.migration.MigrationModel;
import com.sqlapp.exceptions.CommandException;

import lombok.Getter;
import lombok.Setter;

/**
 * Runs the unit tests of YAML-defined models and then builds the models in
 * dependency order inside the connected engine.
 */
@Getter
@Setter
public class ExecuteMigrationModelsCommand extends AbstractDataSourceCommand {
	/** Model YAML. */
	private File configurationFile;
	/**
	 * Optional state artifact. It is written after every model was built and is the
	 * comparison baseline of {@link #modifiedOnly}.
	 */
	private File stateFile;
	/**
	 * Build only models changed since {@link #stateFile} and their downstream
	 * models.
	 */
	private boolean modifiedOnly;
	/** Run unit tests before building; a failed test prevents every build. */
	private boolean unitTestsEnabled = true;
	/** Run unit tests only and build nothing. */
	private boolean testOnly;

	private List<MigrationTransformationTestResult> unitTestResults = List.of();
	private MigrationModelRunner.Result result;

	@Override
	protected void doRun() {
		unitTestResults = List.of();
		result = null;
		if (getDataSource() == null) {
			throw new CommandException("Migration model data source is required.");
		}
		if (testOnly && !unitTestsEnabled) {
			throw new CommandException("testOnly requires unitTestsEnabled.");
		}
		final var resolved = new MigrationModelConfigurationResolver().resolve(configurationFile);
		final var plan = resolved.plan();
		final List<MigrationModel> selected;
		if (modifiedOnly && !testOnly) {
			if (stateFile == null || !stateFile.isFile()) {
				throw new CommandException("modifiedOnly requires an existing stateFile"
						+ (stateFile == null ? "." : ": " + stateFile.getAbsolutePath())
						+ " Run once without modifiedOnly to create it.");
			}
			final Set<String> names = plan
					.modifiedAndDownstream(new MigrationNodeManifestIO().read(stateFile.toPath()));
			selected = plan.select(names);
			info("Modified and downstream migration models: ", selected.stream().map(MigrationModel::name).toList());
		} else {
			selected = plan.models();
		}
		executeNoTranAndClose(getDataSource(), connection -> {
			if (unitTestsEnabled) {
				try {
					unitTestResults = MigrationModelRunner.test(connection, plan, resolved.unitTests());
				} catch (IllegalArgumentException e) {
					throw new CommandException("Invalid migration model unit test: " + e.getMessage(), e);
				}
				final var mismatches = unitTestResults.stream().filter(test -> !test.match()).toList();
				if (!mismatches.isEmpty()) {
					final StringBuilder message = new StringBuilder("Migration model unit tests failed:");
					for (final var mismatch : mismatches) {
						message.append(System.lineSeparator()).append("  ").append(mismatch.id()).append(": expected ")
								.append(mismatch.expectedRows()).append(" but was ").append(mismatch.actualRows());
					}
					throw new CommandException(message.toString());
				}
				info("Migration model unit tests passed: ", unitTestResults.size());
			}
			if (testOnly) {
				return;
			}
			result = MigrationModelRunner.run(connection, plan, selected);
			for (final var model : result.models()) {
				info("Migration model ", model.name(), " [", model.materialization(), "] ", model.status(), " ",
						model.elapsedMillis(), "ms", model.message() == null ? "" : " " + model.message());
			}
			if (!result.isSuccess()) {
				final StringBuilder message = new StringBuilder("Migration models failed:");
				for (final var failure : result.failures()) {
					message.append(System.lineSeparator()).append("  ").append(failure.name()).append(": ")
							.append(failure.message());
				}
				throw new CommandException(message.toString());
			}
		});
		if (!testOnly && stateFile != null && result != null && result.isSuccess()) {
			new MigrationNodeManifestIO().write(stateFile.toPath(), plan.nodeManifest());
			info("Migration model state: ", stateFile.getAbsolutePath());
		}
	}
}
