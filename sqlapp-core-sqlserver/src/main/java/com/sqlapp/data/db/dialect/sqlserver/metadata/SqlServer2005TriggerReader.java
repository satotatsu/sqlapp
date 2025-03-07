/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.trim;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TriggerReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;

/**
 * SqlServer2005のトリガー読み込み
 * 
 * @author satoh
 * 
 */
public class SqlServer2005TriggerReader extends TriggerReader {

	protected SqlServer2005TriggerReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Trigger> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Trigger> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Trigger trigger = createTrigger(rs);
				result.add(trigger);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("triggers2005.sql");
	}

	protected Trigger createTrigger(ExResultSet rs) throws SQLException {
		String trigger_name = getString(rs, TRIGGER_NAME);
		String definition = getString(rs, "definition");
		String parentName = getString(rs, "parent_name");
		String type = trim(rs.getString("type"));
		String parent_class_desc = getString(rs, "parent_class_desc");
		boolean fireringTime = rs.getBoolean("is_instead_of_trigger");
		Trigger trigger = new Trigger(trigger_name);
		trigger.setCatalogName(getString(rs, CATALOG_NAME));
		trigger.setSchemaName(getString(rs, SCHEMA_NAME));
		if ("TR".equals(type)) {
			// SQLトリガ
		}
		if ("TA".equals(type)) {
			// CLRトリガ
		}
		if (fireringTime) {
			trigger.setActionTiming("INSTEAD OF");
		} else {
			trigger.setActionTiming("AFTER");
		}
		List<String> events=getEventManipulation(rs);
		trigger.getEventManipulation().addAll(events);
		if ("OBJECT_OR_COLUMN".equalsIgnoreCase(parent_class_desc)) {
			trigger.setTableName(parentName);
			trigger.setActionOrientation("ROW");
		} else if ("DATABASE".equalsIgnoreCase(parent_class_desc)) {
			// TODO
			// trigger.setObjectName()?
			trigger.setActionOrientation("STATEMENT");
		}
		trigger.setClassName(getString(rs, "assembly_class"));
		trigger.setMethodName(getString(rs, "assembly_method"));
		trigger.setEnable(!rs.getBoolean("is_disabled"));
		// trigger.setActionReferenceOldRow(getString(rs,
		// "OLDREFERENCINGNAME"));
		// inserted updated deleted
		// trigger.setActionReferenceNewRow(getString(rs,
		// "NEWREFERENCINGNAME"));
		trigger.setCreatedAt(rs.getTimestamp("create_date"));
		trigger.setLastAlteredAt(rs.getTimestamp("modify_date"));
		trigger.setStatement(SqlServerUtils.getTriggerStatement(definition));
		setSpecifics(rs, "is_not_for_replication", trigger);
		return trigger;
	}

	private List<String> getEventManipulation(ExResultSet rs) throws SQLException {
		List<Event> events = CommonUtils.list(4);
		if (rs.getBoolean("is_insert")) {
			Event event = new Event("INSERT");
			if (rs.getBoolean("insert_is_first")) {
				event.setOrder(0);
			}
			if (rs.getBoolean("insert_is_last")) {
				event.setOrder(Integer.MAX_VALUE);
			}
			events.add(event);
		}
		if (rs.getBoolean("is_update")) {
			Event event = new Event("UPDATE");
			if (rs.getBoolean("update_is_first")) {
				event.setOrder(0);
			}
			if (rs.getBoolean("update_is_last")) {
				event.setOrder(Integer.MAX_VALUE);
			}
			events.add(event);
		}
		if (rs.getBoolean("is_delete")) {
			Event event = new Event("DELETE");
			if (rs.getBoolean("delete_is_first")) {
				event.setOrder(0);
			}
			if (rs.getBoolean("delete_is_last")) {
				event.setOrder(Integer.MAX_VALUE);
			}
			events.add(event);
		}
		Collections.sort(events);
		List<String> ret = CommonUtils.list(events.size());
		for (int i = 0; i < events.size(); i++) {
			ret.add(events.get(i).getName());
		}
		return ret;
	}

	static class Event implements Comparable<Event> {
		private final String name;
		private int order = Integer.MAX_VALUE / 2;

		Event(String name) {
			this.name = name;
		}

		/**
		 * @return the order
		 */
		protected int getOrder() {
			return order;
		}

		/**
		 * @param order
		 *            the order to set
		 */
		protected void setOrder(int order) {
			this.order = order;
		}

		/**
		 * @return the name
		 */
		protected String getName() {
			return name;
		}

		@Override
		public int compareTo(Event o) {
			if (this.getOrder() > o.getOrder()) {
				return 1;
			} else if (this.getOrder() == o.getOrder()) {
				return 0;
			} else {
				return -1;
			}
		}

	}

}
