/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.oracle.metadata;

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
 * Oracleのトリガー作成クラス
 * 
 * @author satoh
 * 
 */
public class OracleTriggerReader extends TriggerReader {

	protected OracleTriggerReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Trigger> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final boolean dba = OracleMetadataUtils.hasSelectPrivilege(connection,
				this.getDialect(), "SYS", "DBA_TRIGGERS");
		SqlNode node = getSqlSqlNode(productVersionInfo);
		OracleMetadataUtils.setDba(dba, context);
		final List<Trigger> result = list();
		context.put("objectName", this.getObjectName(context));
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
		String name = getString(rs, TRIGGER_NAME);
		String triggerType = getString(rs, "TRIGGER_TYPE");
		String status = getString(rs, "STATUS");
		String actionCondition = getString(rs, "WHEN_CLAUSE");
		String tableName = getString(rs, TABLE_NAME);
		String tableOwner = getString(rs, "TABLE_OWNER");
		Trigger trigger = new Trigger(name);
		trigger.addEventManipulation(getString(rs, "TRIGGERING_EVENT"));
		if (triggerType.contains("ROW")) {
			trigger.setActionOrientation("ROW");
		} else if (triggerType.contains("STATEMENT")) {
			trigger.setActionOrientation("STATEMENT");
		} else if (triggerType.contains("EVENT")) {
			trigger.setActionOrientation("EVENT");
		} else {
			trigger.setActionOrientation(triggerType);
		}
		if (triggerType.startsWith("BEFORE")) {
			trigger.setActionTiming("BEFORE");
		} else if (triggerType.startsWith("AFTER")) {
			trigger.setActionOrientation("AFTER");
		}
		trigger.setActionCondition(actionCondition);
		trigger.setEnable("ENABLED".equalsIgnoreCase(status));
		trigger.setActionReferenceOldRow(":OLD");
		trigger.setActionReferenceNewRow(":NEW");
		trigger.setTableName(tableName);
		trigger.setTableSchemaName(tableOwner);
		trigger.setStatement(getString(rs, "TRIGGER_BODY"));
		trigger.addDefinition(getString(rs, "DESCRIPTION"));
		return trigger;
	}
}
