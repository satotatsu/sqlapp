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

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;

import lombok.Getter;

/**
 * マッチングする行が見つからない場合
 * 
 * @author tatsuo satoh
 * 
 */
@Getter
public class CorrelationRowNotFoundException extends SqlappException {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 107328200084629983L;

	private final Table table;
	private final Row row;

	/**
	 * コンストラクタ
	 * 
	 * @param table Table
	 * @param row   Row
	 */
	public CorrelationRowNotFoundException(final Table table, final Row row) {
		super(createMessage(table, row));
		this.table = table;
		this.row = row;
	}

	private static String createMessage(final Table table, final Row row) {
		StringBuilder builder = new StringBuilder();
		builder.append("table=");
		builder.append(table.getName());
		builder.append(", row=");
		builder.append(row);
		return builder.toString();
	}

}
