/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.ExcludeConstraintReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.db.metadata.MviewReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Mview;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Oracleのマテビュー作成クラス
 * 
 * @author satoh
 * 
 */
public class OracleMviewReader extends MviewReader {

	protected OracleMviewReader(Dialect dialect) {
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
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Table> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Mview table = createTable(rs);
				result.add(table);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("mviews.sql");
	}

	protected Mview createTable(ExResultSet rs) throws SQLException {
		Mview table = createTable(getString(rs, "MVIEW_NAME"));
		table.setSchemaName(getString(rs, "OWNER"));
		table.setRemarks("COMMENTS");
		table.setStatement(getString(rs, "QUERY"));
		setSpecifics(rs, "REFRESH_MODE", table);
		setSpecifics(rs, "REFRESH_METHOD", table);
		setSpecifics(rs, "BUILD_MODE", table);
		setSpecifics(rs, "FAST_REFRESHABLE", table);
		return table;
	}

	@Override
	protected void setMetadataDetail(final Connection connection,
			final Table table) throws SQLException {

	}

	@Override
	protected ColumnReader newColumnReader() {
		return new OracleColumnReader(this.getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return new OracleIndexReader(this.getDialect());
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
