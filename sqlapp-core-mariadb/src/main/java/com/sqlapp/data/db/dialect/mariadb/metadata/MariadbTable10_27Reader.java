/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mariadb.
 *
 * sqlapp-core-mariadb is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mariadb is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mariadb.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mariadb.metadata;


import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mysql.metadata.MySqlTable564Reader;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.CheckConstraintReader;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;

public class MariadbTable10_27Reader extends MySqlTable564Reader {

	protected MariadbTable10_27Reader(Dialect dialect) {
		super(dialect);
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.metadata.TableReader#newColumnReader()
	 */
	@Override
	protected ColumnReader newColumnReader() {
		return new MariadbColumn10_27Reader(this.getDialect());
	}

	@Override
	protected CheckConstraintReader newCheckConstraintReader() {
		return new MariadbCheckConstraintReader(getDialect());
	}

	@Override
	protected Table createTable(ExResultSet rs) throws java.sql.SQLException {
		Table table = super.createTable(rs);
		String rowFormat = getString(rs, "ROW_FORMAT");
		if (rowFormat != null) {
			table.getSpecifics().put("ROW_FORMAT", rowFormat);
		}
		String createOptions = getString(rs, "CREATE_OPTIONS");
		if (createOptions != null && !createOptions.isBlank()) {
			table.getSpecifics().put("CREATE_OPTIONS", createOptions);
		}
		if ("SYSTEM VERSIONED".equalsIgnoreCase(getString(rs, "TABLE_TYPE"))) {
			table.toSystemVersioning().setImplicit(true);
		}
		return table;
	}
}
