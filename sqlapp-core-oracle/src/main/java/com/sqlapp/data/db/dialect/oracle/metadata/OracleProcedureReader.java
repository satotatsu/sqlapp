/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.oracle.metadata;

import static com.sqlapp.util.CommonUtils.doubleKeyMap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ProcedureReader;
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.DoubleKeyMap;

/**
 * Oracleのプロシージャ作成クラス
 * 
 * @author satoh
 * 
 */
public class OracleProcedureReader extends ProcedureReader {

	protected OracleProcedureReader(Dialect dialect) {
		super(dialect);
	}

	private static final String OBJECT_TYPE = "PROCEDURE";

	@Override
	protected List<Procedure> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		context.put("objectType", OBJECT_TYPE);
		context.put("objectName", this.getObjectName(context));
		final DoubleKeyMap<String, String, Procedure> map = doubleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String schema_name = getString(rs, "OWNER");
				String name = getString(rs, "OBJECT_NAME");
				Procedure routine = map.get(schema_name, name);
				if (routine == null) {
					routine = createProcedure(rs);
					map.put(schema_name, name, routine);
				}
			}
		});
		List<Procedure> result= map.toList();
		ParametersContext cnt = new ParametersContext();
		DoubleKeyMap<String, String, List<String>> routines=OracleMetadataUtils.getRoutineSources(connection, this.getDialect(), cnt, result, OBJECT_TYPE);
		for(Procedure obj:result){
			List<String> source=routines.get(obj.getSchemaName(), obj.getName());
			String def=OracleMetadataUtils.getProcedureStatement(obj, source);
			if (def!=null){
				obj.setStatement(def);
			} else{
				obj.setDefinition(source);
			}
		}
		return result;
	}
	
	protected Procedure createProcedure(ExResultSet rs) throws SQLException{
		Procedure obj = new Procedure(getString(rs, "OWNER"));
		OracleMetadataUtils.setCommonInfo(rs, obj);
		obj.setDeterministic("YES".equalsIgnoreCase(getString(rs, "DETERMINISTIC")));
		obj.setParallel("YES".equalsIgnoreCase(getString(rs, "PARALLEL")));
		return obj;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("procedures.sql");
	}

	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return new OracleProcedureArgumentReader(this.getDialect());
	}
}
