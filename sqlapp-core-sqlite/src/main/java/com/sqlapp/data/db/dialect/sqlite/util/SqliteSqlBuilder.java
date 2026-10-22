/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-spanner.
 *
 * sqlapp-core-spanner is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-spanner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-spanner.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlite.util;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Sqlite用のSQLビルダー
 * 
 * @author tatsuo satoh
 * 
 */
public class SqliteSqlBuilder extends AbstractSqlBuilder<SqliteSqlBuilder> {

	public SqliteSqlBuilder(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqliteSqlBuilder typeDefinition(Column column) {
		if ("true".equals(column.getSpecifics().get("sqlite.declaredType.empty")) && column.getDomain() == null)
			return this;
		String declared = column.getSpecifics().get("sqlite.declaredType");
		if (declared == null || column.getDomain() != null)
			return super.typeDefinition(column);
		// INT and INTEGER PRIMARY KEY have different rowid semantics. Do not
		// normalize catalog declarations through an inherited Schema dialect.
		return _add(declared);
	}

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public SqliteSqlBuilder clone() {
		return (SqliteSqlBuilder) super.clone();
	}

}
