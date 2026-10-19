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

/** Reads SQLite triggers from the historical {@code sqlite_master} alias. */
public class SqliteTriggerReader extends TriggerReader {

	public SqliteTriggerReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Trigger> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final List<Trigger> result = list();
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
				trigger.setTableSchemaName(schemaName);
				trigger.setTableName(resultSet.getString("tbl_name"));
				trigger.setActionOrientation("ROW");
				trigger.setActionReferenceOldRow("OLD");
				trigger.setActionReferenceNewRow("NEW");
				final String definition = resultSet.getString("sql");
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
			return result;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private String quoteIdentifier(final String value) {
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}
}
