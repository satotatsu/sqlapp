/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.firebird.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.trim;

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
 * Firebirdのテーブル読み込み
 * 
 * @author satoh
 * 
 */
public class FirebirdTableReader extends TableReader {

	protected FirebirdTableReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Table> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Table> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Table obj = createTable(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(final ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("tables.sql");
	}

	protected Table createTable(ExResultSet rs) throws SQLException {
		Table obj = createTable(trim(getString(rs, "RDB$RELATION_NAME")));
		obj.setRemarks(trim(getString(rs, "RDB$DESCRIPTION")));
		return obj;
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new FirebirdColumnReader(this.getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return new FirebirdIndexReader(this.getDialect());
	}

	@Override
	protected UniqueConstraintReader newUniqueConstraintReader() {
		return new FirebirdUniqueConstraintReader(this.getDialect());
	}

	@Override
	protected CheckConstraintReader newCheckConstraintReader() {
		return new FirebirdCheckConstraintReader(this.getDialect());
	}

	@Override
	protected ForeignKeyConstraintReader newForeignKeyConstraintReader() {
		return new FirebirdForeignKeyConstraintReader(this.getDialect());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.metadata.TableReader#
	 * newExcludeConstraintReader ()
	 */
	@Override
	protected ExcludeConstraintReader newExcludeConstraintReader() {
		return null;
	}
}
