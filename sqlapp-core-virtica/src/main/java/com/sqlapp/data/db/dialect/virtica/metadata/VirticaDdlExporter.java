/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.virtica.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import com.sqlapp.data.schemas.AbstractSchemaObject;

/**
 * One scoped vendor export, retaining dependencies as one executable script.
 */
public final class VirticaDdlExporter {
	/**
	 * Exports once, then atomically assigns matched CREATE statements to the
	 * targets. The complete dependency script is also returned for review and
	 * archival.
	 */
	public String exportProjectionDefinitions(Connection connection,
			Collection<com.sqlapp.data.schemas.Projection> projections) throws SQLException {
		var targets = java.util.List.copyOf(projections);
		VirticaProjectionDdlMapper.validateTargets(targets);
		String script = exportObjects(connection, targets);
		applyProjectionDefinitions(script, targets);
		return script;
	}

	/**
	 * Offline script-to-model mapping; no SQL is executed and failures leave all
	 * definitions unchanged.
	 */
	public void applyProjectionDefinitions(String script, Collection<com.sqlapp.data.schemas.Projection> projections) {
		VirticaProjectionDdlMapper.apply(script, projections);
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
			if (object instanceof com.sqlapp.data.schemas.Routine<?>)
				throw new IllegalArgumentException(
						"Routine export requires overload signatures; use a vendor export with explicit signatures");
			String name = quote(object.getSchemaName()) + "." + quote(object.getName());
			if (object.getCatalogName() != null && !object.getCatalogName().isBlank())
				name = quote(object.getCatalogName()) + "." + name;
			names.add(name);
		}
		if (names.isEmpty())
			return "";
		Objects.requireNonNull(connection, "connection");
		try (var statement = connection.prepareStatement("SELECT EXPORT_OBJECTS('', ?, false)")) {
			statement.setString(1, String.join(",", names));
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
