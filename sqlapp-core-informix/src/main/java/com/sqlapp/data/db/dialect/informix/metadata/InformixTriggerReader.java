/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-informix.
 */
package com.sqlapp.data.db.dialect.informix.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TriggerReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Reads Informix triggers and their catalog-stored SQL text. */
public class InformixTriggerReader extends TriggerReader {
	private static final Pattern TIMING_PATTERN = Pattern.compile(
			"\\b(BEFORE|AFTER|INSTEAD\\s+OF)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern ORIENTATION_PATTERN = Pattern.compile(
			"\\bFOR\\s+EACH\\s+(ROW|STATEMENT)\\b", Pattern.CASE_INSENSITIVE);

	public InformixTriggerReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Trigger> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("triggers.sql");
		Map<Integer, TriggerText> triggers = new LinkedHashMap<>();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				int id = rs.getInt("trigger_id");
				TriggerText triggerText = triggers.get(id);
				if (triggerText == null) {
					Trigger trigger = new Trigger(getString(rs, TRIGGER_NAME));
					trigger.setCatalogName(getString(rs, CATALOG_NAME));
					trigger.setSchemaName(getString(rs, SCHEMA_NAME));
					trigger.setTableSchemaName(getString(rs, "table_schema_name"));
					trigger.setTableName(getString(rs, TABLE_NAME));
					setEvent(trigger, getString(rs, "trigger_event"));
					trigger.setActionReferenceOldRow(getString(rs, "old_reference"));
					trigger.setActionReferenceNewRow(getString(rs, "new_reference"));
					triggerText = new TriggerText(trigger);
					triggers.put(id, triggerText);
				}
				String dataKey = getString(rs, "data_key");
				if ("D".equals(dataKey)) {
					triggerText.definition.append(getString(rs, "trigger_text"));
				} else if ("A".equals(dataKey)) {
					triggerText.statement.append(getString(rs, "trigger_text"));
				}
			}
		});
		List<Trigger> result = list();
		triggers.values().forEach(triggerText -> {
			Trigger trigger = triggerText.trigger;
			String definition = triggerText.definition.toString();
			String statement = triggerText.statement.toString();
			if (getReaderOptions().isReadDefinition()) {
				trigger.setDefinition(definition);
			}
			if (getReaderOptions().isReadStatement()) {
				trigger.setStatement(statement);
			}
			setHeaderAttributes(trigger, definition + statement);
			result.add(trigger);
		});
		return result;
	}

	private static void setEvent(final Trigger trigger, final String event) {
		String normalized = event.toUpperCase(Locale.ROOT);
		switch (normalized) {
		case "I" -> trigger.getEventManipulation().add("INSERT");
		case "U" -> trigger.getEventManipulation().add("UPDATE");
		case "D" -> trigger.getEventManipulation().add("DELETE");
		case "S" -> trigger.getEventManipulation().add("SELECT");
		default -> { }
		}
		if (!event.equals(normalized)) {
			trigger.setActionTiming("INSTEAD OF");
		} else {
			trigger.setActionTiming("AFTER");
		}
	}

	private static void setHeaderAttributes(final Trigger trigger, final String definition) {
		Matcher timing = TIMING_PATTERN.matcher(definition);
		if (timing.find()) {
			trigger.setActionTiming(timing.group(1).toUpperCase(Locale.ROOT)
					.replaceAll("\\s+", " "));
		}
		Matcher orientation = ORIENTATION_PATTERN.matcher(definition);
		if (orientation.find()) {
			trigger.setActionOrientation(orientation.group(1).toUpperCase(Locale.ROOT));
		}
	}

	private static class TriggerText {
		private final Trigger trigger;
		private final StringBuilder definition = new StringBuilder();
		private final StringBuilder statement = new StringBuilder();

		private TriggerText(final Trigger trigger) {
			this.trigger = trigger;
		}
	}
}
