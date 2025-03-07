/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.MaskReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Mask;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

public class Db2_1010MaskReader extends MaskReader {

	protected Db2_1010MaskReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Mask> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final List<Mask> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Mask obj = createMask(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString(".sql");
	}

	protected Mask createMask(ExResultSet rs) throws SQLException {
		Mask obj = new Mask(getString(rs, "CONTROLNAME"));
		obj.setSchemaName(getString(rs, "CONTROLSCHEMA"));
		obj.setTableSchemaName(rs.getString("TABSCHEMA"));
		obj.setTableName(rs.getString("TABNAME"));
		obj.setColumnName(rs.getString("COLNAME"));
		obj.setCreatedAt(rs.getTimestamp("CREATE_TIME"));
		obj.setLastAlteredAt(rs.getTimestamp("ALTER_TIME"));
		obj.setEnable("Y".equalsIgnoreCase(getString(rs, "ENABLE")));
		obj.setValid("Y".equalsIgnoreCase(getString(rs, "VALID")));
		obj.setStatement(rs.getString("RULETEXT"));
		obj.setRemarks(getString(rs, REMARKS));
		setSpecifics(rs, "CLASS", obj);
		return obj;
	}
}
