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

import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.FunctionType;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SeparatedStringBuilder;

/**
 * Postgres8.4 Function reader
 * 
 * @author satoh
 * 
 */
public class Postgres84FunctionReader extends PostgresFunctionReader {

	protected Postgres84FunctionReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Function createFunction(ExResultSet rs) throws SQLException {
		Function obj = super.createFunction(rs);
		if (this.getReaderOptions().isReadDefinition()) {
			obj.setDefinition(rs.getString("functiondef"));
		}
		Boolean proisagg = this.getBoolean(rs, "proisagg");
		if (proisagg != null && proisagg.booleanValue()) {
			obj.setFunctionType(FunctionType.Aggregate);
		}
		Boolean proiswindow = this.getBoolean(rs, "proiswindow");
		if (proiswindow != null && proiswindow.booleanValue()) {
			obj.setFunctionType(FunctionType.Window);
		}
		Boolean proretset = this.getBoolean(rs, "proretset");
		if (proretset != null && proretset.booleanValue()) {
			obj.setFunctionType(FunctionType.Table);
		}
		if (rs.getInt("pronargs") == 0) setArguments(rs, obj);
		String result = rs.getString("function_result");
		if (result != null && result.startsWith("TABLE(")) setReturningRecordType(rs, obj);
		// The model cannot represent local SET clauses or external library bindings.
		if (rs.getString("proconfig") != null
				|| rs.getString("probin") != null) obj.setDefinition(rs.getString("functiondef"));
		return obj;
	}

	@Override
	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("functions84.sql");
		return node;
	}

	@Override
	protected void setArguments(ExResultSet rs, Function obj) throws SQLException {
		Object[] types = array(rs, "proallargtypes");
		if (types == null) {
			String text = rs.getString("proargtypes");
			types = CommonUtils.isEmpty(text) ? new String[0] : text.trim().split(" +");
		}
		Object[] names = array(rs, "proargnames");
		Object[] modes = array(rs, "proargmodes");
		var defaults = PostgresRoutineExpressions.split(rs.getString("argument_defaults"));
		int inputCount = rs.getInt("pronargs");
		int inputIndex = 0;
		SeparatedStringBuilder identity = new SeparatedStringBuilder(",");
		for (int i = 0; i < types.length; i++) {
			NamedArgument argument = PostgresUtils.getTypeInfoById(rs.getStatement().getConnection(),
					getDialect(), types[i].toString());
			String mode = modes == null ? "i" : modes[i].toString();
			argument.setName(names == null || names[i] == null || names[i].toString().isEmpty() ? null : names[i].toString());
			if (argument.getName() == null) argument.getSpecifics().put("UNNAMED_ARGUMENT", true);
			argument.setDirection("o".equals(mode) || "t".equals(mode) ? ParameterDirection.Output
					: "b".equals(mode) ? ParameterDirection.Inout : ParameterDirection.Input);
			boolean input = argument.getDirection() != ParameterDirection.Output;
			if (input) {
				int defaultIndex = inputIndex++ - (inputCount - defaults.size());
				if (defaultIndex >= 0) argument.setDefaultValue(defaults.get(defaultIndex));
				identity.add(PostgresUtils.typeName(argument));
			}
			if ("v".equals(mode)) argument.getSpecifics().put("VARIADIC", true);
			if (!"t".equals(mode)) obj.getArguments().add(argument);
		}
		obj.setSpecificName(obj.getName() + "(" + identity.toString() + ")");
	}

	protected void setReturningRecordType(ExResultSet rs, Function obj) throws SQLException {
		Object[] types = array(rs, "proallargtypes");
		Object[] names = array(rs, "proargnames");
		Object[] modes = array(rs, "proargmodes");
		obj.getReturning().toTable();
		for (int i = 0; types != null && i < types.length; i++) {
			if (!"t".equals(modes[i].toString())) continue;
			NamedArgument argument = PostgresUtils.getTypeInfoById(rs.getStatement().getConnection(), getDialect(), types[i].toString());
			obj.getReturning().getTable().getColumns().add(names[i].toString(), column -> {
				column.setDataType(argument.getDataType()).setDataTypeName(argument.getDataTypeName())
						.setLength(argument.getLength()).setScale(argument.getScale()).setArrayDimension(argument.getArrayDimension());
			});
		}
		obj.setFunctionType(FunctionType.Table);
	}

	private Object[] array(ExResultSet rs, String name) throws SQLException {
		java.sql.Array array = rs.getArray(name);
		if (array == null) return null;
		try { return (Object[]) array.getArray(); }
		finally { array.free(); }
	}
}
