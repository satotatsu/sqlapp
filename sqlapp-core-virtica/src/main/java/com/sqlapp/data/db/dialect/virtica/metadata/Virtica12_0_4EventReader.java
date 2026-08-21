/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.EventReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Event;
import com.sqlapp.data.schemas.EventType;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Reads scheduled stored-procedure triggers, available since Vertica 12.0.4. */
public class Virtica12_0_4EventReader extends EventReader {

	protected Virtica12_0_4EventReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Event> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("events12_0_4.sql");
		List<Event> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				result.add(createEvent(rs));
			}
		});
		return result;
	}

	protected Event createEvent(ExResultSet rs) throws SQLException {
		Event event = new Event(getString(rs, "TRIGGER_NAME"));
		event.setSchemaName(getString(rs, SCHEMA_NAME));
		event.setDefiner(getString(rs, "OWNER"));
		event.setEnable(rs.getBoolean("TRIGGER_ENABLED")
				&& rs.getBoolean("SCHEDULE_ENABLED"));
		event.setEventType("CRON".equalsIgnoreCase(getString(rs, "DATE_TIME_TYPE"))
				? EventType.Recurring : EventType.OneTime);
		setSpecifics(rs, "PROCEDURE_NAME", event);
		setSpecifics(rs, "PROCEDURE_ARGS", event);
		setSpecifics(rs, "SCHEDULE_NAME", event);
		setSpecifics(rs, "DATE_TIME_TYPE", event);
		setSpecifics(rs, "DATE_TIME_STRING", event);
		return event;
	}
}
