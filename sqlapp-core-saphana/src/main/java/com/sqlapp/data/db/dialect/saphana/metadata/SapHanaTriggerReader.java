/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-saphana.
 *
 * sqlapp-core-saphana is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-saphana is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-saphana.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.saphana.metadata;

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
 * SAP HANA Trigger Reader
 * 
 * @author satoh
 * 
 */
public class SapHanaTriggerReader extends TriggerReader {

	protected SapHanaTriggerReader(Dialect dialect) {
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
		return getSqlNodeCache().getString("triggers.sql");
	}

	protected Trigger createTrigger(ExResultSet rs) throws SQLException {
		Trigger trigger = new Trigger(getString(rs, TRIGGER_NAME));
		trigger.setSchemaName(getString(rs, SCHEMA_NAME));
		trigger.setId("" + rs.getLong("TRIGGER_OID"));
		String actionLevel = rs.getString("TRIGGERED_ACTION_LEVEL");
		trigger.setActionOrientation(actionLevel);
		String event = rs.getString("TRIGGER_EVENT");
		trigger.addEventManipulation(event);
		String actionTime = getString(rs, "TRIGGER_ACTION_TIME");
		trigger.setActionTiming(actionTime);
		trigger.setTableName(getString(rs, "SUBJECT_TABLE_NAME"));
		trigger.setTableSchemaName(getString(rs, "SUBJECT_TABLE_SCHEMA"));
		trigger.setDefinition(getString(rs, "DEFINITION"));
		return trigger;
	}
}
