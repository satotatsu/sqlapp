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

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.notZero;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.FunctionReader;
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.FunctionReturning;
import com.sqlapp.data.schemas.FunctionType;
import com.sqlapp.data.schemas.OnNullCall;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * SqlServer2005の関数読み込みクラス
 * 
 * @author satoh
 * 
 */
public class SqlServer2005FunctionReader extends FunctionReader {

	protected SqlServer2005FunctionReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Function> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Function> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Function obj = createFunction(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("functions2005.sql");
	}

	protected Function createFunction(ExResultSet rs) throws SQLException {
		Function obj = new Function(getString(rs, FUNCTION_NAME));
		obj.setDialect(getDialect());
		obj.setCatalogName(getString(rs, CATALOG_NAME));
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		obj.setClassNamePrefix(getString(rs, "assembly_name"));
		obj.setClassName(getString(rs, "assembly_class"));
		obj.setMethodName(getString(rs, "assembly_method"));
		/*
		 * 'AF' --集計関数 (CLR) 'FN' --SQL スカラー関数 'FS' --アセンブリ (CLR)スカラー関数 'FT'
		 * --アセンブリ (CLR) テーブル値関数 'IF' --SQL インライン テーブル値関数 'TF' --SQL テーブル値関数
		 */
		String type = rs.getString("type");
		if ("AF".equalsIgnoreCase(type)){
			obj.setFunctionType(FunctionType.Aggregate);
		} else if ("FN".equalsIgnoreCase(type)||"FS".equalsIgnoreCase(type)){
			obj.setFunctionType(FunctionType.Scalar);
		}else if ("FT".equalsIgnoreCase(type)||"IF".equalsIgnoreCase(type)||"TF".equalsIgnoreCase(type)){
			obj.setFunctionType(FunctionType.Table);
		}
		String definition = getString(rs, "definition");
		if (this.getReaderOptions().isReadDefinition()) {
			obj.setDefinition(definition);
		}else if (this.getReaderOptions().isReadStatement()) {
			obj.setStatement(SqlServerUtils.getFunctionStatement(definition,
					type));
		}
		obj.setCreatedAt(rs.getTimestamp("create_date"));
		obj.setLastAlteredAt(rs.getTimestamp("modify_date"));
		obj.setSqlSecurity(getString(rs, "sql_security"));
		obj.setExecuteAs(getString(rs, "execute_as"));
		Boolean nullOnNullInput = this.getBoolean(rs, "null_on_null_input");
		if (nullOnNullInput != null && nullOnNullInput.booleanValue()) {
			obj.setOnNullCall(OnNullCall.ReturnsNullOnNullInput);
		} else {
			obj.setOnNullCall(OnNullCall.CalledOnNullInput);
		}
		// setDbSpecificInfo(rs, "execute_as", obj);
		setSpecifics(rs, "assembly_id", obj);
		FunctionReturning ret = obj.getReturning();
		ret.setName(SqlServerUtils.getFunctionReturnName(definition));
		if ("FT".equalsIgnoreCase(type) || "TF".equalsIgnoreCase(type)) {
			String tableDef = SqlServerUtils.getFunctionReturnTable(definition);
			ret.setDefinition(tableDef);
			obj.setFunctionType(FunctionType.Table);
		} else if ("IF".equalsIgnoreCase(type)) {
			obj.setFunctionType(FunctionType.Table);
		} else {
			String productDataType = getString(rs, "NAME");
			Long max_length = getLong(rs, "max_length");
			Long precision = getLong(rs, "precision");
			Integer scale = getInteger(rs, "scale");
			this.getDialect().setDbType(productDataType,
					notZero(max_length, precision), scale, ret);
		}
		return obj;
	}

	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return new SqlServer2005FunctionArgumentReader(this.getDialect());
	}
}
