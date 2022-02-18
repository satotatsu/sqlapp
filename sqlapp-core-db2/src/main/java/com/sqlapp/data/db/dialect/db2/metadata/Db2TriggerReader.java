/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.db2.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TriggerReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Trigger;

/**
 * DB2のトリガー読み込みクラス
 * 
 * @author satoh
 * 
 */
public class Db2TriggerReader extends TriggerReader {

	protected Db2TriggerReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Trigger> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final List<Trigger> result = list();
		final Dialect dbDialect = this.getDialect();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Trigger trigger = createTrigger(rs);
				trigger.setDialect(dbDialect);
				result.add(trigger);
			}
		});
		return result;
	}

	protected Trigger createTrigger(ExResultSet rs) throws SQLException {
		Trigger trigger = new Trigger(getString(rs, TRIGGER_NAME));
		trigger.setSchemaName(getString(rs, SCHEMA_NAME));
		trigger.getEventManipulation().add(getString(rs, EVENT_MANIPULATION));
		trigger.setTableSchemaName(getString(rs, EVENT_OBJECT_SCHEMA));
		trigger.setTableName(getString(rs, EVENT_OBJECT_TABLE));
		trigger.setDefinition(getString(rs, ACTION_STATEMENT));
		trigger.setActionTiming(getString(rs, ACTION_TIMING));
		trigger.setActionOrientation(getString(rs, ACTION_ORIENTATION));
		trigger.setCreatedAt(rs.getTimestamp("CREATE_TIME"));
		trigger.setValid("Y".equalsIgnoreCase(getString(rs, "VALID")));
		trigger.setRemarks(getString(rs, REMARKS));
		return trigger;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("triggers.sql");
		return node;
	}
}
