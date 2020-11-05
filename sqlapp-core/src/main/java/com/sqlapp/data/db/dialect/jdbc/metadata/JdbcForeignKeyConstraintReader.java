/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.jdbc.metadata;

import static com.sqlapp.util.CommonUtils.emptyToNull;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.tripleKeyMap;
import static com.sqlapp.util.DbUtils.close;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
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
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FlexList;
import com.sqlapp.util.TripleKeyMap;

/**
 * JDBC外部キー制約読み込み作成クラス
 * 
 * @author satoh
 * 
 */
public class JdbcForeignKeyConstraintReader extends ForeignKeyConstraintReader {

	public JdbcForeignKeyConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ForeignKeyConstraint> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		ResultSet rs = null;
		List<ForeignKeyConstraint> list = list();
		try {
			DatabaseMetaData databaseMetaData = connection.getMetaData();
			rs = databaseMetaData.getImportedKeys(
					CommonUtils.coalesce(emptyToNull(this.getCatalogName(context)), emptyToNull(this.getCatalogName()))
					,CommonUtils.coalesce(emptyToNull(this.getSchemaName(context)), emptyToNull(this.getSchemaName()))
					, emptyToNull(this.getTableName(context)));
			TripleKeyMap<String, String, String, ForeignKeyConstraint> tCMap = tripleKeyMap();
			TripleKeyMap<String, String, String, FlexList<ColumnPair>> tColMap = tripleKeyMap();
			while (rs.next()) {
				String pk_table_catalog = getString(rs, "PKTABLE_CAT");
				String pk_table_schema = getString(rs, "PKTABLE_SCHEM");
				String pk_table_name = getString(rs, "PKTABLE_NAME");
				String pk_columnName = getString(rs, "PKCOLUMN_NAME");
				String fk_table_catalog = getString(rs, "FKTABLE_CAT");
				String fk_table_schema = getString(rs, "FKTABLE_SCHEM");
				String fk_table_name = getString(rs, "FKTABLE_NAME");
				String fk_columnName = getString(rs, "FKCOLUMN_NAME");
				String fk_name = getString(rs, "FK_NAME");
				ForeignKeyConstraint c = tCMap.get(pk_table_catalog,
						pk_table_schema, fk_name);
				FlexList<ColumnPair> colList = tColMap.get(pk_table_catalog,
						pk_table_schema, fk_name);
				if (c == null) {
					c = new ForeignKeyConstraint(fk_name);
					c.setCatalogName(pk_table_catalog);
					c.setSchemaName(pk_table_schema);
					c.setTableName(pk_table_name);
					c.setUpdateRule(CascadeRule.parse(rs.getInt("UPDATE_RULE")));
					c.setDeleteRule(CascadeRule.parse(rs.getInt("DELETE_RULE")));
					c.setDeferrability(Deferrability.parse(rs
							.getInt("DEFERRABILITY")));
					colList = new FlexList<ColumnPair>();
					tCMap.put(pk_table_catalog, pk_table_schema, fk_name, c);
					tColMap.put(pk_table_catalog, pk_table_schema, fk_name,
							colList);
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
			setForeignKeyConstraintColumns(tColMap, list);
			return list;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			close(rs);
		}
	}
}
