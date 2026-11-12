/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import java.io.File;
import java.util.List;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.migration.transfer.CompareTransferStagingCommand;

/** Read-only comparison of retained staging and a live target. */
@DisableCachingByDefault(because = "Reads the current state of a live database")
public abstract class CompareTransferStagingTask extends AbstractDbTask<CompareTransferStagingCommand> {
	public CompareTransferStagingTask() {
		getWorkspaceDirectory().convention(getProject().getLayout().getBuildDirectory().dir("sqlapp-transfer/compare"));
		getFailOnDifference().convention(false);
		getOutputs().upToDateWhen(task -> false);
	}

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getResultFile();

	@InputFiles
	@PathSensitive(PathSensitivity.RELATIVE)
	public Provider<List<File>> getCompanionFiles() {
		return getResultFile().map(result -> {
			var directory = result.getAsFile().getParentFile();
			if (result.getAsFile().getName().equals("import-result.json")) {
				var members = directory.listFiles(file -> file.isFile() && !file.equals(result.getAsFile()));
				return members == null ? List.of() : List.of(members);
			}
			return List.of("transfer-review.json", "transfer.yaml", "data-job.yaml", "source-schema.xml")
					.stream().map(name -> new File(directory, name)).toList();
		});
	}

	@OutputDirectory
	public abstract DirectoryProperty getWorkspaceDirectory();

	@Input
	public abstract Property<Boolean> getFailOnDifference();

	@Override
	protected void initializeCommand(CompareTransferStagingCommand command) {
		command.setResultFile(getResultFile().get().getAsFile());
		command.setWorkspaceDirectory(getWorkspaceDirectory().get().getAsFile());
		command.setFailOnDifference(getFailOnDifference().get());
		command.validateInputs();
		super.initializeCommand(command);
	}

	@Override
	protected CompareTransferStagingCommand createCommand() {
		return new CompareTransferStagingCommand();
	}
}
