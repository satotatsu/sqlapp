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
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.FunctionReader;
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.FunctionType;
import com.sqlapp.data.schemas.OnNullCall;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SqlDataAccess;

/**
 * DB2の関数読み込みクラス
 *
 * @author satoh
 *
 */
public class Db2FunctionReader extends FunctionReader {

	protected Db2FunctionReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Function> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
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

	protected Function createFunction(ExResultSet rs) throws SQLException {
		Function obj = new Function(getString(rs, ROUTINE_NAME));
		Db2Utils.setRutine(this.getDialect(), rs, this.getReaderOptions(), obj);
		obj.setDeterministic("Y".equalsIgnoreCase(this.getString(rs, "DETERMINISTIC")));
		obj.setParallel("Y".equalsIgnoreCase(this.getString(rs, "PARALLEL")));
		String type = this.getString(rs, "FUNCTIONTYPE");
		String definition = getString(rs, "ROUTINE_DEFINITION");
		if ("R".equalsIgnoreCase(type)) {
			obj.setFunctionType(FunctionType.Row);
			setRowTableDefinition(obj, definition);
		} else if ("T".equalsIgnoreCase(type)) {
			// テーブル
			obj.setFunctionType(FunctionType.Table);
			setRowTableDefinition(obj, definition);
		} else if ("C".equalsIgnoreCase(type)) {
			// 列または集約
			obj.setFunctionType(FunctionType.Aggregate);
		} else if ("S".equalsIgnoreCase(type)) {
			// スカラー
			obj.setFunctionType(FunctionType.Scalar);
			String returnType = getString(rs, "RETURN_TYPE_NAME");
			if (returnType != null) {
				getDialect().setDbType(returnType, getLong(rs, "RETURN_LENGTH"), getInteger(rs, "RETURN_SCALE"),
						obj.getReturning());
			}
		}
		String nullCall = this.getString(rs, "NULLCALL");
		if ("N".equalsIgnoreCase(nullCall)) {
			obj.setOnNullCall(OnNullCall.ReturnsNullOnNullInput);
		} else if ("Y".equalsIgnoreCase(nullCall)) {
			obj.setOnNullCall(OnNullCall.CalledOnNullInput);
		}
		String sqlDataAccess = getString(rs, "SQL_DATA_ACCESS");
		if ("C".equalsIgnoreCase(sqlDataAccess)) {
			obj.setSqlDataAccess(SqlDataAccess.ContainsSql);
		} else if ("M".equalsIgnoreCase(sqlDataAccess)) {
			obj.setSqlDataAccess(SqlDataAccess.ModifiesSqlData);
		} else if ("N".equalsIgnoreCase(sqlDataAccess)) {
			obj.setSqlDataAccess(SqlDataAccess.NoSql);
		} else if ("R".equalsIgnoreCase(sqlDataAccess)) {
			obj.setSqlDataAccess(SqlDataAccess.ReadsSqlData);
		}
		setSpecifics(rs, "DIALECT", obj);
		return obj;
	}

	private static final Pattern RETURN_COLUMNS = Pattern.compile("\\bRETURNS\\s+(TABLE|ROW)\\s*\\(",
			Pattern.CASE_INSENSITIVE);

	protected void setRowTableDefinition(Function obj, String definition) {
		if (definition == null || (!obj.getFunctionType().isTable() && !obj.getFunctionType().isRow())) {
			return;
		}
		Matcher matcher = RETURN_COLUMNS.matcher(definition);
		if (matcher.find()) {
			obj.getReturning().toTable();
			setColumnDefinitions(obj, definition.substring(matcher.end()));
		}
	}

	protected void setColumnDefinitions(Function obj, String columnDefs) {
		int end = searchEndOfColumns(columnDefs);
		String columns = columnDefs.substring(0, end);
		int start = 0;
		int depth = 0;
		char quote = 0;
		for (int i = 0; i < columns.length(); i++) {
			char c = columns.charAt(i);
			if (quote != 0) {
				if (c == quote) {
					if (i + 1 < columns.length() && columns.charAt(i + 1) == quote)
						i++;
					else
						quote = 0;
				}
			} else if (c == '"' || c == '\'') {
				quote = c;
			} else if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth--;
			} else if (c == ',' && depth == 0) {
				setColumnDefinition(obj, columns.substring(start, i).trim());
				start = i + 1;
			}
		}
		if (start < columns.length())
			setColumnDefinition(obj, columns.substring(start).trim());
	}

	private static final Pattern COLUMN_PATTERN = Pattern.compile("^(?<name>\"(?:[^\"]|\"\")*\"|\\S+)\\s+(?<def>.*)",
			Pattern.DOTALL);

	protected int searchEndOfColumns(String columnDefs) {
		int depth = 0;
		char quote = 0;
		for (int i = 0; i < columnDefs.length(); i++) {
			char c = columnDefs.charAt(i);
			if (quote != 0) {
				if (c == quote) {
					if (i + 1 < columnDefs.length() && columnDefs.charAt(i + 1) == quote)
						i++;
					else
						quote = 0;
				}
			} else if (c == '"' || c == '\'') {
				quote = c;
			} else if (c == '(') {
				depth++;
			} else if (c == ')' && --depth < 0) {
				return i;
			}
		}
		return columnDefs.length();
	}

	protected void setColumnDefinition(Function obj, String columnDef) {
		Matcher matcher = COLUMN_PATTERN.matcher(columnDef);
		if (!matcher.matches()) {
			throw new IllegalArgumentException("Invalid Db2 function return column: " + columnDef);
		}
		String name = matcher.group("name");
		if (name.startsWith("\""))
			name = name.substring(1, name.length() - 1).replace("\"\"", "\"");
		final String definition = matcher.group("def");
		obj.getReturning().getTable().getColumns().add(name, c -> c.setDataTypeName(definition));
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("functions.sql");
		return node;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.sqlapp.data.db.metadata.RoutineReader#newRoutineArgumentReader()
	 */
	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return new Db2FunctionArgumentReader(this.getDialect());
	}

}
