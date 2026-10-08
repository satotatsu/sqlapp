/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.work.DisableCachingByDefault;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import com.sqlapp.data.db.command.migration.assessment.VerifyDatabaseMigrationDdlPhasesCommand;

/** Verifies phase-separated database migration DDL without opening a database connection. */
@DisableCachingByDefault(because = "Command execution has not been validated for build caching")
public abstract class VerifyDatabaseMigrationDdlPhasesTask
		extends AbstractTask<VerifyDatabaseMigrationDdlPhasesCommand> {
	public VerifyDatabaseMigrationDdlPhasesTask() {
		getFailOnUnresolvedAutoNumberStrategies().convention(false);
		getFailOnIncompleteMapping().convention(false);
		getFailOnMappingSemanticDifferences().convention(false);
		getFailOnAssessmentBlockers().convention(false);
		getRequireDeploymentReady().convention(false);
		getRequireDataScan().convention(false);
		getRequireRelationshipsCollected().convention(false);
		getRequireApprovedFingerprints().convention(false);
	}

	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getDirectory();
	@org.gradle.api.tasks.InputFile @Optional
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getAssessmentReportFile();
	@OutputFile @Optional
	public abstract RegularFileProperty getVerificationReportFile();
	@Input @Optional
	public abstract Property<String> getExpectedManifestFingerprint();
	@Input @Optional
	public abstract Property<String> getExpectedAssessmentReportFingerprint();
	@Input @Optional
	public abstract Property<String> getExpectedSourceFingerprint();
	@Input @Optional
	public abstract Property<String> getExpectedMappingFingerprint();
	@Input @Optional
	public abstract Property<String> getExpectedTargetDatabase();
	@Input @Optional
	public abstract Property<String> getExpectedTargetVersion();
	@Input
	public abstract Property<Boolean> getFailOnUnresolvedAutoNumberStrategies();
	@Input
	public abstract Property<Boolean> getFailOnIncompleteMapping();
	@Input
	public abstract Property<Boolean> getFailOnMappingSemanticDifferences();
	@Input
	public abstract Property<Boolean> getFailOnAssessmentBlockers();
	@Input
	public abstract Property<Boolean> getRequireDeploymentReady();
	@Input
	public abstract Property<Boolean> getRequireDataScan();
	@Input
	public abstract Property<Boolean> getRequireRelationshipsCollected();
	@Input
	public abstract Property<Boolean> getRequireApprovedFingerprints();

	@Override
	protected VerifyDatabaseMigrationDdlPhasesCommand createCommand() {
		return new VerifyDatabaseMigrationDdlPhasesCommand();
	}

	@Override
	protected void beforeRun(final VerifyDatabaseMigrationDdlPhasesCommand command) {
		command.setDirectory(getDirectory().get().getAsFile());
		if (getAssessmentReportFile().isPresent()) { command.setAssessmentReportFile(getAssessmentReportFile().get().getAsFile()); }
		if (getVerificationReportFile().isPresent()) { command.setVerificationReportFile(getVerificationReportFile().get().getAsFile()); }
		if (getExpectedManifestFingerprint().isPresent()) {
			command.setExpectedManifestFingerprint(getExpectedManifestFingerprint().get());
		}
		if (getExpectedAssessmentReportFingerprint().isPresent()) {
			command.setExpectedAssessmentReportFingerprint(getExpectedAssessmentReportFingerprint().get());
		}
		if (getExpectedSourceFingerprint().isPresent()) { command.setExpectedSourceFingerprint(getExpectedSourceFingerprint().get()); }
		if (getExpectedMappingFingerprint().isPresent()) { command.setExpectedMappingFingerprint(getExpectedMappingFingerprint().get()); }
		if (getExpectedTargetDatabase().isPresent()) { command.setExpectedTargetDatabase(getExpectedTargetDatabase().get()); }
		if (getExpectedTargetVersion().isPresent()) { command.setExpectedTargetVersion(getExpectedTargetVersion().get()); }
		command.setFailOnUnresolvedAutoNumberStrategies(getFailOnUnresolvedAutoNumberStrategies().get());
		command.setFailOnIncompleteMapping(getFailOnIncompleteMapping().get());
		command.setFailOnMappingSemanticDifferences(getFailOnMappingSemanticDifferences().get());
		command.setFailOnAssessmentBlockers(getFailOnAssessmentBlockers().get());
		command.setRequireDeploymentReady(getRequireDeploymentReady().get());
		command.setRequireDataScan(getRequireDataScan().get());
		command.setRequireRelationshipsCollected(getRequireRelationshipsCollected().get());
		command.setRequireApprovedFingerprints(getRequireApprovedFingerprints().get());
	}
}
