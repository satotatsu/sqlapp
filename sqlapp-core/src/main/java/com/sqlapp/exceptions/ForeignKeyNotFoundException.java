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

import com.sqlapp.data.schemas.Table;

/**
 * ForeignKeyNotFoundException
 * 
 * @author tatsuo satoh
 * 
 */
public class ForeignKeyNotFoundException extends SqlappException {
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
	private final String foreignKeyName;
	/**
	 * オブジェクト
	 */
	private final Table relatedTable;

	/**
	 * コンストラクタ
	 * 
	 * @param foreignKeyName Foreign Key Name
	 * @param table          Table
	 * @param relatedTable   Table
	 * @param t              例外
	 */
	public ForeignKeyNotFoundException(String foreignKeyName, Table table, Table relatedTable) {
		super(createMessage(foreignKeyName, table, relatedTable));
		this.foreignKeyName = foreignKeyName;
		this.table = table;
		this.relatedTable = relatedTable;
	}

	private static String createMessage(String foreignKeyName, Table table, Table relatedTable) {
		StringBuilder builder = new StringBuilder();
		builder.append("foreignKeyName=");
		builder.append(foreignKeyName);
		if (table != null) {
			builder.append(", table=");
			builder.append(table.getName());
		}
		if (relatedTable != null) {
			builder.append(", relatedTable=");
			builder.append(relatedTable.getName());
		}
		return builder.toString();
	}

	public String getForeignKeyName() {
		return foreignKeyName;
	}

	public Table getTable() {
		return table;
	}

	public Table getRelatedTable() {
		return relatedTable;
	}

}
