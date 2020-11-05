/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.db2.metadata;

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
 * DB2の外部キー制約読み込みクラス
 * 
 * @author satoh
 * 
 */
public class Db2ForeignKeyConstraintReader extends ForeignKeyConstraintReader {

	public Db2ForeignKeyConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ForeignKeyConstraint> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final List<ForeignKeyConstraint> list = list();
		final TripleKeyMap<String, String, String, ForeignKeyConstraint> tCMap = tripleKeyMap();
		final TripleKeyMap<String, String, String, FlexList<ColumnPair>> tColMap = tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String ref_table_catalog = null;
				String ref_table_schema = getString(rs, "ref_" + SCHEMA_NAME);
				String ref_table_name = getString(rs, "ref_" + TABLE_NAME);
				String ref_columnName = getString(rs, "ref_" + COLUMN_NAME);
				String fk_table_catalog = null;
				String fk_table_schema = getString(rs, SCHEMA_NAME);
				String fk_table_name = getString(rs, TABLE_NAME);
				String columnName = getString(rs, COLUMN_NAME);
				String fk_name = getString(rs, CONSTRAINT_NAME);
				String pk_name = getString(rs, "PK_NAME");
				ForeignKeyConstraint c = tCMap.get(fk_table_catalog,
						fk_table_schema, fk_name);
				FlexList<ColumnPair> colList = tColMap.get(fk_table_catalog,
						fk_table_schema, fk_name);
				if (c == null) {
					c = new ForeignKeyConstraint(fk_name);
					c.setSchemaName(fk_table_schema);
					c.setTableName(fk_table_name);
					c.setUpdateRule(CascadeRule
							.parse(getString(rs, UPDATE_RULE)));
					c.setDeleteRule(CascadeRule
							.parse(getString(rs, DELETE_RULE)));
					c.setCreatedAt(rs.getTimestamp("CREATE_TIME"));
					c.setRemarks(getString(rs, REMARKS));
					c.setEnable("Y".equals(getString(rs, "ENFORCED")));
					// String check=getString(rs, "CHECKEXISTINGDATA");
					// c.setDeferrability(Deferrability.parse(rs.getInt("DEFERRABILITY")));
					colList = new FlexList<ColumnPair>();
					tCMap.put(fk_table_catalog, fk_table_schema, fk_name, c);
					tColMap.put(fk_table_catalog, fk_table_schema, fk_name,
							colList);
					list.add(c);
				}
				ColumnPair cPair = new ColumnPair();
				cPair.refCatalogName = ref_table_catalog;
				cPair.refSchemaName = ref_table_schema;
				cPair.refTableName = ref_table_name;
				cPair.refColumnName = ref_columnName;
				cPair.columnName = columnName;
				colList.add(cPair);
			}
		});
		setForeignKeyConstraintColumns(tColMap, list);
		return list;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("foreignKeyConstraints.sql");
	}
}
