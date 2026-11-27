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
	public InformixTriggerReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Trigger> doGetAll(final Connection connection, final ParametersContext context,
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
					final String state = getString(rs, "trigger_state");
					if (state != null && !"E".equals(state) && !"D".equals(state))
						throw new IllegalStateException("Unknown Informix trigger mode: " + state);
					trigger.setEnable(!"D".equals(state));
					setEvent(trigger, getString(rs, "trigger_event"));
					trigger.setActionReferenceOldRow(getString(rs, "old_reference"));
					trigger.setActionReferenceNewRow(getString(rs, "new_reference"));
					triggerText = new TriggerText(trigger);
					triggers.put(id, triggerText);
				}
				String dataKey = getString(rs, "data_key");
				if ("D".equals(dataKey)) {
					triggerText.definition.append(text(rs));
				} else if ("A".equals(dataKey)) {
					triggerText.statement.append(text(rs));
				}
			}
		});
		List<Trigger> result = list();
		triggers.values().forEach(triggerText -> {
			Trigger trigger = triggerText.trigger;
			String header = triggerText.definition.toString().stripTrailing();
			String statement = triggerText.statement.toString().stripTrailing();
			if (getReaderOptions().isReadDefinition() || getReaderOptions().isReadStatement()) {
				if (header.isBlank() || statement.isBlank())
					throw new IllegalStateException("Incomplete Informix trigger text: " + trigger.getName());
				// D is only the header; A contains all action phases. Neither is executable
				// alone.
				trigger.setDefinition(header + "\n" + statement);
			}
			if (getReaderOptions().isReadStatement()) {
				trigger.setStatement(statement);
			}
			result.add(trigger);
		});
		return result;
	}

	private static String text(final ExResultSet rs) throws SQLException {
		final String text = rs.getString("trigger_text");
		if (text == null)
			throw new IllegalStateException("Missing Informix trigger text segment");
		return text; // Preserve whitespace at the boundary of the catalog's 256-character chunks.
	}

	private static void setEvent(final Trigger trigger, final String event) {
		if (event == null)
			throw new IllegalStateException("Missing Informix trigger event");
		String normalized = event.toUpperCase(Locale.ROOT);
		switch (normalized) {
		case "I" -> trigger.getEventManipulation().add("INSERT");
		case "U" -> trigger.getEventManipulation().add("UPDATE");
		case "D" -> trigger.getEventManipulation().add("DELETE");
		case "S" -> trigger.getEventManipulation().add("SELECT");
		default -> throw new UnsupportedOperationException("Unsupported Informix trigger event: " + event);
		}
		if (!event.equals(normalized)) {
			trigger.setActionTiming("INSTEAD OF");
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
