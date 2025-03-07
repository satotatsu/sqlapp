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
import com.sqlapp.data.db.dialect.information_schema.metadata.AbstractISViewReader;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.ExcludeConstraintReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;

/**
 * SqlServer2000のビュー読み込み
 * 
 * @author satoh
 * 
 */
public class SqlServer2000ViewReader extends AbstractISViewReader {

	protected SqlServer2000ViewReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new SqlServer2000ColumnReader(this.getDialect());
	}

	@Override
	protected Table createTable(ExResultSet rs) throws SQLException {
		Table obj = super.createTable(getString(rs, TABLE_NAME));
		obj.setCatalogName(getString(rs, TABLE_CATALOG));
		obj.setSchemaName(getString(rs, TABLE_SCHEMA));
		// TODO
		// table.setCheckOption(getString(rs, "CHECK_OPTION"));
		// table.setUpdatable(getString(rs, "IS_UPDATABLE"));
		String difinition = getString(rs, "view_definition");
		if (this.getReaderOptions().isReadDefinition()) {
			obj.setDefinition(difinition);
		}
		if (this.getReaderOptions().isReadStatement()) {
			obj.setStatement(SqlServerUtils.getViewStatement(difinition));
		}
		return obj;
	}

	@Override
	protected IndexReader newIndexReader() {
		return new SqlServer2000IndexReader(this.getDialect());
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
