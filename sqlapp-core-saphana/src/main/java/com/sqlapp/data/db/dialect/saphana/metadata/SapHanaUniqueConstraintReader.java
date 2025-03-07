/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-saphana.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.saphana.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.tripleKeyMap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.UniqueConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.TripleKeyMap;

/**
 * SAP HANA Unique Constraint Reader
 * 
 * @author satoh
 * 
 */
public class SapHanaUniqueConstraintReader extends UniqueConstraintReader {

	public SapHanaUniqueConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<UniqueConstraint> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final TripleKeyMap<String, String, String, UniqueConstraint> map = tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String name = getString(rs, INDEX_NAME);
				String schemaName = getString(rs, SCHEMA_NAME);
				String tableName = getString(rs, TABLE_NAME);
				// String expression=getString(rs, "SEARCH_CONDITION");
				UniqueConstraint c = map.get(schemaName, tableName, name);
				if (c == null) {
					c = createUniqueConstraint(rs);
					map.put(schemaName, tableName, name, c);
				}
				final Order order;
				String asc = getString(rs, "ASCENDING_ORDER");
				if ("TRUE".equalsIgnoreCase(asc)) {
					order = Order.Asc;
				} else {
					order = Order.Desc;
				}
				c.getColumns().add(new Column(getString(rs, COLUMN_NAME)),
						order);
			}
		});
		List<UniqueConstraint> list = map.toList();
		return list;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("uniqueConstraints.sql");
	}
	
	protected UniqueConstraint createUniqueConstraint(ExResultSet rs) throws SQLException {
		UniqueConstraint c = new UniqueConstraint();
		String name = getString(rs, INDEX_NAME);
		String schemaName = getString(rs, SCHEMA_NAME);
		String tableName = getString(rs, TABLE_NAME);
		String cons=getString(rs, "CONSTRAINT");
		cons=cons.replace("NOT NULL", "").trim();
		boolean primary = "PRIMARY KEY".equalsIgnoreCase(cons);
		c = new UniqueConstraint(name, primary);
		c.setSchemaName(schemaName);
		c.setTableName(tableName);
		return c;
	}
}
