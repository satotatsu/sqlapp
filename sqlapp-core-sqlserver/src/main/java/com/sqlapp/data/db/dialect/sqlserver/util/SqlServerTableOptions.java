/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.sqlserver.util;

import java.sql.SQLException;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;

public enum SqlServerTableOptions {
	LOCK_ESCALATION() {
		@Override
		public void setTable(final ExResultSet rs, Table table) throws SQLException {
			if (!rs.contains(this.toString())) {
				table.getSpecifics().remove(this.toString());
				return;
			}
			setTable(table, rs.getString(this.toString()));
		}
		@Override
		public void setTable(Table table, String value) {
			if ("AUTO".equals(value)||"TABLE".equals(value)||"DISABLE".equals(value)) {
				table.getSpecifics().put(this.toString(), value.toString());
			} else {
				table.getSpecifics().put(this.toString(), "TABLE");
			}
		}
	};

	public Object getDefaultValue() {
		return null;
	}

	public void setTable(Table table, String value) {
	}

	public void setTable(final ExResultSet rs, Table table) throws SQLException {	
	}
}
