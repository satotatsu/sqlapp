/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.work.DisableCachingByDefault;
import com.sqlapp.data.db.command.migration.transfer.ImportTransferFileCommand;

/** Explicit target-only CSV import. Indirect package members are validated by the command. */
@DisableCachingByDefault(because = "Executes mutations against an external database")
public abstract class ImportTransferFileTask extends AbstractDbTask<ImportTransferFileCommand> {
	public ImportTransferFileTask() {
		getAllowExecution().convention(false);
		getWorkspaceDirectory().convention(getProject().getLayout().getBuildDirectory().dir("sqlapp-transfer/import"));
		getOutputs().upToDateWhen(task -> false);
	}
	@InputFile @PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getManifestFile();
	@Input public abstract Property<String> getExpectedManifestFingerprint();
	@Input public abstract Property<Boolean> getAllowExecution();
	@OutputDirectory public abstract DirectoryProperty getWorkspaceDirectory();

	@Override protected void initializeCommand(ImportTransferFileCommand command) {
		command.setAllowExecution(getAllowExecution().get());
		command.setExpectedManifestFingerprint(getExpectedManifestFingerprint().getOrNull());
		command.validateAuthorization(); // Before AbstractTask constructs a DataSource/pool.
		beforeRun(command);
		try { command.validateInputs(); }
		catch (Exception failure) { throw new org.gradle.api.GradleException("Invalid CSV import package.", failure); }
		super.initializeCommand(command);
	}
	@Override protected void beforeRun(ImportTransferFileCommand command) {
		command.setManifestFile(getManifestFile().get().getAsFile());
		command.setExpectedManifestFingerprint(getExpectedManifestFingerprint().getOrNull());
		command.setAllowExecution(getAllowExecution().get());
		command.setWorkspaceDirectory(getWorkspaceDirectory().get().getAsFile());
	}
	@Override protected ImportTransferFileCommand createCommand() { return new ImportTransferFileCommand(); }
}
