/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.oracle.metadata;

import static com.sqlapp.util.CommonUtils.notZero;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.parameter.ParametersContextBuilder;
import com.sqlapp.data.schemas.CharacterSemantics;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.TripleKeyMap;

/**
 * Oracleのカラム読み込み
 * 
 * @author satoh
 * 
 */
public class OracleColumnReader extends ColumnReader {

	protected OracleColumnReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Column> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		TripleKeyMap<String, String, String, Column> result = CommonUtils.tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Column column = createColumn(rs);
				result.put(column.getSchemaName(), column.getTableName(), column.getName(), column);
			}
		});
		List<Column> list=result.toList();
		if (list.isEmpty()){
			return list;
		}
		ParametersContextBuilder builder=ParametersContextBuilder.create().schemaName(result.keySet().toArray(new String[0]));
		builder.tableName(result.secondKeySet().toArray(new String[0]));
		String[] columnNames=result.thirdKeySet().toArray(new String[0]);
		if (columnNames.length<100){
			builder.columnName(columnNames);
		}
		ParametersContext con=builder.build();
		SqlNode commentNode = getCommentsSqlSqlNode(productVersionInfo);
		execute(connection, commentNode, con, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String schemaName=getString(rs, "OWNER");
				String tableName=getString(rs, TABLE_NAME);
				String columnName=getString(rs, COLUMN_NAME);
				String comment=getString(rs, "COMMENTS");
				Column column=result.get(schemaName, tableName, columnName);
				if (column!=null){
					column.setRemarks(comment);
				}
			}
		});
		return list;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("columns.sql");
	}

	protected SqlNode getCommentsSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("columnComments.sql");
	}
	
	protected Column createColumn(ExResultSet rs) throws SQLException {
		String dataDefault = getString(rs, "DATA_DEFAULT");
		String column_name = getString(rs, COLUMN_NAME);
		String productDataType = getString(rs, "DATA_TYPE");
		long max_length = rs.getLong("CHAR_LENGTH");
		long precision = rs.getLong("DATA_PRECISION");
		Integer scale = getInteger(rs, "DATA_SCALE");
		boolean nullable = "Y".equalsIgnoreCase(getString(rs, "NULLABLE"));
		Column column = new Column(column_name);
		column.setNullable(nullable);
		this.getDialect().setDbType(productDataType, notZero(max_length, precision), scale, column);
		column.setDefaultValue(dataDefault);
		column.setOctetLength(rs.getLong("DATA_LENGTH"));
		column.setSchemaName(getString(rs, "OWNER"));
		column.setTableName(getString(rs, TABLE_NAME));
		//column.setRemarks(getString(rs, "COMMENTS"));
		column.setCharacterSemantics(CharacterSemantics.parse(getString(rs,
				"CHAR_USED")));
		setStatistics(rs, "LAST_ANALYZED", column);
		return column;
	}
}
