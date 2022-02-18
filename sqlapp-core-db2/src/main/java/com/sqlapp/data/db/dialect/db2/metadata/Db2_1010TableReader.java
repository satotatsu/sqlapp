/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.db2.metadata;

import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;

public class Db2_1010TableReader extends Db2_980TableReader {

	protected Db2_1010TableReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Table createTable(final ExResultSet rs) throws SQLException {
		final Table table = super.createTable(rs);
		final String comp = getString(rs, "COMPRESSION");
		final String compMode = getString(rs, "ROWCOMPMODE");
		table.setCompression("B".equalsIgnoreCase(comp) || "V".equalsIgnoreCase(comp) || "R".equalsIgnoreCase(comp));
		if ("V".equalsIgnoreCase(comp)) {
			table.setCompressionType("VALUE");
		}
		if ("A".equalsIgnoreCase(compMode)) {
			//ADAPTIVE
		} else if("S".equalsIgnoreCase(compMode)){
			//STATIC
		}
		return table;
	}
}
