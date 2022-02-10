/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.information_schema.metadata;

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
 * INFORMATION_SCHEMAのトリガー読み込みクラス
 * 
 * @author satoh
 * 
 */
public class ISTriggerReader extends TriggerReader {

	protected ISTriggerReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Trigger> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final List<Trigger> result = list();
		final Dialect dialect = this.getDialect();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Trigger trigger = createTrigger(rs);
				trigger.setDialect(dialect);
				result.add(trigger);
			}
		});
		return result;
	}

	protected Trigger createTrigger(ExResultSet rs) throws SQLException {
		Trigger trigger = new Trigger(getString(rs, TRIGGER_NAME));
		trigger.setCatalogName(getString(rs, "TRIGGER_CATALOG"));
		trigger.setSchemaName(getString(rs, "TRIGGER_SCHEMA"));
		trigger.getEventManipulation().add(getString(rs, EVENT_MANIPULATION));
		trigger.setTableSchemaName(getString(rs, "EVENT_OBJECT_SCHEMA"));
		trigger.setTableName(getString(rs, "EVENT_OBJECT_TABLE"));
		//trigger.setTableCatalog(getString(rs, "EVENT_OBJECT_CATALOG"));
		trigger.setActionCondition(getString(rs, ACTION_CONDITION));
		trigger.setStatement(getString(rs, ACTION_STATEMENT));
		trigger.setActionTiming(getString(rs, ACTION_TIMING));
		trigger.setActionOrientation(getString(rs, ACTION_ORIENTATION));
		trigger.setActionReferenceOldRow(getString(rs, ACTION_REFERENCE_OLD_ROW));
		trigger.setActionReferenceNewRow(getString(rs, ACTION_REFERENCE_NEW_ROW));
		trigger.setCreatedAt(rs.getTimestamp("CREATED"));
		return trigger;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache(ISTriggerReader.class).getString(
				"triggers.sql");
		return node;
	}
}
