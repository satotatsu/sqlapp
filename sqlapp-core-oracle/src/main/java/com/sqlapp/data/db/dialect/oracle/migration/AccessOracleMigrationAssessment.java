/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.*;

/** Metadata preflight for a logical Access migration. Does not generate or execute SQL. */
public final class AccessOracleMigrationAssessment {
	private static final Set<String> TARGETS = Set.of("19c", "21c", "23ai", "26ai");
	private static final String TYPES = "https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlqr/Data-Types.html";
	private static final String NULLS = "https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/Nulls.html";

	private AccessOracleMigrationAssessment() { }

	public static boolean supportsTargetVersion(final String version) {
		return version != null && TARGETS.contains(version.toLowerCase(Locale.ROOT));
	}

	public static String validateTargetVersion(final String version) {
		if (!supportsTargetVersion(version)) {
			throw new IllegalArgumentException("targetVersion must be 19c, 21c, 23ai or 26ai");
		}
		return version.toLowerCase(Locale.ROOT);
	}

	/** Requires Access native type IDs in column specifics under access.sourceType. */
	public static MigrationAssessment assess(final Schema schema, final String targetVersion) {
		final String version = validateTargetVersion(targetVersion);
		if (schema == null || !"Microsoft Access".equals(schema.getProductName())) {
			throw new IllegalArgumentException("An Access assessment Schema is required");
		}
		final var findings = new ArrayList<Finding>();
		int columns = 0;
		int relationships = 0;
		for (final Table table : schema.getTables()) {
			final var tableId = id(schema, "table", table.getName(), null);
			identifier(findings, tableId);
			if (table.getPrimaryKeyConstraint() == null) {
				add(findings, "access.oracle.primary-key", Severity.WARNING, tableId,
						"No primary key is present in the source metadata.",
						"Choose and validate a stable key for reconciliation, restart and linked-table updates.");
			}
			for (final var constraint : table.getConstraints()) {
				final var constraintId = id(schema, "constraint", constraint.getName(), table.getName());
				identifier(findings, constraintId);
				if (constraint instanceof CheckConstraint) {
					add(findings, "access.oracle.check-expression", Severity.REVIEW, constraintId,
							"An Access table validation expression is present.", "Translate and test its Oracle and NULL semantics.");
				} else if (constraint instanceof ForeignKeyConstraint foreignKey) {
					relationships++;
					if (foreignKey.getUpdateRule() == CascadeRule.Cascade) {
						add(findings, "access.oracle.cascade-update", Severity.WARNING, constraintId,
								"The Access relationship uses cascading key updates.",
								"Oracle foreign keys do not provide ON UPDATE CASCADE; design and test an alternative.");
					}
				}
			}
			for (final var index : table.getIndexes()) {
				final var indexId = id(schema, "index", index.getName(), table.getName());
				identifier(findings, indexId);
				if (index.isUnique() || Boolean.TRUE.equals(index.getSpecifics().get("IGNORE_NULLS", Boolean.class))) {
					add(findings, "access.oracle.index-semantics", Severity.REVIEW, indexId,
							"The source index has uniqueness or IgnoreNulls semantics.",
							"Compare target uniqueness, NULL, empty-string and collation behavior using representative data.");
				}
			}
			for (final Column column : table.getColumns()) {
				columns++;
				final var columnId = id(schema, "column", column.getName(), table.getName());
				identifier(findings, columnId);
				final String nativeType = column.getSpecifics().get("access.sourceType", String.class);
				if ("COMPLEX_TYPE".equals(nativeType)) {
					add(findings, "access.oracle.complex-type", Severity.BLOCKER, columnId,
							"An attachment or multi-value field cannot be copied as a scalar column.",
							"Define a child-table or attachment extraction mapping that preserves parent keys and content.");
					continue;
				}
				final String action = typeAction(nativeType, version);
				add(findings, "access.oracle.type", action == null ? Severity.BLOCKER : Severity.REVIEW, columnId,
						"Source type: " + (nativeType == null ? "unknown" : nativeType) + ". No value conversion has been performed.",
						action == null ? "Supply a supported native type and an explicit lossless conversion plan." : action);
				if (Set.of("TEXT", "MEMO", "GUID").contains(nativeType == null ? "" : nativeType)) {
					findings.add(new Finding("access.oracle.empty-string", Severity.WARNING, Evidence.DOCUMENTED_RULE,
							columnId, "Oracle scalar character columns treat empty strings as NULL; source values were not scanned.",
							"Count NULL and empty values, decide their intended meaning, and verify NOT NULL and unique constraints after conversion.", NULLS));
				}
				if (column.isIdentity()) {
					add(findings, "access.oracle.autonumber", Severity.REVIEW, columnId,
							"The source column uses AutoNumber.",
							"Preserve existing IDs and references. Determine increment/random/GUID behavior; configure target generation and test the first new insert after loading.");
				}
				if (hasText(column.getDefaultValue()) || hasText(column.getCheck()) || hasText(column.getFormula())) {
					add(findings, "access.oracle.column-expression", Severity.REVIEW, columnId,
							"An Access default, validation rule or calculated expression is present.",
							"Translate the expression explicitly and test representative values; do not copy Access SQL verbatim.");
				}
			}
		}
		return new MigrationAssessment(findings, List.of(
				new Inventory(schema.getCatalogName(), schema.getName(), "localTables", schema.getTables().size()),
				new Inventory(schema.getCatalogName(), schema.getName(), "columns", columns),
				new Inventory(schema.getCatalogName(), schema.getName(), "collectedRelationships", relationships)));
	}

	private static String typeAction(final String type, final String version) {
		if (type == null) { return null; }
		return switch (type) {
		case "BOOLEAN" -> Set.of("23ai", "26ai").contains(version)
				? "Consider BOOLEAN after verifying Access/ODBC client support, or use an explicit numeric truth-value mapping."
				: "Choose NUMBER(1) with a check constraint and an explicit truth-value mapping; verify Access true/false encoding.";
		case "BYTE", "INT", "LONG", "BIG_INT" -> "Consider NUMBER with sufficient integer precision; validate source ranges and target constraints.";
		case "MONEY" -> "Consider NUMBER(19,4); preserve exact decimal values and reconcile monetary totals.";
		case "NUMERIC" -> "Use NUMBER(p,s) after checking the Schema precision and scale and profiling actual values.";
		case "FLOAT", "DOUBLE" -> "Choose binary floating point or NUMBER deliberately; define rounding and reconciliation tolerances.";
		case "SHORT_DATE_TIME", "EXT_DATE_TIME" -> "Choose DATE or TIMESTAMP with explicit fractional precision; preserve time-of-day and decide timezone semantics without assuming a timezone.";
		case "TEXT" -> "Choose VARCHAR2 with explicit CHAR/BYTE semantics or NVARCHAR2 after checking target character sets and actual encoded byte lengths.";
		case "MEMO" -> "Consider CLOB/NCLOB; verify Unicode, rich-text markup, empty LOB handling and client behavior.";
		case "GUID" -> "Choose a documented GUID representation (character or RAW) and consistent conversion of all referencing keys.";
		case "BINARY" -> "Consider RAW or BLOB after checking maximum payload sizes and client bindings.";
		case "OLE" -> "Decide whether to preserve the OLE container in BLOB or extract its embedded payload; verify content checksums.";
		default -> null;
		};
	}

	private static void identifier(final List<Finding> findings, final ObjectId id) {
		if (id.name() == null) { return; }
		if (!id.name().matches("[A-Za-z][A-Za-z0-9_$#]*")) {
			add(findings, "access.oracle.identifier", Severity.REVIEW, id,
					"The name requires an explicit Oracle naming/quoting decision.",
					"Preserve a name mapping and update application references; validate reserved words, encoded byte lengths and target COMPATIBLE settings.");
		}
	}

	private static ObjectId id(final Schema schema, final String type, final String name, final String table) {
		return new ObjectId(schema.getCatalogName(), schema.getName(), type, name, table);
	}

	private static void add(final List<Finding> findings, final String rule, final Severity severity,
			final ObjectId object, final String reason, final String action) {
		findings.add(new Finding(rule, severity, Evidence.SCHEMA, object, reason, action,
				"access.oracle.type".equals(rule) ? TYPES : null));
	}

	private static boolean hasText(final String value) { return value != null && !value.isBlank(); }
}
