/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.logic;

import java.util.List;
import java.util.Objects;

/**
 * File contracts written by {@link GenerateLogicMigrationRequestCommand}.
 * <p>
 * The records declare the JSON field order. They describe a conversion request
 * only; none of the values mean that a conversion was produced, reviewed,
 * applied or verified.
 */
public final class LogicMigrationRequestModel {
	public static final String REQUEST_FORMAT = "sqlapp-logic-request";
	public static final String CHANGES_FORMAT = "sqlapp-logic-changes";
	public static final String STATE_FORMAT = "sqlapp-logic-request-state";
	public static final int FORMAT_VERSION = 1;

	private LogicMigrationRequestModel() {
	}

	/** Whether the definition of a source object could be read. */
	public enum Acquisition {
		AVAILABLE, MISSING_DEFINITION
	}

	/** Whether a source object is handed to the converter. */
	public enum Eligibility {
		/** Sent to the converter. */
		REQUESTED,
		/** Excluded by an explicit user decision. */
		EXCLUDED,
		/** A recognised form that this version does not convert. */
		UNSUPPORTED_FORM,
		/** The form or definition could not be classified safely. */
		UNKNOWN_FORM,
		/** The table that the trigger belongs to cannot be resolved uniquely. */
		TARGET_TABLE_UNRESOLVED
	}

	/** How a target table was matched to a source table. */
	public enum TableResolution {
		/** Same schema and table name. */
		EXACT,
		/** Same schema and table name ignoring case. */
		IGNORE_CASE,
		/**
		 * The only target table with the same name ignoring case, in another schema.
		 */
		TABLE_NAME_ONLY
	}

	public record TableIdentity(String catalog, String schema, String name) {
		public TableIdentity {
			Objects.requireNonNull(name, "name");
		}
	}

	/**
	 * Exact identity of a source trigger. Names keep the spelling of the Schema
	 * model.
	 */
	public record ObjectIdentity(String kind, String catalog, String schema, String name, TableIdentity table) {
		public ObjectIdentity {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(name, "name");
		}
	}

	/** A user decision not to convert one source object. */
	public record Exclusion(ObjectIdentity identity, String reason) {
	}

	public record Database(String product, String version) {
	}

	public record InputFile(String path, String fingerprint) {
	}

	public record Inputs(InputFile sourceSchema, InputFile targetSchema, InputFile changes, InputFile prompt) {
	}

	public record ObjectEntry(String sourceId, ObjectIdentity identity, TableIdentity targetTable,
			TableResolution targetTableResolution, Acquisition acquisition, String definitionFingerprint,
			Eligibility eligibility, String reason, String timing, String orientation, List<String> events,
			boolean enabled) {
	}

	public record Diagnostic(String level, String code, String message) {
	}

	public record Manifest(String format, int formatVersion, String requestId, Database sourceDatabase,
			Database targetDatabase, String verificationProfile, Inputs inputs, List<ObjectEntry> objects,
			List<Diagnostic> diagnostics) {
	}

	public record ColumnInfo(String name, String dataType, String dataTypeName, Long length, Integer scale,
			boolean notNull) {
	}

	/** How a target column was matched to a source column. */
	public enum ColumnResolution {
		/** Same name. */
		EXACT,
		/** Same name ignoring case. */
		IGNORE_CASE,
		/** No target column has the same name ignoring case. */
		UNMATCHED,
		/**
		 * Several target candidates, or the target column is also matched by another
		 * source column. No correspondence is asserted.
		 */
		UNRESOLVED
	}

	/**
	 * One source column. {@code target} is set only for EXACT and IGNORE_CASE;
	 * {@code candidates} and {@code reason} explain UNRESOLVED.
	 */
	public record ColumnChange(ColumnInfo source, ColumnInfo target, ColumnResolution resolution,
			List<String> candidates, String reason) {
	}

	public record TableChange(TableIdentity sourceTable, TableIdentity targetTable, TableResolution resolution,
			List<ColumnChange> columns, List<String> unmatchedTargetColumns) {
	}

	/**
	 * Correspondences between source and target structure. {@code mappingSource}
	 * states how the correspondences were obtained; NAME_MATCH means they were
	 * inferred from names and are not a resolved transfer mapping.
	 */
	public record Changes(String format, int formatVersion, String mappingSource, List<TableChange> tables) {
	}

	public record OriginFingerprints(String sourceSchemaFile, String targetSchemaFile) {
	}

	/**
	 * sqlapp-side record of a published request. It is not given to the converter
	 * and does not authenticate the request.
	 */
	public record State(String format, int formatVersion, String requestId, InputFile manifest,
			OriginFingerprints origin, String createdAt) {
	}

	/** Result returned to callers of the command. */
	public record Result(String requestId, java.nio.file.Path workDir, Manifest manifest, String manifestFingerprint) {
	}
}
