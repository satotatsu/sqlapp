/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-mariadb.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.mariadb.metadata;

import java.sql.Connection;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mysql.metadata.MySqlColumn570Reader;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.jdbc.ExResultSet;

public class MariadbColumn10_27Reader extends MySqlColumn570Reader {

	protected MariadbColumn10_27Reader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void setDefaultValue(final Connection connection, final ExResultSet rs, final Column column, final String def) {
		if (def != null) {
			column.setDefaultValue(def);
		}
	}

}
