/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-saphana.
 *
 * sqlapp-core-saphana is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-saphana is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with sqlapp-core-saphana.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.saphana.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.tripleKeyMap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnPair;
import com.sqlapp.data.db.metadata.ForeignKeyConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.FlexList;
import com.sqlapp.util.TripleKeyMap;

/**
 * SAP HANAの外部キー制約作成クラス
 * 
 * @author satoh
 * 
 */
public class SapHanaForeignKeyConstraintReader extends ForeignKeyConstraintReader {

	public SapHanaForeignKeyConstraintReader(Dialect dialect) {
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
				String fk_catalog = null;
				String fk_schema = getString(rs, SCHEMA_NAME);
				String fk_name = getString(rs, CONSTRAINT_NAME);
				String columnName = getString(rs, COLUMN_NAME);
				String referenced_table_catalog = null;
				String referenced_table_schema = getString(rs,
						"REFERENCED_SCHEMA_NAME");
				String referenced_table_name = getString(rs,
						"REFERENCED_TABLE_NAME");
				String referenced_column_name = getString(rs,
						"REFERENCED_COLUMN_NAME");
				ForeignKeyConstraint c = tCMap.get(fk_catalog, fk_schema,
						fk_name);
				FlexList<ColumnPair> colList = tColMap.get(fk_catalog,
						fk_schema, fk_name);
				if (c == null) {
					c = createConstraint(rs);
					// unique_constraint_name
					// c.setDeferrability(Deferrability.parse(rs.getInt("DEFERRABILITY")));
					colList = new FlexList<ColumnPair>();
					tCMap.put(fk_catalog, fk_schema, fk_name, c);
					tColMap.put(fk_catalog, fk_schema, fk_name, colList);
					list.add(c);
				}
				ColumnPair cPair = new ColumnPair();
				cPair.refCatalogName = referenced_table_catalog;
				cPair.refSchemaName = referenced_table_schema;
				cPair.refTableName = referenced_table_name;
				cPair.refColumnName = referenced_column_name;
				cPair.columnName = columnName;
				colList.add(cPair);
			}
		});
		setForeignKeyConstraintColumns(tColMap, list);
		return list;
	}

	protected SqlNode getSqlSqlNode(final ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("foreignKeyConstraints.sql");
	}

	protected ForeignKeyConstraint createConstraint(ExResultSet rs)
			throws SQLException {
		ForeignKeyConstraint c = new ForeignKeyConstraint();
		c = new ForeignKeyConstraint(getString(rs, CONSTRAINT_NAME));
		c.setSchemaName(getString(rs, SCHEMA_NAME));
		c.setTableName(getString(rs, TABLE_NAME));
		c.setMatchOption(getString(rs, MATCH_OPTION));
		c.setUpdateRule(getString(rs, UPDATE_RULE));
		c.setDeleteRule(getString(rs, DELETE_RULE));
		return c;
	}
}
