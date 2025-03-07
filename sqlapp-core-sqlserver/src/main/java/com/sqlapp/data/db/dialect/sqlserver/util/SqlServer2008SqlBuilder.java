/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver.util;

import com.sqlapp.data.db.dialect.Dialect;

public class SqlServer2008SqlBuilder extends SqlServerSqlBuilder{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1224674967046374408L;

	public SqlServer2008SqlBuilder(Dialect dialect) {
		super(dialect);
	}

	
	public SqlServer2008SqlBuilder count(){
		appendElement("COUNT_BIG");
		return instance();
	}

	@Override
	public SqlServer2008SqlBuilder instance(){
		return (SqlServer2008SqlBuilder)super.instance();
	}
	
	@Override
	public SqlServer2008SqlBuilder create() {
		appendElement("CREATE");
		return instance();
	}
}
