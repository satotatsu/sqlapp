/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-postgres.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.postgres.metadata;

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
import com.sqlapp.data.schemas.Deferrability;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.FlexList;
import com.sqlapp.util.TripleKeyMap;

/**
 * Postgresの外部キー制約作成クラス
 * 
 * @author satoh
 * 
 */
public class PostgresForeignKeyConstraintReader extends
		ForeignKeyConstraintReader {

	public PostgresForeignKeyConstraintReader(Dialect dialect) {
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
				String pk_table_catalog = null;
				String pk_table_schema = getString(rs, "constraint_schema");
				String pk_table_name = getString(rs, TABLE_NAME);
				String pk_columnName = getString(rs, COLUMN_NAME);
				String fk_table_schema = getString(rs,
						"referential_table_schema");
				String fk_table_name = getString(rs, "referential_table_name");
				String fk_columnName = getString(rs, "referential_column_name");
				String fk_name = getString(rs, CONSTRAINT_NAME);
				ForeignKeyConstraint c = tCMap.get(pk_table_catalog,
						pk_table_schema, fk_name);
				FlexList<ColumnPair> colList = tColMap.get(pk_table_catalog,
						pk_table_schema, fk_name);
				if (c == null) {
					c = new ForeignKeyConstraint(fk_name);
					c.setSchemaName(pk_table_schema);
					c.setTableName(pk_table_name);
					c.setUpdateRule(CascadeRule.parse(getString(rs,
							"update_rule")));
					c.setDeleteRule(CascadeRule.parse(getString(rs,
							"delete_rule")));
					c.setDeferrability(Deferrability.getDeferrability(
							rs.getBoolean("is_deferrable"),
							rs.getBoolean("initially_deferred")));
					c.setMatchOption(getString(rs, "match_option"));
					colList = new FlexList<ColumnPair>();
					tCMap.put(pk_table_catalog, pk_table_schema, fk_name, c);
					tColMap.put(pk_table_catalog, pk_table_schema, fk_name,
							colList);
					list.add(c);
				}
				ColumnPair cPair = new ColumnPair();
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
		return getSqlNodeCache().getString("foreignKeyConstraints.sql");
	}
}
