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

package com.sqlapp.jdbc;

import java.sql.Connection;

import javax.sql.DataSource;

/**
 * ログ出力のためのデータソース
 * 
 * @author satoh
 * 
 */
public class SqlappDataSource extends AbstractDataSource {

	public SqlappDataSource(DataSource nativeObject) {
		super(nativeObject);
	}

	@Override
	protected SqlappConnection getConnection(Connection connection) {
		if (connection instanceof SqlappConnection) {
			SqlappConnection conn= (SqlappConnection)connection;
			this.initializeChild(conn);
			return conn;
		}
		SqlappConnection conn=  new SqlappConnection(connection);
		this.initializeChild(conn);
		return conn;
	}
	
	
	/**
	 * @param debug the debug to set
	 */
	@Override
	public void setDebug(boolean debug) {
		super.setDebug(debug);
	}
}
