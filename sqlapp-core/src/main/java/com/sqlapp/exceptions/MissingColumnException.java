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
 * ColumnSelectionStrategyの条件のカラムに値が未設定の場合
 * 
 * @author tatsuo satoh
 * 
 */
public class MissingColumnException extends SqlappException {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 107328200084629983L;

	/**
	 * フィールド名
	 */
	private final String column;
	/**
	 * オブジェクト
	 */
	private final Table table;
	/**
	 * フィールド名
	 */
	private final String expression;

	/**
	 * コンストラクタ
	 * 
	 * @param table      Table
	 * @param column     column
	 * @param expression expression
	 * @param t          例外
	 */
	public MissingColumnException(Table table, String column, String expression) {
		super(createMessage(table, column, expression));
		this.table = table;
		this.column = column;
		this.expression = expression;
	}

	private static String createMessage(Table table, String column, String expression) {
		StringBuilder builder = new StringBuilder();
		builder.append("table=");
		builder.append(table.getName());
		builder.append(", column=");
		builder.append(column);
		builder.append(", expression=");
		builder.append(expression);
		return builder.toString();
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
	}

	public String getColumn() {
		return column;
	}

	public Table getTable() {
		return table;
	}

	public String getExpression() {
		return expression;
	}

}
