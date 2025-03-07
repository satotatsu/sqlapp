/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle.metadata;

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
 * Oracleの外部キー制約作成クラス
 * 
 * @author satoh
 * 
 */
public class OracleForeignKeyConstraintReader extends
		ForeignKeyConstraintReader {

	public OracleForeignKeyConstraintReader(Dialect dialect) {
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
				String fk_schema = getString(rs, "OWNER");
				String fk_name = getString(rs, "CONSTRAINT_NAME");
				String table_name = getString(rs, TABLE_NAME);
				String columnName = getString(rs, COLUMN_NAME);
				String reference_table_schema = getString(rs, "REFERENCE_OWNER");
				String reference_table_name = getString(rs,
						"REFERENCE_TABLE_NAME");
				String reference_columnName = getString(rs,
						"REFERENCE_COLUMN_NAME");
				ForeignKeyConstraint c = tCMap.get(pk_table_catalog, fk_schema,
						fk_name);
				FlexList<ColumnPair> colList = tColMap.get(pk_table_catalog,
						fk_schema, fk_name);
				if (c == null) {
					c = new ForeignKeyConstraint(fk_name);
					c.setSchemaName(fk_schema);
					c.setTableName(table_name);
					c.setUpdateRule(CascadeRule.None);
					c.setDeleteRule(rs.getString(DELETE_RULE));
					String deferrable = getString(rs, "DEFERRABLE");
					String deferred = getString(rs, "DEFERRED");
					c.setDeferrability(OracleMetadataUtils.getDeferrability(
							deferrable, deferred));
					colList = new FlexList<ColumnPair>();
					tCMap.put(pk_table_catalog, fk_schema, fk_name, c);
					tColMap.put(pk_table_catalog, fk_schema, fk_name, colList);
					list.add(c);
				}
				ColumnPair cPair = new ColumnPair();
				cPair.refSchemaName = reference_table_schema;
				cPair.refTableName = reference_table_name;
				cPair.refColumnName = reference_columnName;
				cPair.columnName = columnName;
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
