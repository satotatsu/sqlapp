/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.exceptions;

import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

/**
 * Parent Table Not Found Exception
 * 
 * @author tatsuo satoh
 * 
 */
public class ParentTableNotFoundException extends SqlappException {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 107328200084629983L;

	/**
	 * オブジェクト
	 */
	private final Table table;
	/**
	 * オブジェクト
	 */
	private final SqlType sqlType;

	/**
	 * コンストラクタ
	 * 
	 * @param table                   Table
	 * @param columnSelectionStrategy columnSelectionStrategy
	 * @param t                       例外
	 */
	public ParentTableNotFoundException(Table table, SqlType sqlType) {
		super(createMessage(table, sqlType));
		this.table = table;
		this.sqlType = sqlType;
	}

	private static String createMessage(Table table, SqlType sqlType) {
		StringBuilder builder = new StringBuilder();
		builder.append("table=");
		builder.append(table.getName());
		builder.append(", sqlType=");
		builder.append(sqlType);
		return builder.toString();
	}

	public SqlType getSqlType() {
		return sqlType;
	}

	public Table getTable() {
		return table;
	}

}
