/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.set;
import static com.sqlapp.util.CommonUtils.trim;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TriggerReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;

public class FirebirdTriggerReader extends TriggerReader {

	protected FirebirdTriggerReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Trigger> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Trigger> result = CommonUtils.list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String name = trim(getString(rs, "RDB$TRIGGER_NAME"));
				int triggerType = rs.getInt("RDB$TRIGGER_TYPE");
				int inactive = rs.getInt("RDB$TRIGGER_INACTIVE");
				Trigger trigger = new Trigger(name);
				trigger.setTableName(trim(getString(rs, "RDB$RELATION_NAME")));
				trigger.setActionTiming(getActionTimingString(triggerType));
				trigger.setEventManipulation(getEventManipulationString(triggerType));
				trigger.setActionOrientation("ROW");
				trigger.setEnable(0 == inactive);
				trigger.setActionReferenceOldRow("OLD");
				trigger.setActionReferenceNewRow("NEW");
				trigger.setStatement(getStatement(rs));
				setSpecifics(rs, "RDB$TRIGGER_SEQUENCE", "POSITION", trigger);
				result.add(trigger);
			}
		});
		return result;
	}

	private static final Pattern STATEMENT_PATTERN = Pattern.compile("AS\\s+(.*)",
			Pattern.CASE_INSENSITIVE + Pattern.MULTILINE + Pattern.DOTALL);

	private String getStatement(ExResultSet rs) throws SQLException {
		String statement = trim(getString(rs, "RDB$TRIGGER_SOURCE"));
		Matcher mathcer = STATEMENT_PATTERN.matcher(statement);
		if (mathcer.matches()) {
			return mathcer.group(1);
		}
		return statement;
	}

	protected SqlNode getSqlSqlNode(final ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("triggers.sql");
	}

	/**
	 * トリガーのタイミング文字列の取得
	 * 
	 * @param type
	 */
	private String getActionTimingString(int type) {
		if (type <= 0 || type >= 8000)
			return "";
		if (type % 2 == 0) {
			return "AFTER";
		}
		return "BEFORE";
	}

	private Set<String> getEventManipulationString(int type) {
		if (type <= 0)
			return set();
		Set<String> result = set();
		switch (type) {
		case 8192:
			result.add("ON CONNECT");
			return result;
		case 8193:
			result.add("ON DISCONNECT");
			return result;
		case 8194:
			result.add("ON TRANSACTION START");
			return result;
		case 8195:
			result.add("ON TRANSACTION COMMIT");
			return result;
		case 8196:
			result.add("ON TRANSACTION ROLLBACK");
			return result;
		}
		int tmp = type & 7; // 111 でマスク
		String action = getTriggerEvent(tmp);
		result.add(action);
		tmp = type >> 3;
		if (tmp == 0) {
			return result;
		}
		tmp = tmp & 3; // 11 でマスク
		action = getTriggerEvent(tmp);
		if (!isEmpty(action)) {
			result.add(action);
		}
		tmp = type >> 5;
		if (tmp == 0) {
			return result;
		}
		tmp = tmp & 3; // 11 でマスク
		action = getTriggerEvent(tmp);
		if (!isEmpty(action)) {
			result.add(action);
		}
		return result;
	}

	private String getTriggerEvent(int tmp) {
		switch (tmp) {
		case 1:
		case 2:
			return "INSERT";
		case 3:
		case 4:
			return "UPDATE";
		case 5:
		case 6:
			return "DELETE";
		default:
			return "";
		}
	}

}
