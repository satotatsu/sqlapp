/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql.metadata;

import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.information_schema.metadata.AbstractISViewReader;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.ExcludeConstraintReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.db.dialect.mysql.sql.MySqlCreateViewFactory;
import com.sqlapp.data.db.dialect.mysql.util.MySqlDefiner;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.jdbc.ExResultSet;

/**
 * MySqlのビュー読み込み
 * 
 * @author satoh
 * 
 */
public class MySqlViewReader extends AbstractISViewReader {

	protected MySqlViewReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Table createTable(ExResultSet rs) throws SQLException {
		Table table = createTable(getString(rs, TABLE_NAME));
		table.setCatalogName(getString(rs, TABLE_CATALOG));
		table.setSchemaName(getString(rs, TABLE_SCHEMA));
		Boolean updatable = toBoolean(getString(rs, "IS_UPDATABLE"));
		if (updatable != null) {
			table.setReadonly(!updatable.booleanValue());
		}
		table.setStatement(getString(rs, "VIEW_DEFINITION"));
		MySqlDefiner.read(table.getSpecifics(), rs.getString("DEFINER"));
		com.sqlapp.data.db.dialect.mysql.util.MySqlStoredObjectContext.read(rs, table);
		String check = getString(rs, "CHECK_OPTION");
		if (check != null && !"NONE".equalsIgnoreCase(check)) {
			table.getSpecifics().put(MySqlCreateViewFactory.CHECK_OPTION, check);
		}
		String security = getString(rs, "SECURITY_TYPE");
		if (security != null) {
			table.getSpecifics().put(MySqlCreateViewFactory.SECURITY_TYPE, security);
		}
		if ("MariaDB".equalsIgnoreCase(getDialect().getProductName())) {
			String algorithm = getString(rs, "ALGORITHM");
			if (algorithm != null) {
				table.getSpecifics().put(MySqlCreateViewFactory.ALGORITHM, algorithm);
			}
		}
		return table;
	}

	@Override
	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache(MySqlViewReader.class).getString("views.sql");
	}

	@Override
	protected ColumnReader newColumnReader() {
		return getDialect().getCatalogReader().getSchemaReader().getTableReader().getColumnReader();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.metadata.TableReader#newIndexReader()
	 */
	@Override
	protected IndexReader newIndexReader() {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.dialect.metadata.TableReader#newExcludeConstraintReader ()
	 */
	@Override
	protected ExcludeConstraintReader newExcludeConstraintReader() {
		return null;
	}
}
