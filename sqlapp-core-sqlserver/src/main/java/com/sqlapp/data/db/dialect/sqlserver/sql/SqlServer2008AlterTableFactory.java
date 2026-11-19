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

package com.sqlapp.data.db.dialect.sqlserver.sql;

import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Difference;
import com.sqlapp.data.schemas.Table;

/**
 * SQLServer2008 alter table
 * 
 * @author tatsuo satoh
 * 
 */
public class SqlServer2008AlterTableFactory extends SqlServer2005AlterTableFactory {

	@Override
	protected void addOtherDefinitions(Map<String, Difference<?>> allDiff, Table originalTable, Table table,
			List<SqlOperation> result) {
		super.addOtherDefinitions(allDiff, originalTable, table, result);
		boolean before = tracking(originalTable, "HAS_CHANGE_TRACKING");
		boolean after = tracking(table, "HAS_CHANGE_TRACKING");
		boolean oldColumns = tracking(originalTable, "is_track_columns_updated_on");
		boolean columns = tracking(table, "is_track_columns_updated_on");
		if (!after && columns && (!before || !oldColumns))
			throw new IllegalArgumentException("TRACK_COLUMNS_UPDATED requires change tracking");
		if (before == after && (!after || oldColumns == columns))
			return;
		SqlServerSqlBuilder builder = createSqlBuilder();
		builder.alter().table().name(table, getOptions().isDecorateSchemaName());
		if (after)
			builder.enable().changeTracking()._add(" WITH (TRACK_COLUMNS_UPDATED = ")
					._add(columns ? "ON" : "OFF")._add(")");
		else
			builder.disable().changeTracking();
		add(result, createOperation(builder.toString(), com.sqlapp.data.db.sql.SqlType.ALTER, originalTable, table));
	}

	private boolean tracking(Table table, String key) {
		String value = table.getSpecifics().get(key);
		if (value == null || "false".equalsIgnoreCase(value) || "0".equals(value))
			return false;
		if ("true".equalsIgnoreCase(value) || "1".equals(value))
			return true;
		throw new IllegalArgumentException("Invalid " + key + ": " + value);
	}
}
