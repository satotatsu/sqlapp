/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.logic;

import java.util.List;

import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.Eligibility;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.InputFile;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.ObjectIdentity;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.TableIdentity;

/**
 * File contracts read and written by
 * {@link ImportLogicMigrationResponseCommand} (milestone M2).
 * <p>
 * {@code response.json} is written by a converter. {@code import.json} is the
 * evidence written by sqlapp. An accepted candidate passed the structural
 * checks of this command only: it was not created in a database, reviewed or
 * verified.
 */
public final class LogicMigrationResponseModel {
	public static final String RESPONSE_FORMAT = "sqlapp-logic-response";
	public static final String IMPORT_FORMAT = "sqlapp-logic-import";
	public static final int FORMAT_VERSION = 1;

	private LogicMigrationResponseModel() {
	}

	/** Status stated by the converter. */
	public enum ResultStatus {
		CONVERTED, UNRESOLVED
	}

	public enum ArtifactKind {
		FUNCTION, TRIGGER
	}

	public enum SuggestionType {
		REPLACE_WITH_SCHEMA, NOT_NEEDED
	}

	public record ColumnIdentity(String catalog, String schema, String table, String name) {
	}

	public record Suggestion(SuggestionType type, String reason, ColumnIdentity targetColumn, List<String> checks) {
	}

	public record Result(String sourceId, ResultStatus status, List<String> artifactIds, List<String> notes,
			Suggestion suggestion) {
	}

	/** {@code table} is required for TRIGGER and absent for FUNCTION. */
	public record Artifact(String artifactId, ArtifactKind kind, String schema, String name, List<String> argumentTypes,
			TableIdentity table, String file, List<String> dependsOn) {
	}

	public record Response(String format, int formatVersion, String requestId, List<Result> results,
			List<Artifact> artifacts) {
	}

	/** Outcome of one source object after import. */
	public enum ImportStatus {
		/** Converted and passed the structural checks; still a candidate. */
		ACCEPTED_CANDIDATE,
		/** Converted, but a structural check failed. Not applicable. */
		REJECTED,
		/** The converter reported unresolved issues. Not applicable. */
		UNRESOLVED,
		/** Requested but not returned. Not applicable. */
		MISSING,
		/** Not requested; listed to keep the whole inventory. */
		NOT_REQUESTED
	}

	/**
	 * Declaration read from the outer CREATE statement. Body contents are not
	 * interpreted.
	 */
	public record ParsedDeclaration(boolean orReplace, String schema, String name, Integer argumentCount,
			String returns, String language, boolean securityDefiner, String timing, List<String> events,
			String orientation, TableIdentity table, String calledFunctionSchema, String calledFunctionName) {
	}

	public record ImportedArtifact(String artifactId, ArtifactKind kind, String schema, String name,
			TableIdentity table, List<String> dependsOn, InputFile file, ParsedDeclaration declaration,
			String generatedFingerprint, List<String> problems) {
	}

	public record ImportedObject(String sourceId, ObjectIdentity identity, Eligibility eligibility, ImportStatus status,
			ResultStatus converterStatus, List<String> notes, Suggestion suggestion, List<ImportedArtifact> artifacts,
			List<String> problems) {
	}

	public record Outputs(InputFile targetSchema, InputFile sql) {
	}

	public record Report(String format, int formatVersion, String requestId, InputFile requestManifest,
			InputFile response, List<ImportedObject> objects, List<String> applyOrder, Outputs outputs,
			List<LogicMigrationRequestModel.Diagnostic> diagnostics) {
	}
}
