/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.split;
import static com.sqlapp.util.CommonUtils.unwrap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.FunctionReader;
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.OnNullCall;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SqlSecurity;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.SeparatedStringBuilder;

/**
 * Postgresの関数読み込みクラス
 * 
 * @author satoh
 * 
 */
public class PostgresFunctionReader extends FunctionReader {

	protected PostgresFunctionReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Function> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Function> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Function function = createFunction(rs);
				result.add(function);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("functions.sql");
		return node;
	}

	protected Function createFunction(ExResultSet rs)
			throws SQLException {
		Function obj = new Function(getString(rs, FUNCTION_NAME));
		obj.setDialect(this.getDialect());
		// function.setSpecificName(getString(rs, "oid"));
		// function.setCatalogName(getString(rs, "function_catalog"));
		obj.setSchemaName(getString(rs, "function_schema"));
		obj.setLanguage(getString(rs, "lanname"));
		if(this.getReaderOptions().isReadStatement()){
			obj.setStatement(getString(rs, "prosrc"));
		}
		Boolean prosecdef=getBoolean(rs, "prosecdef");
		if (prosecdef!=null){
			if (prosecdef.booleanValue()){
				obj.setSqlSecurity(SqlSecurity.Definer);
			} else{
				obj.setSqlSecurity(SqlSecurity.Invoker);
			}
		}
		Boolean proisstrict=getBoolean(rs, "proisstrict");
		if (proisstrict!=null){
			if (proisstrict.booleanValue()){
				obj.setOnNullCall(OnNullCall.ReturnsNullOnNullInput);
			} else{
				obj.setOnNullCall(OnNullCall.CalledOnNullInput);
			}
		}
		String provolatile=getString(rs, "provolatile");
		if ("i".equalsIgnoreCase(provolatile)){
			obj.setDeterministic(true);
		}else if ("v".equalsIgnoreCase(provolatile)){
			obj.setDeterministic(false);
		}else if ("s".equalsIgnoreCase(provolatile)){
			obj.setStable(true);
		}
		String retTypeId = getString(rs, "prorettype");
		NamedArgument routineArgument = PostgresUtils.getTypeInfoById(
				rs.getStatement().getConnection(), this.getDialect(), retTypeId);
		obj.getReturning().setDataTypeName(routineArgument.getDataTypeName());
		obj.getReturning().setDataType(routineArgument.getDataType());
		obj.getReturning().setLength(routineArgument.getLength());
		obj.getReturning().setScale(routineArgument.getScale());
		int argNo = rs.getInt("pronargs");
		if (argNo > 0) {
			setArguments(rs, obj);
		}
		return obj;
	}
	
	protected void setArguments(ExResultSet rs, Function obj) throws SQLException{
		String allArgTypes = unwrap(rs.getString("proargtypes"), "{", "}");
		String allArgModes = unwrap(rs.getString("proargmodes"), "{", "}");
		String allArgNames = unwrap(rs.getString("proargnames"), "{", "}");
		String[] argArray = split(allArgTypes, "[, ]");
		String[] argModeArray = split(allArgModes, "[, ]");
		String[] argNameArray = split(allArgNames, "[, ]");
		if (allArgTypes != null) {
			SeparatedStringBuilder builder = new SeparatedStringBuilder(",");
			List<NamedArgument> arguments = PostgresUtils.getTypeInfoById(
					rs.getStatement().getConnection(), this.getDialect(), argArray, argNameArray,
					argModeArray);
			for (NamedArgument argument : arguments) {
				builder.add(argument.getDataTypeName());
			}
			obj.setSpecificName(obj.getName() + "(" + builder.toString()
					+ ")");
			obj.getArguments().addAll(arguments);
		}
	}

	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return null;
	}
}
