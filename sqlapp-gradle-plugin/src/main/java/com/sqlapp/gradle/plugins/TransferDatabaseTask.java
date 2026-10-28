/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.Action;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.work.DisableCachingByDefault;
import com.sqlapp.data.db.command.migration.transfer.TransferDatabaseCommand;
import com.sqlapp.gradle.plugins.extension.DataSourceExtension;

/** Shared transfer definition, two runtime endpoints, read-only review by default. */
@DisableCachingByDefault(because = "Inspects live databases and may execute a transfer")
public abstract class TransferDatabaseTask extends AbstractDbTask<TransferDatabaseCommand> {
	public TransferDatabaseTask() {
		setSourceDataSource(getProject().getObjects().newInstance(DataSourceExtension.class));
		getAllowExecution().convention(false);
		getWorkspaceDirectory().convention(getProject().getLayout().getBuildDirectory().dir("sqlapp-transfer/direct"));
		getOutputs().upToDateWhen(task -> false);
	}
	@InputFile @PathSensitive(PathSensitivity.RELATIVE) public abstract RegularFileProperty getConfigurationFile();
	@Input @Optional public abstract Property<String> getExpectedConfigurationFingerprint();
	@Input public abstract Property<Boolean> getAllowExecution();
	@OutputDirectory public abstract DirectoryProperty getWorkspaceDirectory();
	@Nested public abstract DataSourceExtension getSourceDataSource();
	public abstract void setSourceDataSource(DataSourceExtension value);
	public void sourceDataSource(Action<DataSourceExtension> action) { action.execute(getSourceDataSource()); }
	private void inputs(TransferDatabaseCommand command) {
		command.setConfigurationFile(getConfigurationFile().get().getAsFile());
		command.setWorkspaceDirectory(getWorkspaceDirectory().get().getAsFile());
		command.setAllowExecution(getAllowExecution().get());
		command.setExpectedConfigurationFingerprint(getExpectedConfigurationFingerprint().getOrNull());
	}
	@Override protected void initializeCommand(TransferDatabaseCommand command) {
		inputs(command);
		try { command.validateInputs(); }
		catch (java.io.IOException failure) { throw new org.gradle.api.GradleException("Invalid direct transfer definition."); }
		super.initializeCommand(command);
	}
	@Override protected void beforeRun(TransferDatabaseCommand command) {
		inputs(command);
		try { command.setSourceDataSource(getSourceDataSource().createDataSource()); }
		catch (RuntimeException failure) {
			if (command.getDataSource() instanceof AutoCloseable closeable) {
				try { closeable.close(); } catch (Exception ignored) { throw new IllegalStateException("Source connection setup and target cleanup failed."); }
			}
			throw new IllegalArgumentException("Unable to configure the source connection.");
		}
	}
	@Override protected TransferDatabaseCommand createCommand() { return new TransferDatabaseCommand(); }
}
