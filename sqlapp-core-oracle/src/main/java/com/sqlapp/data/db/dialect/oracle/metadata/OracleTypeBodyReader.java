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

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TypeBodyReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.TypeBody;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.DoubleKeyMap;

/**
 * OracleのTYPE BODY作成クラス
 * 
 * @author satoh
 * 
 */
public class OracleTypeBodyReader extends TypeBodyReader {

	protected OracleTypeBodyReader(Dialect dialect) {
		super(dialect);
	}

	private static final String OBJECT_TYPE = "TYPE BODY";

	@Override
	protected List<TypeBody> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<TypeBody> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				TypeBody obj = createTypeBody(rs);
				result.add(obj);
			}
		});
		ParametersContext cnt = new ParametersContext();
		DoubleKeyMap<String, String, List<String>> routines=OracleMetadataUtils.getRoutineSources(connection, this.getDialect(), cnt, result, OBJECT_TYPE);
		for(TypeBody obj:result){
			obj.setDefinition(routines.get(obj.getSchemaName(), obj.getName()));
		}
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("typeBody.sql");
	}

	protected TypeBody createTypeBody(ExResultSet rs) throws SQLException {
		TypeBody obj = new TypeBody(getString(rs, "TYPE_NAME"));
		obj.setSchemaName(getString(rs, "OWNER"));
		obj.setCreatedAt(rs.getTimestamp("CREATED"));
		obj.setLastAlteredAt(rs.getTimestamp("LAST_DDL_TIME"));
		if (!"VALID".equalsIgnoreCase(getString(rs, "STATUS"))) {
			obj.setValid(false);
		}
		return obj;
	}
}
