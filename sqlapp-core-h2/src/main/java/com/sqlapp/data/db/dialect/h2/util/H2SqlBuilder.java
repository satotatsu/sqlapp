/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-h2.
 *
 * sqlapp-core-h2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-h2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-h2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.h2.util;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * H2用のSQLビルダー
 * 
 * @author tatsuo satoh
 * 
 */
public class H2SqlBuilder extends AbstractSqlBuilder<H2SqlBuilder> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	public H2SqlBuilder(Dialect dialect) {
		super(dialect);
	}

	/**
	 * ALIAS句を追加します
	 * 
	 */
	public H2SqlBuilder alias() {
		appendElement("ALIAS");
		return instance();
	}
	
	@Override
	public H2SqlBuilder clone(){
		return (H2SqlBuilder)super.clone();
	}

}
