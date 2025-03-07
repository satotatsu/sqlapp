/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres.metadata;

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
 * Postgresのトリガー読み込み
 * 
 * @author satoh
 * 
 */
public class PostgresTriggerReader extends TriggerReader {

	protected PostgresTriggerReader(Dialect dialect) {
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

	protected Trigger createTrigger(ExResultSet rs) throws SQLException {
		Trigger obj = new Trigger(getString(rs, TRIGGER_NAME));
		obj.setDialect(this.getDialect());
		obj.setSpecificName(getString(rs, "oid"));
		// trigger.setCatalogName(getString(rs, "trigger_catalog"));
		obj.setSchemaName(getString(rs, "trigger_schema"));
		obj.setActionOrientation(getString(rs, "action_orientation"));
		obj.setActionTiming(getString(rs, "condition_timing"));
		obj.setStatement(getString(rs, "action_statement"));
		obj.addEventManipulation(getString(rs, "is_insert"));
		obj.addEventManipulation(getString(rs, "is_update"));
		obj.addEventManipulation(getString(rs, "is_delete"));
		obj.setRemarks(getString(rs, "remarks"));
		// A:常にトリガーが起動、O:起点モードとローカルモードでトリガが起動、D:無効、R:replicaモード
		obj.setEnable(!"D".equalsIgnoreCase(getString(rs,
				"tgenabled")));
		return obj;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("triggers.sql");
	}
}
