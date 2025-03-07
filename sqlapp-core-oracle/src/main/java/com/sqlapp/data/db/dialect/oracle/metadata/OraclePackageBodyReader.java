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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.PackageBodyReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.PackageBody;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.DoubleKeyMap;

/**
 * Oracleのパッケージ作成クラス
 * 
 * @author satoh
 * 
 */
public class OraclePackageBodyReader extends PackageBodyReader {

	protected OraclePackageBodyReader(Dialect dialect) {
		super(dialect);
	}

	private static final String OBJECT_TYPE = "PACKAGE BODY";

	@Override
	protected List<PackageBody> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		context.put("objectType", OBJECT_TYPE);
		context.put("objectName", this.getObjectName(context));
		final DoubleKeyMap<String, String, PackageBody> map = doubleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String schema_name = getString(rs, "OWNER");
				String name = getString(rs, "OBJECT_NAME");
				PackageBody routine = map.get(schema_name, name);
				if (routine == null) {
					routine = createPackageBody(rs);
					map.put(schema_name, name, routine);
				}
			}
		});
		List<PackageBody> result= map.toList();
		ParametersContext cnt = new ParametersContext();
		DoubleKeyMap<String, String, List<String>> routines=OracleMetadataUtils.getRoutineSources(connection, this.getDialect(), cnt, result, OBJECT_TYPE);
		for(PackageBody obj:result){
			List<String> source=routines.get(obj.getSchemaName(), obj.getName());
			String def=OracleMetadataUtils.getPackageStatement(obj, source);
			if (def!=null){
				obj.setStatement(def);
			} else{
				obj.setDefinition(source);
			}
		}
		return result;
	}

	protected PackageBody createPackageBody(ExResultSet rs) throws SQLException {
		String schemaName = getString(rs, "OWNER");
		String name = getString(rs, "OBJECT_NAME");
		PackageBody obj = new PackageBody(name);
		obj.setSchemaName(schemaName);
		OracleMetadataUtils.setCommonInfo(rs, obj);
		return obj;
	}
	
	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("routines.sql");
	}
}
