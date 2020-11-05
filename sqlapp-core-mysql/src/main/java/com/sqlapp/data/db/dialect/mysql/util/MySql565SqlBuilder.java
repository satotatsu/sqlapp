/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.mysql.util;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;

/**
 * MySQL5.6.5用のSQLビルダー
 * 
 * @author tatsuo satoh
 * 
 */
public class MySql565SqlBuilder extends MySqlSqlBuilder {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	public MySql565SqlBuilder(Dialect dialect) {
		super(dialect);
	}
	
	@Override
	public MySql565SqlBuilder clone(){
		return (MySql565SqlBuilder)super.clone();
	}
	
	@Override
	protected void onUpdateDefinition(Column column){
		this.on().update().space()._add(column.getOnUpdate());
	}

}
