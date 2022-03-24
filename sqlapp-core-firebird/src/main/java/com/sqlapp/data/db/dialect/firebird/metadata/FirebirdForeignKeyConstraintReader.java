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

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.trim;
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
 * Firebirdの外部キー制約読み込みクラス
 * 
 * @author satoh
 * 
 */
public class FirebirdForeignKeyConstraintReader extends ForeignKeyConstraintReader {

	public FirebirdForeignKeyConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ForeignKeyConstraint> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<ForeignKeyConstraint> list = list();
		final TripleKeyMap<String, String, String, ForeignKeyConstraint> tCMap = tripleKeyMap();
		final TripleKeyMap<String, String, String, FlexList<ColumnPair>> tColMap = tripleKeyMap();
		final String pk_table_catalog = null;
		final String pk_table_schema = null;
		final String fk_table_catalog = null;
		final String fk_table_schema = null;
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String pk_table_name = trim(getString(rs, "PKTABLE_NAME"));
				String pk_columnName = trim(getString(rs, "PKCOLUMN_NAME"));
				String fk_table_name = trim(getString(rs, "FKTABLE_NAME"));
				String fk_columnName = trim(getString(rs, "FKCOLUMN_NAME"));
				String fk_name = trim(getString(rs, "FK_NAME"));
				String pk_name = trim(getString(rs, "PK_NAME"));
				ForeignKeyConstraint c = tCMap.get(pk_table_catalog, pk_table_schema, fk_name);
				FlexList<ColumnPair> colList = tColMap.get(pk_table_catalog, pk_table_schema, fk_name);
				if (c == null) {
					c = new ForeignKeyConstraint(fk_name);
					c.setSchemaName(pk_table_schema);
					c.setTableName(pk_table_name);
					c.setUpdateRule(CascadeRule.parse(trim(getString(rs, "UPDATE_RULE"))));
					c.setDeleteRule(CascadeRule.parse(trim(getString(rs, "DELETE_RULE"))));
					c.setDeferrability(
							Deferrability.getDeferrability(!"NO".equalsIgnoreCase(trim(getString(rs, "DEFERRABLE"))),
									!"NO".equalsIgnoreCase(trim(getString(rs, "INITIALLY_DEFERRED")))));
					colList = new FlexList<ColumnPair>();
					tCMap.put(pk_table_catalog, pk_table_schema, fk_name, c);
					tColMap.put(pk_table_catalog, pk_table_schema, fk_name, colList);
					list.add(c);
				}
				ColumnPair cPair = new ColumnPair();
				cPair.refCatalogName = fk_table_catalog;
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
