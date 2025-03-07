/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle.metadata;

import static com.sqlapp.util.CommonUtils.doubleKeyMap;
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
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.DoubleKeyMap;

/**
 * Oracleの関数作成クラス
 * 
 * @author satoh
 * 
 */
public class OracleFunctionReader extends FunctionReader {

	protected OracleFunctionReader(Dialect dialect) {
		super(dialect);
	}

	private static final String OBJECT_TYPE = "FUNCTION";

	@Override
	protected List<Function> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		context.put("objectType", OBJECT_TYPE);
		context.put("objectName", this.getObjectName(context));
		final DoubleKeyMap<String, String, Function> map = doubleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String schema_name = getString(rs, "OWNER");
				String name = getString(rs, "OBJECT_NAME");
				Function routine = createFunction(rs);
				map.put(schema_name, name, routine);
			}
		});
		List<Function> result= map.toList();
		ParametersContext cnt = new ParametersContext();
		DoubleKeyMap<String, String, List<String>> routines=OracleMetadataUtils.getRoutineSources(connection, this.getDialect(), cnt, result, OBJECT_TYPE);
		for(Function obj:result){
			List<String> source=routines.get(obj.getSchemaName(), obj.getName());
			String def=OracleMetadataUtils.getFunctionStatement(obj, source);
			if (def!=null){
				obj.setStatement(def);
			} else{
				obj.setDefinition(source);
			}
		}
		return result;
	}
	
	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("functions.sql");
	}

	protected Function createFunction(ExResultSet rs) throws SQLException {
		Function routine = new Function(getString(rs, "OBJECT_NAME"));
		routine.setSchemaName(getString(rs, "OWNER"));
		OracleMetadataUtils.setCommonInfo(rs, routine);
		FunctionReturning obj = new FunctionReturning(routine);
		String productDataType = getString(rs, "DATA_TYPE");
		long max_length = rs.getLong("CHAR_LENGTH");
		long precision = rs.getLong("DATA_PRECISION");
		Integer scale = getInteger(rs, "DATA_SCALE");
		String characterSetName=getString(rs, "CHARACTER_SET_NAME");
		routine.setDeterministic("YES".equalsIgnoreCase(getString(rs, "DETERMINISTIC")));
		routine.setParallel("YES".equalsIgnoreCase(getString(rs, "PARALLEL")));
		obj.setCharacterSet(characterSetName);
		this.getDialect().setDbType(productDataType, notZero(max_length, precision), scale, obj);
		if ("OBJECT".equals(productDataType)){
			String typeName=getString(rs, "TYPE_NAME");
			obj.setDataTypeName(typeName);
		} else{
			obj.setDataTypeName(productDataType);
		}
		routine.setReturning(obj);
		return routine;
	}
	
	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return new OracleFunctionArgumentReader(this.getDialect());
	}
}
