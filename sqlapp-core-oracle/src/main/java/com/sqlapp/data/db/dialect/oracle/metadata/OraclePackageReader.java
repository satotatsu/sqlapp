/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static com.sqlapp.util.CommonUtils.doubleKeyMap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.PackageReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Package;
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
public class OraclePackageReader extends PackageReader {

	protected OraclePackageReader(Dialect dialect) {
		super(dialect);
	}

	private static final String OBJECT_TYPE = "PACKAGE";

	@Override
	protected List<Package> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		context.put("objectType", OBJECT_TYPE);
		context.put("objectName", this.getObjectName(context));
		final DoubleKeyMap<String, String, Package> map = doubleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String schemaName = getString(rs, "OWNER");
				String name = getString(rs, "OBJECT_NAME");
				Package routine = map.get(schemaName, name);
				if (routine == null) {
					routine = createPackage(rs);
					map.put(schemaName, name, routine);
				}
			}
		});
		List<Package> result= map.toList();
		ParametersContext cnt = new ParametersContext();
		DoubleKeyMap<String, String, List<String>> routines=OracleMetadataUtils.getRoutineSources(connection, this.getDialect(), cnt, result, OBJECT_TYPE);
		for(Package obj:result){
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

	protected Package createPackage(ExResultSet rs) throws SQLException {
		String schemaName = getString(rs, "OWNER");
		String name = getString(rs, "OBJECT_NAME");
		Package obj = new Package(name);
		obj.setSchemaName(schemaName);
		OracleMetadataUtils.setCommonInfo(rs, obj);
		return obj;
	}
	
	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("routines.sql");
	}
}
