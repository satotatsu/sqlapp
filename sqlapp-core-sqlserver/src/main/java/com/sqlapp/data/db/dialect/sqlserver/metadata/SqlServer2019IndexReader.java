/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * SqlServer2019のインデックス読み込みクラス
 * 
 * @author satoh
 * 
 */
public class SqlServer2019IndexReader extends SqlServer2016IndexReader {

	public SqlServer2019IndexReader(final Dialect dialect) {
		super(dialect);
	}

	/**
	 * OPTIMIZE_FOR_SEQUENTIAL_KEY
	 */
	public static final String OPTIMIZE_FOR_SEQUENTIAL_KEY = "OPTIMIZE_FOR_SEQUENTIAL_KEY";
	@Override
	protected Index createIndex(final ExResultSet rs) throws SQLException {
		final Index index = super.createIndex(rs);
		setSpecifics(rs, OPTIMIZE_FOR_SEQUENTIAL_KEY, index);
		return index;
	}
}