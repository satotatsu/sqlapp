/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-gradle-plugin.
 *
 * sqlapp-gradle-plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-gradle-plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-gradle-plugin.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.gradle.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;

import com.sqlapp.gradle.plugins.extension.MigrationExtension;

public class DbPlugin implements Plugin<Project> {

	/** Task group names shown by {@code gradle tasks}. */
	public static final String GROUP_SCHEMA = "sqlapp schema";
	public static final String GROUP_SCHEMA_TRANSFORMATION = "sqlapp schema transformation";
	public static final String GROUP_MIGRATION = "sqlapp migration";
	public static final String GROUP_MIGRATION_ASSESSMENT = "sqlapp migration assessment";
	public static final String GROUP_BULK_MIGRATION = "sqlapp bulk migration";
	public static final String GROUP_MIGRATION_SNAPSHOT = "sqlapp migration snapshot";
	public static final String GROUP_TRANSFER = "sqlapp transfer";
	public static final String GROUP_LEGACY_MIGRATION = "sqlapp legacy migration";

	@Override
	public void apply(Project project) {
		// sqlapp schema
		registerTask(project, "countAllTables", CountAllTableTask.class, GROUP_SCHEMA,
				"Counts rows of the selected tables.");
		registerTask(project, "exportSchemaXml", ExportSchemaXmlTask.class, GROUP_SCHEMA,
				"Exports a database schema to Schema XML.");
		registerTask(project, "exportAccessSchemaXml", ExportAccessSchemaXmlTask.class, GROUP_SCHEMA,
				"Exports an Access database file to Schema XML.");
		registerTask(project, "exportSqliteSchemaXml", ExportSqliteSchemaXmlTask.class, GROUP_SCHEMA,
				"Exports a SQLite database file to Schema XML.");
		registerTask(project, "diffSchemaXml", DiffSchemaXmlTask.class, GROUP_SCHEMA,
				"Logs the differences between two Schema XML files.");
		registerTask(project, "generateDiffSql", GenerateDiffSqlTask.class, GROUP_SCHEMA,
				"Generates SQL for the differences between two Schema XML files.");
		registerTask(project, "generateSql", GenerateSqlTask.class, GROUP_SCHEMA, "Generates SQL from Schema XML.");
		registerTask(project, "generateHtmlDocs", GenerateHtmlDocsTask.class, GROUP_SCHEMA,
				"Generates HTML documentation and ER diagrams from Schema XML.");
		// sqlapp schema transformation
		registerTask(project, "firstNormalForm", FirstNormalFormTask.class, GROUP_SCHEMA_TRANSFORMATION,
				"Transforms Schema XML into first normal form.");
		registerTask(project, "generateNormalizationPlan", GenerateNormalizationPlanTask.class, GROUP_SCHEMA_TRANSFORMATION,
				"Generates a normalization plan for Schema XML.");
		registerTask(project, "columnRuleTransform", ColumnRuleTransformTask.class, GROUP_SCHEMA_TRANSFORMATION,
				"Applies column rules to Schema XML.");
		// sqlapp migration
		registerTaskWithExtensions(project, "migration", MigrationExtension.class, MigrationTask.class, GROUP_MIGRATION,
				"Applies pending versioned SQL migrations.");
		registerTask(project, "migrationInsert", MigrationInsertTask.class, GROUP_MIGRATION,
				"Inserts migration history entries.");
		registerTask(project, "migrationRepair", MigrationRepairTask.class, GROUP_MIGRATION,
				"Repairs migration history entries.");
		registerTask(project, "migrationValidate", MigrationValidateTask.class, GROUP_MIGRATION,
				"Validates the checksums of applied migrations.");
		registerTask(project, "migrationPlan", MigrationPlanTask.class, GROUP_MIGRATION,
				"Lists pending migrations and blockers without applying them.");
		registerTask(project, "migrationEnvironmentSnapshot", MigrationEnvironmentSnapshotTask.class, GROUP_MIGRATION,
				"Writes a snapshot of the migration state of an environment.");
		registerTask(project, "compareMigrationEnvironments", CompareMigrationEnvironmentsTask.class, GROUP_MIGRATION,
				"Compares migration snapshots of several environments.");
		registerTask(project, "verifyMigrationExecutionReport", VerifyMigrationExecutionReportTask.class, GROUP_MIGRATION,
				"Verifies a migration execution report.");
		// sqlapp migration assessment
		registerTask(project, "assessMigration", AssessMigrationTask.class, GROUP_MIGRATION_ASSESSMENT,
				"Assesses a schema for migration to Oracle.");
		registerTask(project, "assessDatabaseMigration", AssessDatabaseMigrationTask.class, GROUP_MIGRATION_ASSESSMENT,
				"Assesses an Access database for migration to Oracle or SQL Server.");
		registerTask(project, "assessAccessOracleMigration", AssessAccessOracleMigrationTask.class, GROUP_MIGRATION_ASSESSMENT,
				"Assesses an Access database for migration to Oracle (alias of assessDatabaseMigration).");
		registerTask(project, "verifyDatabaseMigrationDdlPhases", VerifyDatabaseMigrationDdlPhasesTask.class, GROUP_MIGRATION_ASSESSMENT,
				"Verifies the phase-separated DDL written by assessDatabaseMigration.");
		// sqlapp bulk migration
		registerTask(project, "generateAccessBulkMigrationJobConfiguration", GenerateAccessBulkMigrationJobConfigurationTask.class, GROUP_BULK_MIGRATION,
				"Generates a bulk migration job configuration from an Access assessment.");
		registerTask(project, "validateBulkMigrationTarget", ValidateBulkMigrationTargetTask.class, GROUP_BULK_MIGRATION,
				"Validates the target database before a bulk migration.");
		registerTask(project, "executeBulkMigrationJob", ExecuteBulkMigrationJobTask.class, GROUP_BULK_MIGRATION,
				"Runs a bulk migration job.");
		registerTask(project, "generateBulkMigrationOperationalReport", GenerateBulkMigrationOperationalReportTask.class, GROUP_BULK_MIGRATION,
				"Generates a bulk migration operational report.");
		registerTask(project, "verifyBulkMigrationEvidence", VerifyBulkMigrationEvidenceTask.class, GROUP_BULK_MIGRATION,
				"Verifies bulk migration reports and writes audit evidence.");
		registerTask(project, "verifyBulkMigrationEvidenceReport", VerifyBulkMigrationEvidenceReportTask.class, GROUP_BULK_MIGRATION,
				"Re-verifies saved bulk migration audit evidence.");
		registerTask(project, "generateBulkMigrationJobRepairPlanReport", GenerateBulkMigrationJobRepairPlanReportTask.class, GROUP_BULK_MIGRATION,
				"Generates a bulk migration repair plan for review.");
		registerTask(project, "executeBulkMigrationJobRepair", ExecuteBulkMigrationJobRepairTask.class, GROUP_BULK_MIGRATION,
				"Runs an approved bulk migration repair plan.");
		registerTask(project, "verifyBulkMigrationJobRepairEvidence", VerifyBulkMigrationJobRepairEvidenceTask.class, GROUP_BULK_MIGRATION,
				"Verifies the evidence of a successful bulk migration repair.");
		registerTask(project, "verifyBulkMigrationJobRepairFailureEvidence", VerifyBulkMigrationJobRepairFailureEvidenceTask.class, GROUP_BULK_MIGRATION,
				"Verifies the evidence of a failed bulk migration repair.");
		registerTask(project, "verifyBulkMigrationJobRepairOutcome", VerifyBulkMigrationJobRepairOutcomeTask.class, GROUP_BULK_MIGRATION,
				"Determines and verifies the outcome of a bulk migration repair.");
		registerTask(project, "verifyBulkMigrationJobRepairOutcomeReport", VerifyBulkMigrationJobRepairOutcomeReportTask.class, GROUP_BULK_MIGRATION,
				"Re-verifies a saved bulk migration repair outcome report.");
		// sqlapp migration snapshot
		registerTask(project, "generateMigrationSnapshotApprovalReport", GenerateMigrationSnapshotApprovalReportTask.class, GROUP_MIGRATION_SNAPSHOT,
				"Generates an approval report for a migration snapshot.");
		registerTask(project, "executeMigrationSnapshot", ExecuteMigrationSnapshotTask.class, GROUP_MIGRATION_SNAPSHOT,
				"Runs an approved migration snapshot.");
		registerTask(project, "verifyMigrationSnapshotReport", VerifyMigrationSnapshotReportTask.class, GROUP_MIGRATION_SNAPSHOT,
				"Verifies a migration snapshot report.");
		registerTask(project, "verifyMigrationSnapshotFailureReport", VerifyMigrationSnapshotFailureReportTask.class, GROUP_MIGRATION_SNAPSHOT,
				"Verifies a migration snapshot failure report.");
		// sqlapp transfer
		registerTask(project, "transferDatabase", TransferDatabaseTask.class, GROUP_TRANSFER,
				"Reviews or runs a direct database-to-database transfer.");
		registerTask(project, "exportTransferFile", ExportTransferFileTask.class, GROUP_TRANSFER,
				"Exports source data to a transfer file package.");
		registerTask(project, "importTransferFile", ImportTransferFileTask.class, GROUP_TRANSFER,
				"Imports a transfer file package into the target database.");
		registerTask(project, "compareTransferStaging", CompareTransferStagingTask.class, GROUP_TRANSFER,
				"Compares retained staging data with the target database.");
		// sqlapp legacy migration
		registerTask(project, "pliSchemaImport", PliSchemaImportTask.class, GROUP_LEGACY_MIGRATION,
				"Imports PL/I record definitions as Schema XML.");
		registerTask(project, "generateLegacyMigrationContract", GenerateLegacyMigrationContractTask.class, GROUP_LEGACY_MIGRATION,
				"Generates an extraction and load contract from a mapping.");
		registerTask(project, "generatePliCsvExtractor", GeneratePliCsvExtractorTask.class, GROUP_LEGACY_MIGRATION,
				"Generates PL/I CSV extraction artifacts.");
		registerTask(project, "generateLegacyRdbLoader", GenerateLegacyRdbLoaderTask.class, GROUP_LEGACY_MIGRATION,
				"Generates a load plan for a relational database.");
		registerTask(project, "loadLegacyHierarchy", LoadLegacyHierarchyTask.class, GROUP_LEGACY_MIGRATION,
				"Loads hierarchical legacy data according to a load plan.");
	}

	protected void registerTaskWithExtensions(Project project, String name, Class<?> pojoClass,
			Class<? extends Task> taskClass) {
		registerTaskWithExtensions(project, name, pojoClass, taskClass, null, null);
	}

	protected void registerTaskWithExtensions(Project project, String name, Class<?> pojoClass,
			Class<? extends Task> taskClass, String group, String description) {
		createExtensions(project, name, pojoClass);
		registerTask(project, name, taskClass, group, description);
	}

	protected void registerTask(Project project, String name, Class<? extends Task> taskClass) {
		registerTask(project, name, taskClass, null, null);
	}

	protected void registerTask(Project project, String name, Class<? extends Task> taskClass, String group,
			String description) {
		project.getTasks().register(name, taskClass, task -> {
			if (group != null) {
				task.setGroup(group);
			}
			if (description != null) {
				task.setDescription(description);
			}
		});
	}

	protected void createExtensions(Project project, String name, Class<?> pojoClass) {
		// project.getExtensions().create(name, pojoClass, project);
		project.getExtensions().create(name, pojoClass);
	}
}
