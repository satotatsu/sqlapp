/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.migration.pipeline.RunMigrationPipelineCommand;
import com.sqlapp.gradle.plugins.extension.DataSourceExtension;

/**
 * Loads the engine, builds the models and publishes them to the target as one
 * job. {@code dataSource} is the engine. Without {@code allowExecution} the task
 * only reports the fingerprint to approve.
 */
@DisableCachingByDefault(because = "Inspects live databases and may execute transfers and model builds")
public abstract class RunMigrationPipelineTask extends AbstractDbTask<RunMigrationPipelineCommand> {
	public RunMigrationPipelineTask() {
		setSourceDataSource(getProject().getObjects().newInstance(DataSourceExtension.class));
		setTargetDataSource(getProject().getObjects().newInstance(DataSourceExtension.class));
		getAllowExecution().convention(false);
		getWorkspaceDirectory()
				.convention(getProject().getLayout().getBuildDirectory().dir("sqlapp-migration/pipeline"));
		getOutputs().upToDateWhen(task -> false);
	}

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getConfigurationFile();

	@Input
	@Optional
	public abstract Property<String> getExpectedConfigurationFingerprint();

	@Input
	public abstract Property<Boolean> getAllowExecution();

	/** One directory per run, holding the run report and the transfer reports. */
	@OutputDirectory
	public abstract DirectoryProperty getWorkspaceDirectory();

	/** Database read by the load job; required when the pipeline has one. */
	@Nested
	public abstract DataSourceExtension getSourceDataSource();

	public abstract void setSourceDataSource(DataSourceExtension value);

	public void sourceDataSource(final Action<DataSourceExtension> action) {
		action.execute(getSourceDataSource());
	}

	/** Database written by the publish job. */
	@Nested
	public abstract DataSourceExtension getTargetDataSource();

	public abstract void setTargetDataSource(DataSourceExtension value);

	public void targetDataSource(final Action<DataSourceExtension> action) {
		action.execute(getTargetDataSource());
	}

	private void inputs(final RunMigrationPipelineCommand command) {
		command.setConfigurationFile(getConfigurationFile().get().getAsFile());
		command.setWorkspaceDirectory(getWorkspaceDirectory().get().getAsFile());
		command.setAllowExecution(getAllowExecution().get());
		command.setExpectedConfigurationFingerprint(getExpectedConfigurationFingerprint().getOrNull());
	}

	@Override
	protected void initializeCommand(final RunMigrationPipelineCommand command) {
		inputs(command);
		try {
			command.validateInputs();
		} catch (final java.io.IOException failure) {
			throw new GradleException("Invalid migration pipeline definition.", failure);
		}
		super.initializeCommand(command);
	}

	@Override
	protected void beforeRun(final RunMigrationPipelineCommand command) {
		inputs(command);
		if (!getAllowExecution().get()) {
			return;
		}
		try {
			if (getSourceDataSource().isConfigured()) {
				command.setSourceDataSource(getSourceDataSource().createDataSource());
			}
			command.setTargetDataSource(getTargetDataSource().createDataSource());
		} catch (final RuntimeException failure) {
			// Only the failure type: the message may contain connection details.
			throw new IllegalArgumentException("Unable to configure the source or target connection ("
					+ failure.getClass().getName() + ").");
		}
	}

	@Override
	protected RunMigrationPipelineCommand createCommand() {
		return new RunMigrationPipelineCommand();
	}
}
