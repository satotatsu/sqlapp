/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres.metadata;

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
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Postgresのマテビュー読み込みクラス
 * 
 * @author satoh
 * 
 */
public class Postgres93MviewReader extends MviewReader {

	protected Postgres93MviewReader(Dialect dialect) {
		super(dialect);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.metadata.MetadataReader#doGetAll(java.sql.Connection,
	 * com.sqlapp.data.parameter.ParametersContext,
	 * com.sqlapp.data.schemas.ProductVersionInfo)
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
				Table table = createTable(rs);
				result.add(table);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("mviews.sql");
	}

	protected Table createTable(ExResultSet rs) throws SQLException {
		Table table = createTable(getString(rs, "matviewname"));
		table.setSchemaName(getString(rs, "schemaname"));
		table.setTableSpaceName(getString(rs, "tablespace"));
		table.setRemarks("remarks");
		table.setStatement(getString(rs, "definition"));
		return table;
	}

	@Override
	protected void setMetadataDetail(final Connection connection,
			final Table table) throws SQLException {

	}

	@Override
	protected ColumnReader newColumnReader() {
		return new PostgresColumnReader(this.getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return new PostgresIndexReader(this.getDialect());
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
		return new Postgres90ExcludeConstraintReader(this.getDialect());
	}

}
