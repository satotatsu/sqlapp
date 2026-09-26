/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.data.schemas.migration.assessment.DatabaseMigrationAssessmentProvider;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.*;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessmentSource;

/** Offline Access-to-SQL Server metadata diagnosis; never executes SQL or reads rows. */
public final class SqlServerDatabaseMigrationAssessmentProvider implements DatabaseMigrationAssessmentProvider {
	private static final Set<String> VERSIONS = Set.of("2016", "2017", "2019", "2022");
	private static final String TYPES = "https://support.microsoft.com/en-us/access/comparing-access-and-sql-server-data-types";
	private static final String COMPATIBILITY = "https://learn.microsoft.com/en-us/sql/ssma/access/incompatible-access-features-accesstosql";
	private static final String LIMITS = "https://learn.microsoft.com/en-us/sql/sql-server/maximum-capacity-specifications-for-sql-server";
	private static final String DATETIME = "https://learn.microsoft.com/en-us/sql/t-sql/data-types/datetime2-transact-sql";
	private static final String KEYS = "https://learn.microsoft.com/en-us/sql/relational-databases/tables/primary-and-foreign-key-constraints";

	@Override
	public boolean supports(final String sourceProduct, final String targetDatabase, final String targetVersion) {
		return "Microsoft Access".equals(sourceProduct) && "sqlserver".equalsIgnoreCase(targetDatabase)
				&& targetVersion != null && VERSIONS.contains(targetVersion);
	}

	@Override
	public String targetProduct() { return "Microsoft SQL Server"; }

	@Override
	public String normalizeTargetVersion(final String version) {
		if (version == null || !VERSIONS.contains(version)) {
			throw new IllegalArgumentException("SQL Server targetVersion must be 2016, 2017, 2019 or 2022");
		}
		return version;
	}

	@Override
	public MigrationAssessment assess(final MigrationAssessmentSource source, final String targetVersion) {
		if (source == null || !supports(source.sourceProduct(), "sqlserver", targetVersion)) {
			throw new IllegalArgumentException("SQL Server assessment requires Access source metadata and targetVersion=2016, 2017, 2019 or 2022");
		}
		final var findings = new ArrayList<Finding>();
		final var inventory = new ArrayList<Inventory>();
		for (final Schema schema : source.schemas()) {
			int columns = 0;
			int relationships = 0;
			for (final Table table : schema.getTables()) {
				final var tableId = id(schema, "table", table.getName(), null);
				identifier(findings, tableId);
				if (table.getPrimaryKeyConstraint() == null && table.getIndexes().stream().noneMatch(index -> index.isUnique())
						&& table.getConstraints().stream().noneMatch(constraint -> constraint instanceof UniqueConstraint)) {
					add(findings, "key", Severity.WARNING, tableId, "No primary key or unique index/constraint was collected.",
							"Choose and validate a stable key for reconciliation and Access linked-table updates.", COMPATIBILITY);
				}
				for (final var constraint : table.getConstraints()) {
					final var constraintId = id(schema, "constraint", constraint.getName(), table.getName());
					identifier(findings, constraintId);
					if (constraint instanceof CheckConstraint) {
						add(findings, "check-expression", Severity.REVIEW, constraintId, "An Access validation expression is present.",
								"Translate to T-SQL and test NULL and function semantics.", COMPATIBILITY);
					} else if (constraint instanceof ForeignKeyConstraint foreignKey) {
						relationships++;
						add(findings, "foreign-key", Severity.REVIEW, constraintId, "An Access relationship was collected.",
								"Verify compatible mapped column types and sizes, a referenced unique key, and absence of orphan rows.", KEYS);
						if (foreignKey.getUpdateRule() == CascadeRule.Cascade || foreignKey.getDeleteRule() == CascadeRule.Cascade) {
							add(findings, "cascade", Severity.REVIEW, constraintId, "The relationship uses cascading updates or deletes.",
									"SQL Server supports cascades; check cycles, multiple cascade paths and interactions with triggers before creating constraints.", KEYS);
						}
					} else if (constraint instanceof UniqueConstraint unique) {
						index(findings, constraintId, !unique.isPrimaryKey(), false);
					}
				}
				for (final var index : table.getIndexes()) {
					final var indexId = id(schema, "index", index.getName(), table.getName());
					identifier(findings, indexId);
					index(findings, indexId, index.isUnique(), Boolean.TRUE.equals(index.getSpecifics().get("IGNORE_NULLS", Boolean.class)));
				}
				for (final Column column : table.getColumns()) {
					columns++;
					column(findings, column, id(schema, "column", column.getName(), table.getName()));
				}
			}
			inventory.add(new Inventory(schema.getCatalogName(), schema.getName(), "localTables", schema.getTables().size()));
			inventory.add(new Inventory(schema.getCatalogName(), schema.getName(), "columns", columns));
			inventory.add(new Inventory(schema.getCatalogName(), schema.getName(), "collectedRelationships", relationships));
		}
		findings.add(new Finding("access.sqlserver.environment", Severity.REVIEW, Evidence.MANUAL_CHECK, null,
				"Target environment and application compatibility were not checked; no rows were inspected by this assessor.",
				"Verify SQL Server release/edition, compatibility level, database/schema mapping, collation, reserved names, ODBC/JDBC support, permissions and cutover recovery. Test Access forms, queries and linked-table writes.", null));
		return new MigrationAssessment(findings, inventory);
	}

	private static void column(final List<Finding> findings, final Column column, final ObjectId id) {
		identifier(findings, id);
		final String type = column.getSpecifics().get("access.sourceType", String.class);
		if ("COMPLEX_TYPE".equals(type)) {
			add(findings, "complex-type", Severity.BLOCKER, id, "An attachment or multi-value field is not a scalar SQL Server column.",
					"Define child-table or attachment extraction mappings preserving parent keys and content.", null);
			return;
		}
		final String action = typeAction(type);
		add(findings, "type", action == null ? Severity.BLOCKER : Severity.REVIEW, id,
				"Source type: " + (type == null ? "unknown" : type) + ". No values were converted.",
				action == null ? "Supply a supported native type and an explicit lossless conversion plan." : action, TYPES);
		if ("SHORT_DATE_TIME".equals(type) || "EXT_DATE_TIME".equals(type)) {
			add(findings, "datetime", Severity.REVIEW, id, "Source dates and fractional precision were not profiled.",
					"Consider datetime2 with explicit precision (0-7); it has no timezone. If choosing datetime, check its narrower date range and rounding.", DATETIME);
		}
		if ("TEXT".equals(type) || "MEMO".equals(type)) {
			add(findings, "text-semantics", Severity.REVIEW, id, "Text comparison and length semantics require a target decision.",
					"Preserve NULL versus empty strings; test trailing spaces, case/accent sensitivity, Unicode lengths and uniqueness under the target collation.", null);
		}
		if (column.isIdentity()) {
			add(findings, "autonumber", Severity.REVIEW, id, "The source column uses AutoNumber.",
					"GUID".equals(type)
							? "Preserve GUID values in uniqueidentifier and choose a GUID generator; numeric IDENTITY is not a GUID generator."
							: "Determine increment versus random behavior. Preserve existing keys; if using IDENTITY, plan IDENTITY_INSERT, reseeding and a test of the first new row.", null);
		}
		if (hasText(column.getDefaultValue()) || hasText(column.getCheck()) || hasText(column.getFormula())) {
			add(findings, "column-expression", Severity.REVIEW, id, "An Access default, validation or calculated expression is present.",
					"Translate explicitly to T-SQL or an application rule and test it; Access expressions are not copied automatically.", COMPATIBILITY);
		}
	}

	private static String typeAction(final String type) {
		if (type == null) { return null; }
		return switch (type) {
		case "BOOLEAN" -> "Consider bit with an explicit true/false mapping; verify Access and client truth-value encoding.";
		case "BYTE" -> "Consider tinyint; validate values are in the unsigned range 0-255.";
		case "INT" -> "Consider smallint; verify range and any referencing keys.";
		case "LONG" -> "Consider int; verify range and any referencing keys.";
		case "BIG_INT" -> "Consider bigint; verify client support and any referencing keys.";
		case "MONEY" -> "Consider decimal(19,4); preserve exact decimal amounts and reconcile totals.";
		case "NUMERIC" -> "Choose decimal(p,s) using source precision/scale and validate actual values against the chosen target precision.";
		case "FLOAT" -> "Consider real; define floating-point comparison tolerances.";
		case "DOUBLE" -> "Consider float(53); define floating-point comparison tolerances.";
		case "SHORT_DATE_TIME", "EXT_DATE_TIME" -> "Consider datetime2(p) after validating dates, fractional precision and Access/ODBC client support; do not infer a timezone.";
		case "TEXT" -> "Consider nvarchar(n); size n in UTF-16 byte-pairs and check Unicode/client behavior under the chosen collation.";
		case "MEMO" -> "Consider nvarchar(max); preserve any rich-text markup or hyperlink encoding and verify client editing behavior.";
		case "GUID" -> "Consider uniqueidentifier; preserve GUID values and use consistent conversion for referencing columns.";
		case "BINARY" -> "Consider varbinary(n) or varbinary(max) after measuring payload sizes.";
		case "OLE" -> "Consider varbinary(max); decide whether to preserve the OLE container or extract its payload and verify content hashes.";
		default -> null;
		};
	}

	private static void index(final List<Finding> findings, final ObjectId id, final boolean unique, final boolean ignoreNulls) {
		add(findings, "index-size", Severity.REVIEW, id, "An index or key constraint was collected.",
				"Check mapped key widths against the selected clustered/nonclustered index limits; long-value types require a separate index design.", LIMITS);
		if (unique || ignoreNulls) {
			add(findings, "index-nulls", Severity.REVIEW, id, "The source index has uniqueness or IgnoreNulls semantics.",
					"Profile NULL-containing keys and target uniqueness. Decide whether a filtered index preserves the intended rule; account for all columns of composite keys.", COMPATIBILITY);
		}
	}

	private static void identifier(final List<Finding> findings, final ObjectId id) {
		if (id.name() == null) { return; }
		if (id.name().codePointCount(0, id.name().length()) > 128) {
			add(findings, "identifier-length", Severity.BLOCKER, id, "The identifier exceeds 128 characters.",
					"Choose a shorter target name and preserve the source-to-target name mapping.", LIMITS);
		} else if (!id.name().matches("[A-Za-z][A-Za-z0-9_]*")) {
			add(findings, "identifier", Severity.REVIEW, id, "The name requires review under SQL Server identifier rules.",
					"Decide naming and bracket/quote escaping, preserve a name mapping, and test application references.", COMPATIBILITY);
		}
	}

	private static ObjectId id(final Schema schema, final String type, final String name, final String table) {
		return new ObjectId(schema.getCatalogName(), schema.getName(), type, name, table);
	}
	private static boolean hasText(final String value) { return value != null && !value.isBlank(); }
	private static void add(final List<Finding> findings, final String rule, final Severity severity, final ObjectId id,
			final String reason, final String action, final String reference) {
		findings.add(new Finding("access.sqlserver." + rule, severity, Evidence.SCHEMA, id, reason, action, reference));
	}
}
