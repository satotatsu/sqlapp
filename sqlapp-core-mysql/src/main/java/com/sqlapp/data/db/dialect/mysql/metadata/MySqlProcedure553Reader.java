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
package com.sqlapp.data.db.dialect.mysql.metadata;

import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;

public class MySqlProcedure553Reader extends MySqlProcedureReader {

	protected MySqlProcedure553Reader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		if (productVersionInfo.gte(5, 5, 3)) {
			return getSqlNodeCache().getString("procedures553.sql");
		} else {
			return super.getSqlSqlNode(productVersionInfo);
		}
	}

	@Override
	protected Procedure createProcedure(ExResultSet rs,
			ProductVersionInfo productVersionInfo) throws SQLException {
		if (productVersionInfo.lt(5, 5, 3)) {
			return super.createProcedure(rs, productVersionInfo);
		}
		Procedure obj = new Procedure(getString(rs, ROUTINE_NAME));
		obj.setDialect(getDialect());
		MySqlUtils.setRoutineInfo(rs, obj);
		return obj;
	}

	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return new MySqlProcedureArgument553Reader(this.getDialect());
	}

}
