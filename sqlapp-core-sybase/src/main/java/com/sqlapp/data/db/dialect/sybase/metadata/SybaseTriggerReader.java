/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sybase.
 */
package com.sqlapp.data.db.dialect.sybase.metadata;

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

/** Reads ASE triggers from sysobjects and their source from syscomments. */
public class SybaseTriggerReader extends TriggerReader {
	private static final Pattern EVENT_PATTERN = Pattern.compile(
			"\\b(?:FOR|INSTEAD\\s+OF)\\s+((?:INSERT|UPDATE|DELETE)(?:\\s*,\\s*(?:INSERT|UPDATE|DELETE))*)",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern INSTEAD_OF_PATTERN = Pattern.compile(
			"\\bINSTEAD\\s+OF\\b", Pattern.CASE_INSENSITIVE);

	protected SybaseTriggerReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Trigger> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("triggers.sql");
		Map<Integer, TriggerSource> sources = new LinkedHashMap<>();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				int id = rs.getInt("trigger_id");
				TriggerSource source = sources.get(id);
				if (source == null) {
					Trigger trigger = new Trigger(getString(rs, TRIGGER_NAME));
					trigger.setCatalogName(getString(rs, CATALOG_NAME));
					trigger.setSchemaName(getString(rs, SCHEMA_NAME));
					trigger.setTableSchemaName(getString(rs, "table_schema_name"));
					trigger.setTableName(getString(rs, TABLE_NAME));
					trigger.setActionOrientation("ROW");
					trigger.setCreatedAt(rs.getTimestamp("created"));
					source = new TriggerSource(trigger);
					sources.put(id, source);
				}
				source.definition.append(getString(rs, "definition"));
			}
		});
		List<Trigger> result = list();
		sources.values().forEach(source -> {
			String definition = source.definition.toString();
			Trigger trigger = source.trigger;
			if (getReaderOptions().isReadDefinition()) {
				trigger.setDefinition(definition);
			}
			if (getReaderOptions().isReadStatement()) {
				trigger.setStatement(definition);
			}
			trigger.setActionTiming(INSTEAD_OF_PATTERN.matcher(definition).find()
					? "INSTEAD OF" : "AFTER");
			Matcher events = EVENT_PATTERN.matcher(definition);
			if (events.find()) {
				for (String event : events.group(1).split("\\s*,\\s*")) {
					trigger.getEventManipulation().add(event.toUpperCase(Locale.ROOT));
				}
			}
			result.add(trigger);
		});
		return result;
	}

	private static class TriggerSource {
		private final Trigger trigger;
		private final StringBuilder definition = new StringBuilder();

		private TriggerSource(final Trigger trigger) {
			this.trigger = trigger;
		}
	}
}
