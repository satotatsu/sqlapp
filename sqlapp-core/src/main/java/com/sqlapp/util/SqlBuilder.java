/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.util;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.IndexType;

/**
 * SQL構築用のクラス
 * 
 * @author satoh
 * 
 */
public class SqlBuilder extends AbstractSqlBuilder<SqlBuilder> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -441196589676300634L;

	public SqlBuilder(Dialect dialect) {
		super(dialect);
	}

	/**
	 * 条件を満たす場合にインデックスタイプの追加を行います
	 * 
	 * @param value
	 * @param condition
	 */
	public SqlBuilder _add(IndexType value, boolean condition) {
		if (condition) {
			if (value!=IndexType.BTree){
				_add(value.toString());
			}
		}
		return instance();
	}
}
