/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.sql.Connection;
import java.sql.SQLException;

import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.properties.DefinitionGetter;
import com.sqlapp.data.schemas.properties.StatementGetter;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.*;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessmentProvider;

/** Snapshot-based Oracle 10g and later assessment for a 26ai target. No SQL is executed. */
public final class OracleMigrationAssessmentProvider implements MigrationAssessmentProvider {
	private static final String UPGRADE = "https://docs.oracle.com/en/database/oracle/oracle-database/26/upgrd/oracle-database-releases-that-support-direct-upgrade.html";
	private static final Set<Integer> SOURCES = Set.of(10, 11, 12, 18, 19, 21, 23, 26);
	private static final Set<String> SQL_OBJECTS = Set.of("views", "mviews", "procedures", "functions",
			"packages", "packageBodies", "triggers");

	@Override
	public boolean supports(final String product, final String targetVersion) {
		return product != null && product.matches("(?i)Oracle(?: Database| AI Database)?")
				&& "26ai".equalsIgnoreCase(targetVersion);
	}

	@Override
	public MigrationAssessment assess(final List<Schema> schemas, final String targetVersion, final Method method) {
		return assess(schemas, targetVersion, method, null);
	}

	@Override
	public MigrationAssessment assess(final List<Schema> schemas, final String targetVersion, final Method method,
			final String targetCharacterSet) {
		if (targetCharacterSet != null && !"AL32UTF8".equalsIgnoreCase(targetCharacterSet)) {
			throw new IllegalArgumentException("targetCharacterSet must be AL32UTF8 or omitted; other character sets are not assessed");
		}
		Objects.requireNonNull(method, "migrationMethod is required: DIRECT_UPGRADE or LOGICAL_MIGRATION");
		if (schemas == null || schemas.isEmpty()) {
			throw new IllegalArgumentException("At least one Schema is required");
		}
		final var findings = new ArrayList<Finding>();
		final var inventory = new ArrayList<Inventory>();
		for (final Schema schema : schemas) {
			if (!supports(schema.getProductName(), targetVersion)) {
				throw new IllegalArgumentException("Oracle assessment requires Oracle Schema metadata and targetVersion=26ai");
			}
			final Integer major = schema.getProductMajorVersion();
			if (major == null || !SOURCES.contains(major)) {
				throw new IllegalArgumentException("Schema " + schema.getName()
						+ " must contain a supported Oracle productMajorVersion (10, 11, 12, 18, 19, 21, 23 or 26)");
			}
			final var id = new ObjectId(schema.getCatalogName(), schema.getName(), "schema", schema.getName());
			if (targetCharacterSet != null) {
				OracleCharacterSetAssessment.assess(schema, findings);
			}
			if (method == Method.DIRECT_UPGRADE && major < 19) {
				findings.add(new Finding("oracle.direct-upgrade.unsupported", Severity.BLOCKER, Evidence.DOCUMENTED_RULE, id,
						"Oracle " + major + " cannot be directly upgraded to 26ai.",
						"Choose a supported intermediate upgrade path using the exact source patch version, or assess a logical migration separately.", UPGRADE));
			} else {
				findings.add(new Finding("oracle.migration-path.review", Severity.REVIEW, Evidence.MANUAL_CHECK, id,
						"The selected migration method requires release and environment validation.",
						method == Method.LOGICAL_MIGRATION
								? "Validate the chosen export/import or data-copy tool against the exact source and target releases; direct-upgrade restrictions do not establish logical migration compatibility."
								: "Verify exact release updates, platform and prerequisites with Oracle guidance; 23ai/26ai transitions require separate release-update checks. Run applicable Oracle preupgrade checks.", UPGRADE));
			}
			for (final var entry : schema.getChildObjectCollectionMap().entrySet()) {
				inventory.add(new Inventory(schema.getCatalogName(), schema.getName(), entry.getKey(), entry.getValue().size()));
				for (final Object value : entry.getValue()) {
					final var object = (AbstractSchemaObject<?>) value;
					final var objectId = new ObjectId(schema.getCatalogName(), schema.getName(), entry.getKey(), object.getName());
					if (!object.isValid()) {
						findings.add(new Finding("oracle.object.invalid", Severity.WARNING, Evidence.SCHEMA, objectId,
								"The supplied Schema marks this object invalid.",
								"Inspect compilation errors and dependencies in the source; repair or document the exception, then compile and verify in the target.", null));
					}
					if (SQL_OBJECTS.contains(entry.getKey())
							&& !(object instanceof DefinitionGetter definition && hasText(definition.getDefinition()))
							&& !(object instanceof StatementGetter statement && hasText(statement.getStatement()))) {
						findings.add(new Finding("oracle.object.missing-sql", Severity.WARNING, Evidence.SCHEMA, objectId,
								"Neither definition nor statement text is present in the supplied snapshot.",
								"Capture executable DDL or a body/query with its modeled components before planning recreation. This warning concerns snapshot coverage, not proof of a missing source object.", null));
					}
					if (!"tables".equals(entry.getKey())) {
						findings.add(new Finding("oracle.object.review", Severity.REVIEW, Evidence.MANUAL_CHECK, objectId,
								"Object presence is recorded; target compatibility has not been executed or certified.",
								action(entry.getKey()), null));
					}
				}
			}
		}
		addReview(findings, "snapshot-coverage", "A Schema snapshot may omit objects or metadata due to extraction scope or privileges.",
				"Compare inventory with the source catalog, including public synonyms, database links, grants, roles, directories and scheduler/DBMS_JOB jobs. A zero count does not prove absence.");
		addReview(findings, "character-set", "Source and target character sets and NLS settings have not been compared.",
				"Record database/national character sets, NLS_LENGTH_SEMANTICS, sort/comparison and date settings; test byte expansion, implicit conversions and representative Japanese data.");
		addReview(findings, "client-authentication", "Client and authentication compatibility is not represented by Schema XML.",
				"Inventory JDBC/ODBC/Oracle clients, Access bitness and connection settings; verify supported combinations and authentication with the exact target release. Do not include credentials in reports.");
		addReview(findings, "privileges-jobs", "Runtime grants, external resources and scheduled execution require separate evidence.",
				"Verify direct grants versus roles, definer/invoker rights, directories, external files and job ownership/schedules; test jobs in an isolated target with external side effects controlled.");
		addReview(findings, "sql-regression", "Compilation alone does not establish application correctness or performance.",
				"Recompile PL/SQL and views, inspect invalid objects, and compare business SQL results and execution plans with identical inputs and representative data.");
		addReview(findings, "cutover", "Data consistency and cutover readiness were not assessed offline.",
				"Rehearse load order, constraints/triggers, sequence next values, materialized view refresh, counts and business totals; document downtime, restart and rollback procedures.");
		return new MigrationAssessment(findings, inventory);
	}

	private static void addReview(final List<Finding> findings, final String suffix, final String reason, final String action) {
		findings.add(new Finding("oracle.environment." + suffix, Severity.REVIEW, Evidence.MANUAL_CHECK, null, reason, action, null));
	}

	private static boolean hasText(final List<String> lines) {
		return lines != null && lines.stream().anyMatch(line -> line != null && !line.isBlank());
	}

	@Override
	public MigrationAssessment assess(final Connection connection, final List<Schema> schemas,
			final String targetVersion, final Method method, final String targetCharacterSet,
			final boolean scanCharacterData) {
		if (!"AL32UTF8".equalsIgnoreCase(targetCharacterSet)) {
			throw new IllegalArgumentException("Online Oracle character assessment requires targetCharacterSet=AL32UTF8");
		}
		final MigrationAssessment offline = assess(schemas, targetVersion, method, targetCharacterSet);
		try {
			return OracleOnlineMigrationAssessment.assess(Objects.requireNonNull(connection, "connection"), schemas,
					offline, scanCharacterData);
		} catch (SQLException e) {
			throw new IllegalStateException("Failed to read Oracle source metadata for migration assessment", e);
		}
	}

	private static String action(final String type) {
		return switch (type) {
		case "dbLinks" -> "Verify destination, service resolution, authentication and privileges from the target database; test connectivity without disclosing credentials.";
		case "synonyms" -> "Resolve the referenced owner/object and any remote link in the target; verify grants and application name resolution.";
		case "sequences" -> "Compare sequence options and establish the required next value against migrated keys and application allocation rules before writes resume.";
		case "mviews", "mviewLogs" -> "Verify base-object dependencies, refresh method/schedule, logs and target refresh results.";
		case "procedures", "functions", "packages", "packageBodies", "triggers", "types", "typeBodies", "views" ->
				"Retain executable definition or statement plus modeled components; compile in the target and test dependencies, dynamic SQL, privileges and business results.";
		default -> "Verify recreation, dependencies, privileges and runtime behavior in the target; retain the source definition where available.";
		};
	}
}
