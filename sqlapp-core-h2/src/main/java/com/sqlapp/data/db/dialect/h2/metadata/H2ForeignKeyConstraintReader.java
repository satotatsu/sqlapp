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
 * H2の外部キー制約読み込みクラス
 * 
 * @author satoh
 * 
 */
public class H2ForeignKeyConstraintReader extends ForeignKeyConstraintReader {

	public H2ForeignKeyConstraintReader(Dialect dialect) {
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
				String pk_table_catalog = getString(rs, "PKTABLE_CATALOG");
				String pk_table_schema = getString(rs, "PKTABLE_SCHEMA");
				String pk_table_name = getString(rs, "PKTABLE_NAME");
				String pk_columnName = getString(rs, "PKCOLUMN_NAME");
				String fk_table_catalog = getString(rs, "FKTABLE_CATALOG");
				String fk_table_schema = getString(rs, "FKTABLE_SCHEMA");
				String fk_table_name = getString(rs, "FKTABLE_NAME");
				String fk_columnName = getString(rs, "FKCOLUMN_NAME");
				String fk_name = getString(rs, "FK_NAME");
				String pk_name = getString(rs, "PK_NAME");
				ForeignKeyConstraint c = tCMap.get(pk_table_catalog,
						pk_table_schema, fk_name);
				FlexList<ColumnPair> colList = tColMap.get(pk_table_catalog,
						pk_table_schema, fk_name);
				if (c == null) {
					c = new ForeignKeyConstraint(fk_name);
					c.setCatalogName(fk_table_catalog);
					c.setSchemaName(fk_table_schema);
					c.setTableName(fk_table_name);
					c.setUpdateRule(CascadeRule.parse(rs.getInt(UPDATE_RULE)));
					c.setDeleteRule(CascadeRule.parse(rs.getInt(DELETE_RULE)));
					c.setDeferrability(Deferrability.parse(rs
							.getInt("DEFERRABILITY")));
					colList = new FlexList<ColumnPair>();
					tCMap.put(fk_table_catalog, fk_table_schema, fk_name, c);
					tColMap.put(fk_table_catalog, fk_table_schema, fk_name,
							colList);
					list.add(c);
				}
				ColumnPair cPair = new ColumnPair();
				cPair.refCatalogName = pk_table_catalog;
				cPair.refSchemaName = pk_table_schema;
				cPair.refTableName = pk_table_name;
				cPair.refColumnName = pk_columnName;
				cPair.columnName = fk_columnName;
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
