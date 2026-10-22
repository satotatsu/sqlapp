/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.hsql.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.information_schema.metadata.ISTriggerReader;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.sql.node.SqlNode;
import java.sql.SQLException;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.data.schemas.Trigger;
import java.sql.Connection;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.util.JsonConverter;

/**
 * HSQLのトリガー読み込みクラス
 * 
 * @author satoh
 *
 */
public class HsqlTriggerReader extends ISTriggerReader {

	protected HsqlTriggerReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Trigger> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		if (version.getMajorVersion() < 2)
			return super.doGetAll(connection, context, version);
		var triggers = new LinkedHashMap<List<String>, Trigger>();
		var columns = new LinkedHashMap<List<String>, LinkedHashSet<String>>();
		execute(connection, getSqlNode(version), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				var trigger = createTrigger(rs);
				var key = List.of(trigger.getCatalogName(), trigger.getSchemaName(), trigger.getName());
				trigger.setDialect(getDialect());
				triggers.putIfAbsent(key, trigger);
				String column = getString(rs, "EVENT_OBJECT_COLUMN");
				if (column != null)
					columns.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(column);
			}
		});
		columns.forEach((key, names) -> triggers.get(key).getSpecifics().put("hsql.updateColumns.v1",
				new JsonConverter().toJsonString(names)));
		return new ArrayList<>(triggers.values());
	}

	@Override
	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		if (productVersionInfo.getMajorVersion() < 2)
			return super.getSqlNode(productVersionInfo);
		return getSqlNodeCache().getString("triggers.sql");
	}

	@Override
	protected Trigger createTrigger(ExResultSet rs) throws SQLException {
		Trigger trigger = super.createTrigger(rs);
		for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
			String name = rs.getMetaData().getColumnLabel(i);
			if ("ACTION_REFERENCE_OLD_TABLE".equalsIgnoreCase(name)
					|| "ACTION_REFERENCE_NEW_TABLE".equalsIgnoreCase(name)) {
				setSpecifics(rs, name, name.toUpperCase(java.util.Locale.ROOT), trigger);
			}
		}
		return trigger;
	}

}
