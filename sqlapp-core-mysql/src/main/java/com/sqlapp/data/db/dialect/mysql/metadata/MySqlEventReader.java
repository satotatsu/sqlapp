/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.EventReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Event;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.db.dialect.mysql.sql.MySqlCreateEventFactory;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * MySqlのイベント読み込みクラス
 * 
 * @author satoh
 * 
 */
public class MySqlEventReader extends EventReader {

	protected MySqlEventReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Event> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Event> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Event obj = createEvent(rs, productVersionInfo);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(final ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("events.sql");
	}

	protected Event createEvent(ExResultSet rs, final ProductVersionInfo productVersionInfo) throws SQLException {
		Event obj = new Event(getString(rs, EVENT_NAME));
		obj.setCatalogName(getString(rs, "EVENT_CATALOG"));
		obj.setSchemaName(getString(rs, "EVENT_SCHEMA"));
		obj.setDefiner(rs.getString("DEFINER"));
		com.sqlapp.data.db.dialect.mysql.util.MySqlStoredObjectContext.read(rs, obj);
		obj.setIntervalField(getString(rs, "INTERVAL_FIELD"));
		String interval = getString(rs, "INTERVAL_VALUE");
		if (interval != null) {
			try {
				obj.setIntervalValue(Integer.valueOf(interval));
			} catch (NumberFormatException e) {
				obj.getSpecifics().put(MySqlCreateEventFactory.INTERVAL_VALUE, interval);
			}
		}
		if (productVersionInfo.gte(5, 1, 12)) {
			obj.setStatement(rs.getString("EVENT_DEFINITION"));
		} else {
			obj.setStatement(rs.getString("EVENT_BODY"));
		}
		obj.setExecuteAt(rs.getTimestamp("EXECUTE_AT"));
		obj.setEventType(getString(rs, "EVENT_TYPE"));
		obj.setOnCompletion(getString(rs, "ON_COMPLETION"));
		obj.setStarts(rs.getTimestamp("STARTS"));
		obj.setEnds(rs.getTimestamp("ENDS"));
		String status = getString(rs, "STATUS");
		if (!Set.of("ENABLED", "DISABLED", "SLAVESIDE_DISABLED").contains(status == null ? "" : status))
			throw new SQLException("Unsupported MySQL event status: " + status);
		obj.setEnable("ENABLED".equals(status));
		if ("SLAVESIDE_DISABLED".equals(status))
			obj.getSpecifics().put(MySqlCreateEventFactory.STATUS, status);
		obj.setLastExecuted(rs.getTimestamp("LAST_EXECUTED"));
		obj.setCreatedAt(rs.getTimestamp("CREATED"));
		obj.setLastAlteredAt(rs.getTimestamp("LAST_ALTERED"));
		obj.setRemarks(getString(rs, "EVENT_COMMENT"));
		return obj;
	}
}
