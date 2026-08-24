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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TriggerReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Trigger;

/** Reads SQLite triggers from the historical {@code sqlite_master} alias. */
public class SqliteTriggerReader extends TriggerReader {
	private static final Pattern EVENT_PATTERN = Pattern.compile(
			"(?is)\\b(BEFORE|AFTER|INSTEAD\\s+OF)\\s+(INSERT|UPDATE|DELETE)\\b");
	private static final Pattern BODY_PATTERN = Pattern.compile(
			"(?is).*\\bBEGIN\\b(.*)\\bEND\\s*$");

	public SqliteTriggerReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Trigger> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final List<Trigger> result = list();
		final String schemaName = getSchemaName() == null ? "main" : getSchemaName();
		final String sql = "SELECT name, tbl_name, sql FROM "
				+ quoteIdentifier(schemaName) + ".sqlite_master "
				+ "WHERE type='trigger' ORDER BY name";
		try (var statement = connection.createStatement();
				var resultSet = statement.executeQuery(sql)) {
			while (resultSet.next()) {
				final Trigger trigger = new Trigger(resultSet.getString("name"));
				trigger.setSchemaName(schemaName);
				trigger.setTableSchemaName(schemaName);
				trigger.setTableName(resultSet.getString("tbl_name"));
				trigger.setActionOrientation("ROW");
				trigger.setActionReferenceOldRow("OLD");
				trigger.setActionReferenceNewRow("NEW");
				final String definition = resultSet.getString("sql");
				final Matcher matcher = EVENT_PATTERN.matcher(definition);
				if (matcher.find()) {
					trigger.setActionTiming(matcher.group(1).replaceAll("\\s+", " ")
							.toUpperCase(Locale.ROOT));
					trigger.getEventManipulation().add(matcher.group(2));
				}
				if (getReaderOptions().isReadDefinition()) {
					trigger.setDefinition(definition);
				}
				if (getReaderOptions().isReadStatement()) {
					final Matcher bodyMatcher = BODY_PATTERN.matcher(definition);
					trigger.setStatement(bodyMatcher.matches()
							? bodyMatcher.group(1).trim() : definition);
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
