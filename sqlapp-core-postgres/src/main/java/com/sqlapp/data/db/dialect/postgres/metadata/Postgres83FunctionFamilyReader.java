/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.postgres.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.split;
import static com.sqlapp.util.CommonUtils.unwrap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.FunctionFamilyReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.FunctionFamily;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.QuadKeyMap;
import com.sqlapp.util.SeparatedStringBuilder;

/**
 * PostgresのFunctionFamily読み込みクラス
 * 
 * @author satoh
 * 
 */
public class Postgres83FunctionFamilyReader extends FunctionFamilyReader {

	protected Postgres83FunctionFamilyReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<FunctionFamily> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<FunctionFamily> result = list();
		final QuadKeyMap<String, String, String, String, FunctionFamily> map = CommonUtils
				.quadKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				FunctionFamily obj = createFunctionFamily(connection, rs);
				FunctionFamily obj1 = map.get(obj.getFunctionName(),
						obj.getSchemaName(), obj.getOperatorClassName(),
						obj.getFunctionName());
				if (obj1 == null) {
					obj = createFunctionFamily(connection, rs);
					map.put(obj.getFunctionName(), obj.getSchemaName(),
							obj.getOperatorClassName(), obj.getFunctionName(),
							obj);
					result.add(obj);
				}
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("functionFamilies83.sql");
	}

	protected FunctionFamily createFunctionFamily(final Connection connection,
			ExResultSet rs) throws SQLException {
		FunctionFamily obj = new FunctionFamily();
		obj.setDialect(this.getDialect());
		obj.setOperatorClassName(getString(rs, "operator_class_name"));
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		obj.setSupportNumber(rs.getInt("amprocnum"));
		Function function = new Function(getString(rs, FUNCTION_NAME));
		function.setSchemaName(getString(rs, "function_schema"));
		int argNo = rs.getInt("pronargs");
		if (argNo > 0) {
			String allArgTypes = unwrap(rs.getString("proargtypes"), "{", "}");
			if (allArgTypes != null) {
				String[] argArray = split(allArgTypes, "[, ]");
				SeparatedStringBuilder builder = new SeparatedStringBuilder(",");
				List<NamedArgument> arguments = PostgresUtils.getTypeInfoById(
						connection, this.getDialect(), argArray);
				for (NamedArgument argument : arguments) {
					builder.add(argument.getDataTypeName());
				}
				function.setSpecificName(function.getName() + "("
						+ builder.toString() + ")");
			}
		}
		obj.setFunction(function);
		return obj;
	}

}
