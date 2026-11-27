/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TriggerReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.data.schemas.Table;

/** Reads SQLite triggers from the historical {@code sqlite_master} alias. */
public class SqliteTriggerReader extends TriggerReader {

	public SqliteTriggerReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Trigger> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final List<Trigger> result = list();
		final List<Trigger> unresolved = list();
		final String schemaName = getSchemaName(context) == null ? "main" : getSchemaName(context);
		final String requestedTrigger = getObjectName(context);
		final String sql = "SELECT name, tbl_name, sql FROM " + quoteIdentifier(schemaName) + ".sqlite_master "
				+ "WHERE type='trigger' ORDER BY name";
		try (var statement = connection.createStatement(); var resultSet = statement.executeQuery(sql)) {
			while (resultSet.next()) {
				if (requestedTrigger != null && !requestedTrigger.equalsIgnoreCase(resultSet.getString("name")))
					continue;
				final Trigger trigger = new Trigger(resultSet.getString("name"));
				trigger.setDialect(getDialect());
				trigger.setCatalogName(getCatalogName(context));
				trigger.setSchemaName(schemaName);
				trigger.setTable(new Table(resultSet.getString("tbl_name")).setSchemaName(schemaName));
				trigger.setActionOrientation("ROW");
				trigger.setActionReferenceOldRow("OLD");
				trigger.setActionReferenceNewRow("NEW");
				final String definition = resultSet.getString("sql");
				final String targetSchema = SqliteDdl.triggerTargetSchema(definition);
				if (targetSchema != null) {
					trigger.getTable().setSchemaName(targetSchema);
				} else if ("temp".equalsIgnoreCase(schemaName)) {
					unresolved.add(trigger);
				}
				var event = SqliteDdl.triggerEvent(definition);
				trigger.setActionTiming(event.get(0));
				trigger.getEventManipulation().add(event.get(1));
				// UPDATE OF, WHEN and the complete BEGIN/END block cannot be
				// reconstructed from the shared trigger fields without losing semantics.
				if (getReaderOptions().isReadDefinition() || getReaderOptions().isReadStatement()) {
					trigger.setDefinition(definition);
				}
				result.add(trigger);
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		if (!unresolved.isEmpty())
			resolveTemporaryTargets(connection, unresolved);
		return result;
	}

	private void resolveTemporaryTargets(Connection connection, List<Trigger> triggers) {
		// SQLite does not expose the bound database of an unqualified TEMP trigger.
		// Read all catalogs together, never once per trigger. Ambiguous bindings
		// cannot safely be inferred after schema changes.
		final List<String> catalogs = list();
		final var resolved = new java.util.IdentityHashMap<Trigger, String>();
		try (var statement = connection.createStatement(); var rows = statement.executeQuery("PRAGMA database_list")) {
			while (rows.next())
				catalogs.add(rows.getString("name"));
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		final String sql = catalogs.stream()
				.map(schema -> "SELECT '" + schema.replace("'", "''") + "' AS target_schema, name FROM "
						+ quoteIdentifier(schema) + ".sqlite_master WHERE type IN ('table','view')")
				.collect(java.util.stream.Collectors.joining(" UNION ALL "));
		try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
			while (rows.next()) {
				for (Trigger trigger : triggers) {
					if (!trigger.getTableName().equalsIgnoreCase(rows.getString("name")))
						continue;
					String schema = rows.getString("target_schema");
					if (resolved.put(trigger, schema) != null)
						throw new IllegalArgumentException("SQLite TEMP trigger " + trigger.getName()
								+ " has an ambiguous unqualified ON target; recreate it with ON schema.table");
					trigger.getTable().setSchemaName(schema);
				}
			}
			for (Trigger trigger : triggers) {
				if (!resolved.containsKey(trigger))
					throw new IllegalArgumentException(
							"SQLite TEMP trigger target was not found: " + trigger.getName());
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private String quoteIdentifier(final String value) {
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}
}
