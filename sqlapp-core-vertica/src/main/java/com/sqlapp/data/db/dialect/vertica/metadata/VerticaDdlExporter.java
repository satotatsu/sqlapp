/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.vertica.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import com.sqlapp.data.schemas.AbstractSchemaObject;

/**
 * One scoped vendor export, retaining dependencies as one executable script.
 */
public final class VerticaDdlExporter {
	/**
	 * Captures a complete schema dependency script as one authoritative definition.
	 * Does not export data or grants, and never executes the returned DDL.
	 */
	public String exportSchemaDefinition(Connection connection, com.sqlapp.data.schemas.Schema schema)
			throws SQLException {
		String script = exportSchemaObjects(connection, schema);
		String scope = schema.getName();
		String schedulingStates = null;
		VerticaFlexRawColumns.Capture flex = null;
		if (connection.getMetaData().getDatabaseMajorVersion() >= 25) {
			schedulingStates = new VerticaScheduleStateExporter().export(connection, scope);
			flex = VerticaFlexRawColumns.capture(connection, schema, script);
		}
		schema.setDefinition(script);
		if (schedulingStates != null)
			schema.getSpecifics().put("VERTICA_SCHEDULING_STATES", schedulingStates);
		else
			schema.getSpecifics().remove("VERTICA_SCHEDULING_STATES");
		if (flex != null)
			flex.apply(schema);
		return script;
	}

	String exportSchemaObjects(Connection connection, com.sqlapp.data.schemas.Schema schema) throws SQLException {
		Objects.requireNonNull(schema, "schema");
		// EXPORT_OBJECTS' single-schema scope does not resolve a quoted schema
		// like the qualified object scopes. Restrict this path to simple names.
		String scope = schema.getName();
		if (scope == null || !scope.matches("[A-Za-z_][A-Za-z0-9_$]*"))
			throw new IllegalArgumentException(
					"Vertica schema export requires a simple schema name; quoted schema scopes are not qualified");
		if (schema.getCatalogName() != null && !schema.getCatalogName().isBlank()
				&& !schema.getCatalogName().equalsIgnoreCase(connection.getCatalog()))
			throw new IllegalArgumentException("Schema export catalog must match the connected database");
		return exportScope(connection, scope);
	}

	/**
	 * Exports a text-index set once and assigns matching CREATE definitions
	 * atomically.
	 */
	public String exportTextIndexDefinitions(Connection connection, Collection<com.sqlapp.data.schemas.Index> indexes)
			throws SQLException {
		var targets = java.util.List.copyOf(indexes);
		VerticaTextIndexDdlMapper.targets(targets);
		String script = exportObjects(connection, targets);
		applyTextIndexDefinitions(script, targets);
		return script;
	}

	/** Offline mapping; no SQL execution and no partial definition assignment. */
	public void applyTextIndexDefinitions(String script, Collection<com.sqlapp.data.schemas.Index> indexes) {
		VerticaTextIndexDdlMapper.apply(script, indexes);
	}

	/**
	 * One export for resolved procedure overloads; full CREATE definitions are
	 * assigned atomically.
	 */
	public String exportProcedureDefinitions(Connection connection,
			Collection<com.sqlapp.data.schemas.Procedure> procedures) throws SQLException {
		var targets = java.util.List.copyOf(procedures);
		VerticaProcedureDdlMapper.targets(targets);
		String script = exportObjects(connection, targets);
		applyProcedureDefinitions(script, targets);
		return script;
	}

	/** Offline procedure mapping; preserves all previous definitions on failure. */
	public void applyProcedureDefinitions(String script, Collection<com.sqlapp.data.schemas.Procedure> procedures) {
		VerticaProcedureDdlMapper.apply(script, procedures);
	}

	/**
	 * Exports once, then atomically assigns matched CREATE statements to the
	 * targets. The complete dependency script is also returned for review and
	 * archival.
	 */
	public String exportProjectionDefinitions(Connection connection,
			Collection<com.sqlapp.data.schemas.Projection> projections) throws SQLException {
		var targets = java.util.List.copyOf(projections);
		VerticaProjectionDdlMapper.validateTargets(targets);
		String script = exportObjects(connection, targets);
		applyProjectionDefinitions(script, targets);
		return script;
	}

	/**
	 * Offline script-to-model mapping; no SQL is executed and failures leave all
	 * definitions unchanged.
	 */
	public void applyProjectionDefinitions(String script, Collection<com.sqlapp.data.schemas.Projection> projections) {
		VerticaProjectionDdlMapper.apply(script, projections);
	}

	/**
	 * Returns the complete server export without writing files or executing its
	 * DDL. Empty input returns an empty script without querying the database. Full
	 * schema and object names are required; catalog qualification, when supplied,
	 * is retained. The script can contain dependencies and must not be assigned
	 * wholesale to each object's definition. The caller owns and authorizes the
	 * supplied connection.
	 */
	public String exportObjects(Connection connection, Collection<? extends AbstractSchemaObject<?>> objects)
			throws SQLException {
		Objects.requireNonNull(objects, "objects");
		var names = new LinkedHashSet<String>();
		for (var object : objects) {
			Objects.requireNonNull(object, "export object");
			if (object instanceof com.sqlapp.data.schemas.Routine<?>
					&& !(object instanceof com.sqlapp.data.schemas.Procedure))
				throw new IllegalArgumentException(
						"Routine export requires overload signatures; use a vendor export with explicit signatures");
			String schema = object.getSchemaName();
			if (object instanceof com.sqlapp.data.schemas.Index index) {
				String indexSchema = index.getSpecifics().get("INDEX_SCHEMA_NAME", String.class);
				if (indexSchema != null)
					schema = indexSchema;
			}
			String name = quote(schema) + "." + quote(object.getName());
			if (object.getCatalogName() != null && !object.getCatalogName().isBlank())
				name = quote(object.getCatalogName()) + "." + name;
			if (object instanceof com.sqlapp.data.schemas.Procedure procedure) {
				VerticaProcedureDdlMapper.targets(java.util.List.of(procedure));
				var types = new java.util.ArrayList<String>();
				for (var argument : procedure.getArguments()) {
					var builder = com.sqlapp.data.db.dialect.vertica.DialectHolder.defaultDialect11_1_1
							.createSqlBuilder();
					builder.typeDefinition(argument);
					// Overload identities use base types, not CREATE-column size defaults.
					types.add(builder.toString().replaceAll("\\([^()]*\\)", ""));
				}
				name += "(" + String.join(",", types) + ")";
			}
			names.add(name);
		}
		if (names.isEmpty())
			return "";
		return exportScope(connection, String.join(",", names));
	}

	private String exportScope(Connection connection, String scope) throws SQLException {
		Objects.requireNonNull(connection, "connection");
		try (var statement = connection.prepareStatement("SELECT EXPORT_OBJECTS('', ?, false)")) {
			statement.setString(1, scope);
			try (var result = statement.executeQuery()) {
				if (!result.next())
					throw new SQLException("Vertica EXPORT_OBJECTS returned no script row");
				String script = result.getString(1);
				if (script == null || script.isBlank())
					throw new SQLException("Vertica EXPORT_OBJECTS returned an empty script");
				if (result.next())
					throw new SQLException("Vertica EXPORT_OBJECTS returned multiple script rows");
				return script;
			}
		}
	}

	private String quote(String identifier) {
		if (identifier == null || identifier.isBlank())
			throw new IllegalArgumentException("Vertica DDL export requires schema and object names");
		// The vendor scope is a comma-delimited name list; quoted commas are not
		// qualified offline, so reject them instead of broadening export scope.
		if (identifier.indexOf(',') >= 0 || identifier.indexOf('\0') >= 0)
			throw new IllegalArgumentException(
					"Vertica export scope cannot safely represent this identifier: " + identifier);
		return "\"" + identifier.replace("\"", "\"\"") + "\"";
	}
}
