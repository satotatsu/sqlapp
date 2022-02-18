/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.sqlserver.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.tripleKeyMap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnPair;
import com.sqlapp.data.db.metadata.ForeignKeyConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.FlexList;
import com.sqlapp.util.TripleKeyMap;

/**
 * SqlServer2005の外部キー制約作成クラス
 * 
 * @author satoh
 * 
 */
public class SqlServer2005ForeignKeyConstraintReader extends
		ForeignKeyConstraintReader {

	public SqlServer2005ForeignKeyConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ForeignKeyConstraint> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<ForeignKeyConstraint> list = list();
		final TripleKeyMap<String, String, String, ForeignKeyConstraint> tCMap = tripleKeyMap();
		final TripleKeyMap<String, String, String, FlexList<ColumnPair>> tColMap = tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String pk_table_catalog = getString(rs, CATALOG_NAME);
				String pk_table_schema = getString(rs, SCHEMA_NAME);
				String pk_table_name = getString(rs, TABLE_NAME);
				String pk_columnName = getString(rs, COLUMN_NAME);
				String fk_table_schema = getString(rs,
						"referential_schema_name");
				String fk_table_name = getString(rs, "referential_table_name");
				String fk_columnName = getString(rs, "referential_column_name");
				String fk_name = getString(rs, CONSTRAINT_NAME);
				ForeignKeyConstraint c = tCMap.get(pk_table_catalog,
						pk_table_schema, fk_name);
				FlexList<ColumnPair> colList = tColMap.get(pk_table_catalog,
						pk_table_schema, fk_name);
				if (c == null) {
					c = new ForeignKeyConstraint(fk_name);
					c.setCatalogName(pk_table_catalog);
					c.setSchemaName(pk_table_schema);
					c.setTableName(pk_table_name);
					c.setUpdateRule(CascadeRule.parse(getString(rs,
							"update_referential_action_desc")));
					c.setDeleteRule(CascadeRule.parse(getString(rs,
							"delete_referential_action_desc")));
					c.setEnable(rs.getInt("is_disabled") != 1);
					c.setCreatedAt(rs.getTimestamp("create_date"));
					c.setLastAlteredAt(rs.getTimestamp("modify_date"));
					colList = new FlexList<ColumnPair>();
					tCMap.put(pk_table_catalog, pk_table_schema, fk_name, c);
					tColMap.put(pk_table_catalog, pk_table_schema, fk_name,
							colList);
					list.add(c);
				}
				ColumnPair cPair = new ColumnPair();
				cPair.refCatalogName = pk_table_catalog;
				cPair.refSchemaName = fk_table_schema;
				cPair.refTableName = fk_table_name;
				cPair.refColumnName = fk_columnName;
				cPair.columnName = pk_columnName;
				colList.add(cPair);
			}
		});
		setForeignKeyConstraintColumns(tColMap, list);
		return list;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("foreignKeyConstraints2005.sql");
	}
}
