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
	protected List<Column> doGetAll(Connection connection, ParametersContext context,
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
		List<Column> list = result.toList();
		if (list.isEmpty()) {
			return list;
		}
		if (list.stream().anyMatch(column -> column.getSpecifics().containsKey("oracle.udt.owner"))) {
			java.util.List<OracleIntervalCollections.Description> collections;
			try { collections = OracleIntervalCollections.refresh(connection); }
			catch (SQLException failure) { throw new IllegalStateException("Cannot read Oracle interval collection metadata", failure); }
			for (var column : list) {
				String owner = column.getSpecifics().get("oracle.udt.owner");
				for (var collection : collections)
					if (collection.owner().equals(owner) && collection.name().equals(column.getDataTypeName())) collection.apply(column);
			}
		}
		ParametersContextBuilder builder = ParametersContextBuilder.create()
				.schemaName(result.keySet().toArray(new String[0]));
		builder.tableName(result.secondKeySet().toArray(new String[0]));
		String[] columnNames = result.thirdKeySet().toArray(new String[0]);
		if (columnNames.length < 100) {
			builder.columnName(columnNames);
		}
		ParametersContext con = builder.build();
		SqlNode commentNode = getCommentsSqlSqlNode(productVersionInfo);
		execute(connection, commentNode, con, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String schemaName = getString(rs, "OWNER");
				String tableName = getString(rs, TABLE_NAME);
				String columnName = getString(rs, COLUMN_NAME);
				String comment = getString(rs, "COMMENTS");
				Column column = result.get(schemaName, tableName, columnName);
				if (column != null) {
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
		Long max_length = getLong(rs, "CHAR_LENGTH");
		Long precision = getLong(rs, "DATA_PRECISION");
		Integer scale = getInteger(rs, "DATA_SCALE");
		boolean nullable = "Y".equalsIgnoreCase(getString(rs, "NULLABLE"));
		Column column = new Column(column_name);
		column.setNullable(nullable);
		this.getDialect().setDbType(productDataType, max_length != null && max_length > 0 ? max_length : precision,
				scale, column);
		if ("NUMBER".equalsIgnoreCase(productDataType))
			column.setLength(precision).setScale(scale);
		if (column.getDataType() == com.sqlapp.data.db.datatype.DataType.OTHER) {
			String owner = getString(rs, "DATA_TYPE_OWNER");
			if (owner != null) column.getSpecifics().put("oracle.udt.owner", owner);
		}
		String collectionKind = getString(rs, "SQLAPP_INTERVAL_COLLECTION_KIND");
		if (collectionKind != null) column.getSpecifics().put(OracleIntervalCollections.KIND, collectionKind);
		column.setDefaultValue(dataDefault);
		column.setOctetLength(rs.getLong("DATA_LENGTH"));
		column.setSchemaName(getString(rs, "OWNER"));
		column.setTableName(getString(rs, TABLE_NAME));
		// column.setRemarks(getString(rs, "COMMENTS"));
		column.setCharacterSemantics(CharacterSemantics.parse(getString(rs, "CHAR_USED")));
		setStatistics(rs, "LAST_ANALYZED", column);
		return column;
	}
}
