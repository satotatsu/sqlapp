/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql.metadata;

import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;

/**
 * MySqlの関数作成クラス
 * 
 * @author satoh
 * 
 */
public class MySqlFunction553Reader extends MySqlFunctionReader {

	protected MySqlFunction553Reader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		if (productVersionInfo.gte(5, 5, 3)) {
			return getSqlNodeCache().getString("functions553.sql");
		} else {
			return super.getSqlSqlNode(productVersionInfo);
		}
	}

	@Override
	protected Function createFunction(ExResultSet rs,
			ProductVersionInfo productVersionInfo) throws SQLException {
		if (productVersionInfo.lt(5, 5, 3)) {
			return super.createFunction(rs, productVersionInfo);
		}
		Function obj = new Function(getString(rs, ROUTINE_NAME));
		obj.setDialect(getDialect());
		MySqlUtils.setRoutineInfo(rs, obj);
		String productDataType = getString(rs, "DATA_TYPE");
		long maxLength = rs.getLong("CHARACTER_MAXIMUM_LENGTH");
		long octetLength = rs.getLong("CHARACTER_OCTET_LENGTH");
		long numericPrecision = rs.getLong("NUMERIC_PRECISION");
		Integer numericScale = getInteger(rs, "NUMERIC_SCALE");
		obj.getReturning().setCharacterSet(getString(rs, CHARACTER_SET_NAME));
		obj.getReturning().setCollation(getString(rs, COLLATION_NAME));
		obj.getReturning().setDataTypeName(productDataType);
		this.getDialect().setDbType(productDataType, CommonUtils.notZero(maxLength, numericPrecision), numericScale, obj.getReturning());
		obj.getReturning().setOctetLength(octetLength);
		return obj;
	}

	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return new MySqlFunctionArgument553Reader(this.getDialect());
	}
}
