/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-firebird.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.firebird.metadata;

import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.trim;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
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
				long triggerType = rs.getLong("RDB$TRIGGER_TYPE");
				int inactive = rs.getInt("RDB$TRIGGER_INACTIVE");
				Trigger trigger = new Trigger(name);
				trigger.setTableName(trim(getString(rs, "RDB$RELATION_NAME")));
				trigger.setActionTiming(getActionTimingString(triggerType));
				trigger.setEventManipulation(getEventManipulationString(triggerType));
				if (!isEmpty(trigger.getTableName())) {
					trigger.setActionOrientation("ROW");
					trigger.setActionReferenceOldRow("OLD");
					trigger.setActionReferenceNewRow("NEW");
				}
				trigger.setEnable(0 == inactive);
				if (getReaderOptions().isReadStatement()) {
					trigger.setStatement(getStatement(rs));
				}
				trigger.setRemarks(getString(rs, "RDB$DESCRIPTION"));
				setSpecifics(rs, "RDB$TRIGGER_SEQUENCE", "POSITION", trigger);
				readVersionProperties(rs, trigger);
				result.add(trigger);
			}
		});
		return result;
	}

	protected void readVersionProperties(ExResultSet rs, Trigger trigger) throws SQLException {
	}

	private static final Pattern STATEMENT_PATTERN = Pattern.compile("AS\\s+(.*)",
			Pattern.CASE_INSENSITIVE + Pattern.MULTILINE + Pattern.DOTALL);

	private String getStatement(ExResultSet rs) throws SQLException {
		String statement = trim(getString(rs, "RDB$TRIGGER_SOURCE"));
		if (statement == null) {
			return null;
		}
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
	static String getActionTimingString(long type) {
		if ((type & 0x4000) != 0) {
			return (type & 1) == 0 ? "BEFORE" : "AFTER";
		}
		if (type <= 0 || type >= 8192)
			return null;
		if (type % 2 == 0) {
			return "AFTER";
		}
		return "BEFORE";
	}

	static Set<String> getEventManipulationString(long type) {
		if (type <= 0)
			throw new IllegalArgumentException("Invalid Firebird trigger type: " + type);
		Set<String> result = new LinkedHashSet<>();
		if ((type & 0x4000) != 0) {
			if ((type & ~1L) == 0x7FFFFFFFFFFFDFFEL) {
				result.add("ANY DDL STATEMENT");
				return result;
			}
			for (int i = 0; i < DDL_EVENTS.length; i++) {
				int bit = i < 12 ? i + 1 : i + 4;
				if ((type & (1L << bit)) != 0) {
					result.add(DDL_EVENTS[i]);
				}
			}
			if (result.isEmpty()) {
				throw new IllegalArgumentException("Unsupported Firebird DDL trigger type: " + type);
			}
			return result;
		}
		switch ((int) type) {
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
		if (type >= 8192) {
			throw new IllegalArgumentException("Unsupported Firebird trigger type: " + type);
		}
		int tmp = (int) (((type + 1) >> 1) & 3);
		String action = getTriggerEvent(tmp);
		result.add(action);
		tmp = (int) (type >> 3);
		if (tmp == 0) {
			return result;
		}
		tmp = tmp & 3; // 11 でマスク
		action = getTriggerEvent(tmp);
		if (!isEmpty(action)) {
			result.add(action);
		}
		tmp = (int) (type >> 5);
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

	private static String getTriggerEvent(int tmp) {
		switch (tmp) {
		case 1:
			return "INSERT";
		case 2:
			return "UPDATE";
		case 3:
			return "DELETE";
		default:
			return "";
		}
	}

	private static final String[] DDL_EVENTS = { "CREATE TABLE", "ALTER TABLE", "DROP TABLE", "CREATE PROCEDURE",
			"ALTER PROCEDURE", "DROP PROCEDURE", "CREATE FUNCTION", "ALTER FUNCTION", "DROP FUNCTION", "CREATE TRIGGER",
			"ALTER TRIGGER", "DROP TRIGGER", "CREATE EXCEPTION", "ALTER EXCEPTION", "DROP EXCEPTION", "CREATE VIEW",
			"ALTER VIEW", "DROP VIEW", "CREATE DOMAIN", "ALTER DOMAIN", "DROP DOMAIN", "CREATE ROLE", "ALTER ROLE",
			"DROP ROLE", "CREATE INDEX", "ALTER INDEX", "DROP INDEX", "CREATE SEQUENCE", "ALTER SEQUENCE",
			"DROP SEQUENCE", "CREATE USER", "ALTER USER", "DROP USER", "CREATE COLLATION", "DROP COLLATION",
			"ALTER CHARACTER SET", "CREATE PACKAGE", "ALTER PACKAGE", "DROP PACKAGE", "CREATE PACKAGE BODY",
			"DROP PACKAGE BODY", "CREATE MAPPING", "ALTER MAPPING", "DROP MAPPING" };

}
