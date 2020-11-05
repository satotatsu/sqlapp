/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.information_schema.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.FunctionReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.ProductVersionInfo;

/**
 * INFORMATION_SCHEMAの関数読み込みクラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractISFunctionReader extends FunctionReader {

	protected AbstractISFunctionReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Function> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final List<Function> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Function routine = createFunction(rs);
				result.add(routine);
			}
		});
		return result;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache(AbstractISFunctionReader.class).getString(
				"functions.sql");
		return node;
	}

	protected Function createFunction(ExResultSet rs) throws SQLException {
		Function routine = new Function(getString(rs, "ROUTINE_NAME"));
		routine.setCatalogName(getString(rs, "ROUTINE_CATALOG"));
		routine.setSchemaName(getString(rs, "ROUTINE_SCHEMA"));
		routine.setCreatedAt(rs.getTimestamp("CREATED"));
		routine.setLastAlteredAt(rs.getTimestamp("LAST_ALTERED"));
		routine.setDefinition(getString(rs, "ROUTINE_DEFINITION"));
		return routine;
	}
}
