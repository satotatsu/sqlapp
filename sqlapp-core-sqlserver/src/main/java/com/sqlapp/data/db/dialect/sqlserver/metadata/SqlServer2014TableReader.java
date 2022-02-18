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
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Table.TableType;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;

public class SqlServer2014TableReader extends SqlServer2012TableReader {

	protected SqlServer2014TableReader(Dialect dialect) {
		super(dialect);
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("tables2014.sql");
	}

	protected Table createTable(ExResultSet rs) throws SQLException {
		Table table = super.createTable(rs);
		Boolean bool=this.getBoolean(rs, "is_memory_optimized");
		if (bool!=null&&bool.booleanValue()) {
			table.setTableType(TableType.Memory);
			setSpecifics(rs, "durability", table);
		}
		return table;
	}

	@Override
	protected IndexReader newIndexReader() {
		return new SqlServer2014IndexReader(this.getDialect());
	}

}
