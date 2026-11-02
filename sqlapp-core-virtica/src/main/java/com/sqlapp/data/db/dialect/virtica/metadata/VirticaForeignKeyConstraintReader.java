/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-virtica.
 *
 * sqlapp-core-virtica is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-virtica is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-virtica.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.virtica.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.quadKeyMap;

import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnPair;
import com.sqlapp.data.db.metadata.ForeignKeyConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.util.FlexList;
import com.sqlapp.util.QuadKeyMap;

/**
 * Virtica Forign Key Constraint Reader
 * 
 * @author satoh
 * 
 */
public class VirticaForeignKeyConstraintReader extends ForeignKeyConstraintReader {

	public VirticaForeignKeyConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ForeignKeyConstraint> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final List<ForeignKeyConstraint> list = list();
		final QuadKeyMap<String, String, String, String, ForeignKeyConstraint> tCMap = quadKeyMap();
		final QuadKeyMap<String, String, String, String, FlexList<ColumnPair>> tColMap = quadKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String pk_table_catalog = null;
				String pk_table_schema = getString(rs, "REFERENCE_TABLE_SCHEMA");
				String pk_table_name = getString(rs, "REFERENCE_TABLE_NAME");
				String pk_columnName = getString(rs, "REFERENCE_COLUMN_NAME");
				String fk_table_catalog = null;
				String fk_table_schema = getString(rs, "TABLE_SCHEMA");
				String fk_table_name = getString(rs, TABLE_NAME);
				String fk_columnName = getString(rs, COLUMN_NAME);
				String fk_name = getString(rs, CONSTRAINT_NAME);
				// String pk_name = getString(rs, "PK_NAME");
				ForeignKeyConstraint c = tCMap.get(fk_table_catalog, fk_table_schema, fk_table_name, fk_name);
				FlexList<ColumnPair> colList = tColMap.get(fk_table_catalog, fk_table_schema, fk_table_name, fk_name);
				if (c == null) {
					c = new ForeignKeyConstraint(fk_name);
					c.setCatalogName(fk_table_catalog);
					c.setSchemaName(fk_table_schema);
					c.setTableName(fk_table_name);
					c.setRemarks(getString(rs, "REMARKS"));
					colList = new FlexList<ColumnPair>();
					tCMap.put(fk_table_catalog, fk_table_schema, fk_table_name, fk_name, c);
					tColMap.put(fk_table_catalog, fk_table_schema, fk_table_name, fk_name, colList);
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
		for (ForeignKeyConstraint constraint : list) {
			// Reuse shared column/owner assembly, scoped to this table's constraint.
			var columns = com.sqlapp.util.CommonUtils.<String, String, String, FlexList<ColumnPair>>tripleKeyMap();
			columns.put(constraint.getCatalogName(), constraint.getSchemaName(), constraint.getName(),
					tColMap.get(constraint.getCatalogName(), constraint.getSchemaName(), constraint.getTableName(),
							constraint.getName()));
			setForeignKeyConstraintColumns(columns, java.util.List.of(constraint));
		}
		return list;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("foreignKeyConstraints.sql");
	}
}
