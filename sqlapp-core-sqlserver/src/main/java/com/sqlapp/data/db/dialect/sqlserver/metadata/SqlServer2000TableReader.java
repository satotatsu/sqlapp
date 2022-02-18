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

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.CheckConstraintReader;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.ExcludeConstraintReader;
import com.sqlapp.data.db.metadata.ForeignKeyConstraintReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.db.metadata.UniqueConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * SqlServer2000のテーブル読み込み
 * 
 * @author satoh
 * 
 */
public class SqlServer2000TableReader extends TableReader {

	protected SqlServer2000TableReader(Dialect dialect) {
		super(dialect);
	}

	/**
	 * テーブル情報を取得します
	 * 
	 * @param connection
	 * @param context
	 */
	@Override
	protected List<Table> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Table> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Table table = createTable(rs);
				result.add(table);
			}
		});
		for (Table table : result) {
			setTableComment(connection, table);
		}
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("tables2000.sql");
	}

	protected Table createTable(ExResultSet rs) throws SQLException {
		Table table = super.createTable(getString(rs, TABLE_NAME));
		table.setCatalogName(getString(rs, CATALOG_NAME));
		table.setSchemaName(getString(rs, SCHEMA_NAME));
		table.setCreatedAt(rs.getTimestamp("create_date"));
		table.setId("" + rs.getInt("table_id"));
		table.setTableSpaceName(getString(rs, "file_group_name"));
		table.setLobTableSpaceName(getString(rs, "lob_file_group_name"));
		setSpecifics(rs, "text_in_row_limit", table);
		//setSpecifics(rs, "has_clustered_index", table);
		return table;
	}

	protected void setTableComment(Connection connection, final Table table) {
		SqlNode node = getSqlNodeCache().getString("tableComments2000.sql");
		ParametersContext context = defaultParametersContext(connection);
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String remarks = getString(rs, "value");
				table.setRemarks(remarks);
			}
		});
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new SqlServer2000ColumnReader(this.getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return new SqlServer2000IndexReader(this.getDialect());
	}

	@Override
	protected UniqueConstraintReader newUniqueConstraintReader() {
		return new SqlServer2000UniqueConstraintReader(this.getDialect());
	}

	@Override
	protected CheckConstraintReader newCheckConstraintReader() {
		return new SqlServer2000CheckConstraintReader(this.getDialect());
	}

	@Override
	protected ForeignKeyConstraintReader newForeignKeyConstraintReader() {
		return new SqlServer2000ForeignKeyConstraintReader(this.getDialect());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.dialect.metadata.TableReader#newExcludeConstraintReader
	 * ()
	 */
	@Override
	protected ExcludeConstraintReader newExcludeConstraintReader() {
		return null;
	}

}
