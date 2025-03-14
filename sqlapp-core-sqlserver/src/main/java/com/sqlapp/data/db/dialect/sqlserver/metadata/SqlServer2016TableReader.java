/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver.metadata;

import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;

public class SqlServer2016TableReader extends SqlServer2014TableReader {

	protected SqlServer2016TableReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlNode getSqlSqlNode(final ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("tables2016.sql");
	}

	@Override
	protected Table createTable(final ExResultSet rs) throws SQLException {
		final Table table = super.createTable(rs);
		setSpecifics(rs, "temporal_type", table);
		return table;
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new SqlServer2016ColumnReader(this.getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return new SqlServer2016IndexReader(this.getDialect());
	}
}
