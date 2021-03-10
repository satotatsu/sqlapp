/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.CheckConstraintReader;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.ForeignKeyConstraintReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.db.metadata.UniqueConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableSpace;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DoubleKeyMap;

public class SqlServer2005TableReader extends SqlServer2000TableReader {

	protected SqlServer2005TableReader(final Dialect dialect) {
		super(dialect);
	}

	/**
	 * テーブル情報を取得します
	 * 
	 * @param connection
	 * @param context
	 */
	@Override
	protected List<Table> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final SqlNode node = getSqlSqlNode(productVersionInfo);
		final DoubleKeyMap<String,String,Table> result = new DoubleKeyMap<String,String,Table>();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final Table table = createTable(rs);
				result.put(table.getSchemaName(), table.getName(), table);
			}
		});
		return result.toList();
	}
	
	/**
	 * メタデータの詳細情報を設定するためのメソッドです。子クラスでのオーバーライドを想定しています。
	 * 
	 * @param connection
	 * @param obj
	 */
	@Override
	protected void setMetadataDetail(final Connection connection,
			final ParametersContext context, final List<Table> obj) throws SQLException {
		super.setMetadataDetail(connection, context, obj);
		if (CommonUtils.isEmpty(obj)){
			return;
		}
		final DoubleKeyMap<String,String,Table> tableMap = new DoubleKeyMap<String,String,Table>();
		for(final Table table:obj){
			tableMap.put(table.getSchemaName(), table.getName(), table);
		}
		setPartitioning(connection, tableMap);
	}

	protected void setPartitioning(final Connection connection,final DoubleKeyMap<String,String,Table> tableMap){
		final SqlNode node = getSqlNodeCache().getString("partitionColumns2005.sql");
		final ParametersContext context=new ParametersContext();
		context.put(SCHEMA_NAME, tableMap.keySet());
		context.put(TABLE_NAME, tableMap.secondKeySet());
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final String schemaName=rs.getString(SCHEMA_NAME);
				final String tableName=rs.getString(TABLE_NAME);
				final String columnName=rs.getString(COLUMN_NAME);
				final Table table=tableMap.get(schemaName, tableName);
				if (table!=null) {
					final Column column=table.getColumns().get(columnName);
					final ReferenceColumn rcolumn=new ReferenceColumn(column);
					table.getPartitioning().getPartitioningColumns().add(rcolumn);
				}
			}
		});
	}
	
	@Override
	protected SqlNode getSqlSqlNode(final ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("tables2005.sql");
	}

	@Override
	protected Table createTable(final ExResultSet rs) throws SQLException {
		final Table table = super.createTable(rs);
		table.setLastAlteredAt(rs.getTimestamp("modify_date"));
		final String partitionScheme=this.getString(rs, "partition_scheme");
		if (!CommonUtils.isEmpty(partitionScheme)){
			table.toPartitioning().getPartitioning().setPartitionSchemeName(partitionScheme);
			table.setTableSpace((TableSpace)null);
		}
		setSpecifics(rs, "large_value_types_out_of_row", table);
		setSpecifics(rs, "has_var_decimal", table);
		return table;
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new SqlServer2005ColumnReader(this.getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return new SqlServer2005IndexReader(this.getDialect());
	}

	@Override
	protected UniqueConstraintReader newUniqueConstraintReader() {
		return new SqlServer2005UniqueConstraintReader(this.getDialect());
	}

	@Override
	protected CheckConstraintReader newCheckConstraintReader() {
		return new SqlServer2005CheckConstraintReader(this.getDialect());
	}

	@Override
	protected ForeignKeyConstraintReader newForeignKeyConstraintReader() {
		return new SqlServer2005ForeignKeyConstraintReader(this.getDialect());
	}
}
