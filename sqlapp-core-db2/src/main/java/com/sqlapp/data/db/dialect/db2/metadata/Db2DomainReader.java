/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-db2.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.db2.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.DomainReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.ProductVersionInfo;
/**
 * DB2のドメイン読み込みクラス
 * 
 * @author satoh
 * 
 */
public class Db2DomainReader extends DomainReader {

	protected Db2DomainReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Domain> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final List<Domain> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Domain obj = createDomain(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("domains.sql");
	}

	protected Domain createDomain(ExResultSet rs) throws SQLException {
		Domain obj = new Domain(getString(rs, DOMAIN_NAME));
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		obj.setCreatedAt(rs.getTimestamp("CREATE_TIME"));
		obj.setLastAlteredAt(rs.getTimestamp("ALTER_TIME"));
		obj.setValid("Y".equalsIgnoreCase(getString(rs, "VALID")));
		String productDataType = getString(rs, "sourcename");
		Long length = rs.getLongValue("LENGTH");
		if (length!=null && length.longValue() > 0) {
			obj.setLength(length);
		}
		Integer scale = rs.getInteger("SCALE");
		if (scale!=null && scale.intValue() > 0) {
			obj.setScale(scale);
		}
		String metaType = getString(rs, "METATYPE");
		if ("A".equals(metaType)) {
			obj.setArrayDimension(1);
			obj.setArrayDimensionUpperBound(rs.getInt("ARRAY_LENGTH"));
		}
		if ("L".equals(metaType)) {
			obj.setArrayDimension(1);
			obj.setArrayDimensionUpperBound(rs.getInt("ARRAYINDEXTYPELENGTH"));
		}
		obj.setDataTypeName(productDataType);
		obj.setRemarks(getString(rs, REMARKS));
		setSpecifics(rs, "CLASS", obj);
		return obj;
	}

}
