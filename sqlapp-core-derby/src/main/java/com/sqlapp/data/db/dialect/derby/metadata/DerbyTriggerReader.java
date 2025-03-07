/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-derby.
 *
 * sqlapp-core-derby is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-derby is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-derby.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.derby.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TriggerReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Derbyのトリガー読み込み
 * 
 * @author satoh
 * 
 */
public class DerbyTriggerReader extends TriggerReader {

	protected DerbyTriggerReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Trigger> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("triggers.sql");
		final List<Trigger> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String trigger_name = getString(rs, "TRIGGERNAME");
				// String definition = getString(rs, "TRIGGERDEFINITION");
				String table_name = getString(rs, "TABLENAME");
				String event = getString(rs, "EVENT");
				String type = getString(rs, "TYPE");
				String fireringTime = getString(rs, "FIRINGTIME");
				Trigger trigger = new Trigger(trigger_name);
				trigger.setSchemaName(getString(rs, "SCHEMANAME"));
				if ("A".equalsIgnoreCase(fireringTime)) {
					trigger.setActionTiming("AFTER");
				} else {
					trigger.setActionTiming("BEFORE");
				}
				if ("I".equalsIgnoreCase(event)) {
					trigger.getEventManipulation().add("INSERT");
				} else if ("U".equalsIgnoreCase(event)) {
					trigger.getEventManipulation().add("UPDATE");
				} else if ("D".equalsIgnoreCase(event)) {
					trigger.getEventManipulation().add("DELETE");
				}
				if ("R".equalsIgnoreCase(type)) {
					trigger.setActionOrientation("ROW");
				} else {
					trigger.setActionOrientation("STATEMENT");
				}
				trigger.setEnable("E".equalsIgnoreCase(getString(rs, "STATE")));
				trigger.setActionReferenceOldRow(getString(rs, "OLDREFERENCINGNAME"));
				trigger.setActionReferenceNewRow(getString(rs, "NEWREFERENCINGNAME"));
				trigger.setCreatedAt(rs.getTimestamp("CREATIONTIMESTAMP"));
				trigger.setTableName(table_name);
				trigger.setDefinition(getString(rs, "TRIGGERDEFINITION"));
				result.add(trigger);
			}
		});
		return result;
	}

}
