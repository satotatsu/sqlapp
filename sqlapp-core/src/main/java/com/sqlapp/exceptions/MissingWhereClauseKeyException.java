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

import com.sqlapp.data.db.sql.ColumnSelectionStrategy;
import com.sqlapp.data.schemas.Table;

/**
 * ColumnSelectionStrategyの条件のカラムに値が未設定の場合
 * 
 * @author tatsuo satoh
 * 
 */
public class MissingWhereClauseKeyException extends SqlappException {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 107328200084629983L;

	/**
	 * フィールド名
	 */
	private final ColumnSelectionStrategy columnSelectionStrategy;
	/**
	 * オブジェクト
	 */
	private final Table table;

	/**
	 * コンストラクタ
	 * 
	 * @param table                   Table
	 * @param columnSelectionStrategy columnSelectionStrategy
	 * @param t                       例外
	 */
	public MissingWhereClauseKeyException(Table table, ColumnSelectionStrategy columnSelectionStrategy) {
		super(createMessage(table, columnSelectionStrategy));
		this.table = table;
		this.columnSelectionStrategy = columnSelectionStrategy;
	}

	private static String createMessage(Table table, ColumnSelectionStrategy columnSelectionStrategy) {
		StringBuilder builder = new StringBuilder();
		builder.append("table=");
		builder.append(table.getName());
		builder.append(", columnSelectionStrategy=");
		builder.append(columnSelectionStrategy);
		return builder.toString();
	}

	public ColumnSelectionStrategy getColumnSelectionStrategy() {
		return columnSelectionStrategy;
	}

	public Table getTable() {
		return table;
	}

}
