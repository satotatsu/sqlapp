/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-h2.
 *
 * sqlapp-core-h2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-h2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-h2.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.h2.metadata;

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

public class H2TableReader extends TableReader {

	protected H2TableReader(Dialect dialect) {
		super(dialect);
	}

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
		return result;
	}

	protected Table createTable(ExResultSet rs) throws SQLException {
		Table obj = createTable(getString(rs, TABLE_NAME));
		obj.setCatalogName(getString(rs, TABLE_CATALOG));
		obj.setSchemaName(getString(rs, TABLE_SCHEMA));
		obj.setRemarks(getString(rs, REMARKS));
		obj.setTableType(getString(rs, "STORAGE_TYPE"));
		this.setStatistics(rs, "ROW_COUNT_ESTIMATE", obj);
		return obj;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("tables.sql");
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new H2ColumnReader(this.getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return new H2IndexReader(this.getDialect());
	}

	@Override
	protected UniqueConstraintReader newUniqueConstraintReader() {
		return new H2UniqueConstraintReader(this.getDialect());
	}

	@Override
	protected CheckConstraintReader newCheckConstraintReader() {
		return new H2CheckConstraintReader(this.getDialect());
	}

	@Override
	protected ForeignKeyConstraintReader newForeignKeyConstraintReader() {
		return new H2ForeignKeyConstraintReader(this.getDialect());
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
