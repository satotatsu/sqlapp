/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-h2.
 *
 * sqlapp-core-h2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-h2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-h2.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.h2.metadata;

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
 * H2のトリガー作成クラス
 * 
 * @author satoh
 * 
 */
public class H2TriggerReader extends TriggerReader {

	protected H2TriggerReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Trigger> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final Dialect dialect = this.getDialect();
		final List<Trigger> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Trigger trigger = new Trigger(getString(rs, TRIGGER_NAME));
				trigger.setDialect(dialect);
				String before = getString(rs, "BEFORE");
				trigger.setActionTiming(before);
				trigger.getEventManipulation().add(
						getString(rs, "TRIGGER_TYPE"));
				trigger.setCatalogName(getString(rs, "TRIGGER_CATALOG"));
				trigger.setSchemaName(getString(rs, "TRIGGER_SCHEMA"));
				trigger.setTableSchemaName(getString(rs, "TABLE_SCHEMA"));
				trigger.setTableName(getString(rs, TABLE_NAME));
				trigger.setClassName(getString(rs, "JAVA_CLASS"));
				trigger.setDefinition(getString(rs, "SQL"));
				trigger.setRemarks(getString(rs, REMARKS));
				result.add(trigger);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("triggers.sql");
	}

}
