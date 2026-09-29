/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Pattern;

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
import com.sqlapp.data.schemas.migration.assessment.MigrationDataProfile.ColumnProfile;
import com.sqlapp.data.schemas.migration.assessment.ResolvedMigrationTargetMapping;

/** Offline Access-to-SQL Server metadata diagnosis; never executes SQL or reads rows. */
public final class SqlServerDatabaseMigrationAssessmentProvider implements DatabaseMigrationAssessmentProvider {
	@Override
	public String generateTargetDdl(final MigrationAssessmentSource source,
			final ResolvedMigrationTargetMapping mapping, final String targetVersion) {
		return com.sqlapp.data.schemas.migration.assessment.MigrationTargetDdlGenerator
				.generate(source, mapping, SqlServerDatabaseMigrationAssessmentProvider::sqlServerName, "GO\n\n",
						column -> " IDENTITY(1,1)", SqlServerDatabaseMigrationAssessmentProvider::usableObjectName,
						table -> "Preserve Access AutoNumber values: execute SET IDENTITY_INSERT "
								+ sqlServerTableName(table)
								+ " ON before loading this table, OFF immediately afterward, then verify the identity seed.");
	}

	private static String sqlServerName(final String value) { return "[" + value.replace("]", "]]") + "]"; }
	private static String sqlServerTableName(final ResolvedMigrationTargetMapping.TableMapping table) {
		return (table.targetSchema() == null || table.targetSchema().isBlank() ? ""
				: sqlServerName(table.targetSchema()) + ".") + sqlServerName(table.targetTable());
	}
	private static boolean usableObjectName(final String value) {
		return value.codePointCount(0, value.length()) <= 128
				&& value.codePoints().noneMatch(Character::isISOControl);
	}
	private static final Pattern DECIMAL = Pattern.compile("(?:DECIMAL|NUMERIC)\\((\\d+),(\\d+)\\)", Pattern.CASE_INSENSITIVE);
	private static final Pattern TEXT_TYPE = Pattern.compile("(N?VARCHAR|N?CHAR)\\((MAX|\\d+)\\)", Pattern.CASE_INSENSITIVE);
	private static final Pattern DATETIME2 = Pattern.compile("DATETIME2(?:\\((\\d)\\))?", Pattern.CASE_INSENSITIVE);
	private static final Pattern BINARY_TYPE = Pattern.compile("VARBINARY\\((MAX|\\d+)\\)", Pattern.CASE_INSENSITIVE);
	private static final Pattern FLOAT_TYPE = Pattern.compile("FLOAT(?:\\((\\d+)\\))?", Pattern.CASE_INSENSITIVE);
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
	public String suggestTargetType(final Column column, final String targetVersion) {
		final String type = column.getSpecifics().get("access.sourceType", String.class);
		if (type == null) { return null; }
		return switch (type) {
		case "BOOLEAN" -> "bit";
		case "BYTE" -> "tinyint";
		case "INT" -> "smallint";
		case "LONG" -> "int";
		case "BIG_INT" -> "bigint";
		case "MONEY" -> "decimal(19,4)";
		case "NUMERIC" -> "decimal(" + precision(column, 38) + "," + Math.max(0, column.getScale() == null ? 0 : column.getScale()) + ")";
		case "FLOAT" -> "real";
		case "DOUBLE" -> "float(53)";
		case "SHORT_DATE_TIME" -> "datetime2(3)";
		case "EXT_DATE_TIME" -> "datetime2(7)";
		case "TEXT" -> column.getLength() != null && column.getLength() > 4000 ? "nvarchar(max)" : "nvarchar(" + length(column, 255, 4000) + ")";
		case "MEMO" -> "nvarchar(max)";
		case "GUID" -> "uniqueidentifier";
		case "BINARY" -> column.getLength() != null && column.getLength() > 8000 ? "varbinary(max)" : "varbinary(" + length(column, 255, 8000) + ")";
		case "OLE" -> "varbinary(max)";
		default -> null;
		};
	}
	private static long length(final Column column, final long fallback, final long maximum) {
		return Math.min(maximum, column.getLength() == null || column.getLength() < 1 ? fallback : column.getLength());
	}
	private static long precision(final Column column, final long fallback) {
		return Math.min(38, column.getLength() == null || column.getLength() < 1 ? fallback : column.getLength());
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
		if (source.dataProfile() != null) {
			for (final var table : source.dataProfile().tables()) {
				for (final var column : table.columns()) {
					if (column.dateTime() != null && column.dateTime().minimum() != null
							&& LocalDateTime.parse(column.dateTime().minimum()).isBefore(LocalDateTime.of(1753, 1, 1, 0, 0))) {
						findings.add(new Finding("access.sqlserver.observed-legacy-datetime-range", Severity.WARNING, Evidence.DATABASE,
								column.column(), "Observed dates include values before 1753-01-01, outside legacy datetime's range.",
								"Use a validated datetime2 mapping and compatible clients; do not load these values unchanged into datetime.", DATETIME));
					}
				}
			}
		}
		findings.add(new Finding("access.sqlserver.environment", Severity.REVIEW, Evidence.MANUAL_CHECK, null,
				"Target environment and application compatibility were not checked; no rows were inspected by this assessor.",
				"Verify SQL Server release/edition, compatibility level, database/schema mapping, collation, reserved names, ODBC/JDBC support, permissions and cutover recovery. Test Access forms, queries and linked-table writes.", null));
		return new MigrationAssessment(findings, inventory);
	}

	@Override
	public MigrationAssessment assessMapping(final MigrationAssessmentSource source, final String targetVersion,
			final ResolvedMigrationTargetMapping mapping) {
		final var findings = new ArrayList<Finding>();
		final var profiles = new HashMap<ObjectId, ColumnProfile>();
		if (source.dataProfile() != null) { source.dataProfile().tables().forEach(t -> t.columns().forEach(c -> profiles.put(c.column(), c))); }
		int columns = 0;
		for (final var table : mapping.tables()) {
			int identities = 0;
			targetIdentifier(findings, table.sourceTable(), "schema", table.targetSchema());
			targetIdentifier(findings, table.sourceTable(), "table", table.targetTable());
			for (final var column : table.columns()) {
				columns++;
				targetIdentifier(findings, column.sourceColumn(), "column", column.targetColumn());
				final String type = column.targetType().trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
				final var profile = profiles.get(column.sourceColumn());
				if (!supportedMappingType(type)) {
					findings.add(mappingFinding("type", Severity.BLOCKER, column.sourceColumn(), "Unsupported or invalid SQL Server target type: " + column.targetType(),
							"Use a SQL Server type supported by this assessment and specify length/precision explicitly."));
					continue;
				}
				if (Boolean.TRUE.equals(column.identity())) {
					identities++;
					if (!sqlServerIdentityType(type)) {
						findings.add(mappingFinding("identity", Severity.BLOCKER, column.sourceColumn(),
								"SQL Server IDENTITY requires an integer or scale-zero decimal target type: " + column.targetType(),
								"Use tinyint, smallint, int, bigint or decimal(p,0), or remove identity."));
					}
				}
				nullability(findings, column, profile);
				capacity(findings, column, profile, type);
			}
			if (identities > 1) {
				findings.add(mappingFinding("identity", Severity.BLOCKER, table.sourceTable(),
						"SQL Server permits only one IDENTITY column per table; mapping contains " + identities + ".",
						"Keep one identity column and define explicit generation for the other columns."));
			}
		}
		findings.add(mappingFinding("coverage", Severity.REVIEW, null,
				"The mapping resolves " + mapping.tables().size() + " tables and " + columns + " columns; conversion expressions were recorded but not executed.",
				"Review unmapped objects, generate and inspect DDL/load transformations, then validate converted values and constraints on SQL Server."));
		return new MigrationAssessment(findings, List.of(new Inventory(null, "", "mappedTables", mapping.tables().size()),
				new Inventory(null, "", "mappedColumns", columns)));
	}

	private static boolean supportedMappingType(final String type) {
		final var decimal = DECIMAL.matcher(type);
		if (decimal.matches()) {
			final int precision = Integer.parseInt(decimal.group(1));
			final int scale = Integer.parseInt(decimal.group(2));
			return precision >= 1 && precision <= 38 && scale <= precision;
		}
		final var text = TEXT_TYPE.matcher(type);
		if (text.matches()) {
			if ("MAX".equals(text.group(2))) { return !type.startsWith("NCHAR") && !type.startsWith("CHAR"); }
			final int length = Integer.parseInt(text.group(2));
			return length >= 1 && length <= (type.startsWith("N") ? 4000 : 8000);
		}
		final var dateTime = DATETIME2.matcher(type);
		if (dateTime.matches()) { return dateTime.group(1) == null || Integer.parseInt(dateTime.group(1)) <= 7; }
		final var binary = BINARY_TYPE.matcher(type);
		if (binary.matches()) { return "MAX".equals(binary.group(1))
				|| Integer.parseInt(binary.group(1)) >= 1 && Integer.parseInt(binary.group(1)) <= 8000; }
		final var floating = FLOAT_TYPE.matcher(type);
		if (floating.matches()) { return floating.group(1) == null
				|| Integer.parseInt(floating.group(1)) >= 1 && Integer.parseInt(floating.group(1)) <= 53; }
		return Set.of("BIT", "TINYINT", "SMALLINT", "INT", "BIGINT", "REAL",
						"DATE", "DATETIME", "UNIQUEIDENTIFIER").contains(type);
	}
	private static boolean sqlServerIdentityType(final String type) {
		if (Set.of("TINYINT", "SMALLINT", "INT", "BIGINT").contains(type)) { return true; }
		final var decimal = DECIMAL.matcher(type);
		return decimal.matches() && Integer.parseInt(decimal.group(2)) == 0;
	}
	private static void nullability(final List<Finding> findings,
			final ResolvedMigrationTargetMapping.ColumnMapping column, final ColumnProfile profile) {
		if (!Boolean.FALSE.equals(column.nullable())) { return; }
		if (profile == null || profile.nullCount() == null) {
			findings.add(mappingFinding("nullability-unverified", Severity.REVIEW, column.sourceColumn(),
					"The target is NOT NULL but source NULL values were not completely scanned.", "Run with scanData=true and resolve excluded column coverage before creating the constraint."));
		} else if (profile.nullCount() > 0) {
			findings.add(mappingFinding("observed-null", Severity.BLOCKER, column.sourceColumn(), profile.nullCount()
					+ " source NULL values do not fit the mapped NOT NULL target.", "Clean or convert these values before loading and creating the constraint."));
		}
	}
	private static void capacity(final List<Finding> findings,
			final ResolvedMigrationTargetMapping.ColumnMapping column, final ColumnProfile profile, final String type) {
		if (profile == null) { return; }
		final var decimal = DECIMAL.matcher(type);
		if (decimal.matches() && profile.numeric() != null && profile.numeric().maximumIntegerDigits() != null) {
			final int precision = Integer.parseInt(decimal.group(1));
			final int scale = Integer.parseInt(decimal.group(2));
			if (precision < 1 || precision > 38 || scale > precision || profile.numeric().maximumIntegerDigits() > precision - scale
					|| profile.numeric().maximumScale() > scale) {
				findings.add(mappingFinding("observed-number-overflow", Severity.BLOCKER, column.sourceColumn(),
						"Mapped " + type + " is invalid or does not fit observed numeric digits.", "Choose decimal precision/scale up to 38 or define and test explicit rounding."));
			}
		}
		final var text = TEXT_TYPE.matcher(type);
		if (text.matches() && !"MAX".equals(text.group(2)) && profile.text() != null) {
			final long limit = Long.parseLong(text.group(2));
			final Long observed = type.startsWith("N") ? profile.text().maximumUtf16Units() : profile.text().maximumCodePoints();
			if (observed != null && observed > limit) {
				findings.add(mappingFinding("observed-text-overflow", Severity.BLOCKER, column.sourceColumn(),
						"Observed text length " + observed + " exceeds mapped " + type + " limit " + limit + ".",
						"Choose a larger type; for varchar also validate encoding against the actual target collation/code page."));
			}
		}
		if ("DATETIME".equals(type) && profile.dateTime() != null && profile.dateTime().minimum() != null
				&& LocalDateTime.parse(profile.dateTime().minimum()).isBefore(LocalDateTime.of(1753, 1, 1, 0, 0))) {
			findings.add(mappingFinding("observed-datetime-range", Severity.BLOCKER, column.sourceColumn(),
					"Observed dates before 1753-01-01 do not fit mapped datetime.", "Map to datetime2 with validated precision and client support."));
		}
		final var dateTime = DATETIME2.matcher(type);
		if (dateTime.matches() && profile.dateTime() != null && profile.dateTime().maximumFractionalDigits() != null) {
			final int precision = dateTime.group(1) == null ? 7 : Integer.parseInt(dateTime.group(1));
			if (profile.dateTime().maximumFractionalDigits() > precision) {
				findings.add(mappingFinding("observed-time-precision", Severity.WARNING, column.sourceColumn(),
						"Observed fractional precision exceeds " + type + ".", "Choose sufficient precision or test and approve rounding."));
			}
		}
	}
	private static Finding mappingFinding(final String rule, final Severity severity, final ObjectId id,
			final String reason, final String action) {
		return new Finding("access.sqlserver.mapping." + rule, severity, Evidence.DOCUMENTED_RULE, id, reason, action, null);
	}
	private static void targetIdentifier(final List<Finding> findings, final ObjectId source,
			final String kind, final String name) {
		if (name == null || name.isBlank()) { return; }
		if (name.codePointCount(0, name.length()) > 128) {
			findings.add(mappingFinding("identifier-length", Severity.BLOCKER, source,
					"Mapped target " + kind + " name exceeds 128 characters: " + name,
					"Choose a shorter target name and preserve the mapping."));
		} else if (!name.matches("[A-Za-z][A-Za-z0-9_]*")) {
			findings.add(mappingFinding("identifier", Severity.REVIEW, source,
					"Mapped target " + kind + " name requires bracket/quoted-identifier review: " + name,
					"Confirm casing, escaping, reserved words, target collation and application references."));
		}
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
			add(findings, "datetime", Severity.REVIEW, id, "Validate date ranges and fractional precision against the chosen target mapping.",
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
