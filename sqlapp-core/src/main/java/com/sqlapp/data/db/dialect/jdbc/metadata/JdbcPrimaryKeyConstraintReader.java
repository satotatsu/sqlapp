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
import com.sqlapp.data.db.metadata.UniqueConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FlexList;
import com.sqlapp.util.TripleKeyMap;

/**
 * JDBCユニーク制約読み込みクラス
 * 
 * @author satoh
 * 
 */
public class JdbcPrimaryKeyConstraintReader extends UniqueConstraintReader {

	public JdbcPrimaryKeyConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<UniqueConstraint> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		ResultSet rs = null;
		List<UniqueConstraint> list = list();
		try {
			DatabaseMetaData databaseMetaData = connection.getMetaData();
			rs = databaseMetaData.getPrimaryKeys(CommonUtils.coalesce(emptyToNull(this.getCatalogName(context)), emptyToNull(this.getCatalogName()))
					,CommonUtils.coalesce(emptyToNull(this.getSchemaName(context)), emptyToNull(this.getSchemaName()))
					,CommonUtils.coalesce(emptyToNull(this.getObjectName(context)), emptyToNull(this.getObjectName())));
			TripleKeyMap<String, String, String, UniqueConstraint> tUkMap = tripleKeyMap();
			TripleKeyMap<String, String, String, FlexList<String>> tColMap = tripleKeyMap();
			while (rs.next()) {
				String table_catalog = getString(rs, "TABLE_CAT");
				String table_schema = getString(rs, "TABLE_SCHEM");
				String table_name = getString(rs, TABLE_NAME);
				String columnName = getString(rs, COLUMN_NAME);
				String pk_name = getString(rs, "PK_NAME");
				int keySeq = rs.getInt("KEY_SEQ");
				UniqueConstraint uk = tUkMap.get(table_catalog, table_schema,
						table_name);
				FlexList<String> colList = tColMap.get(table_catalog,
						table_schema, table_name);
				if (uk == null) {
					uk = new UniqueConstraint(pk_name);
					uk.setCatalogName(table_catalog);
					uk.setSchemaName(table_schema);
					uk.setTableName(table_schema);
					colList = new FlexList<String>();
					tUkMap.put(table_catalog, table_schema, table_name, uk);
					tColMap.put(table_catalog, table_schema, table_name,
							colList);
					list.add(uk);
				}
				colList.add(keySeq, columnName);
			}
			for (UniqueConstraint uk : list) {
				FlexList<String> colList = tColMap.get(uk.getCatalogName(),
						uk.getSchemaName(), uk.getTableName());
				uk.getColumns().add(colList.toArray(new String[0]));
			}
			return list;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			close(rs);
		}
	}
}
