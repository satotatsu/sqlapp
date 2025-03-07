/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.notZero;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.DomainReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * SQLServer2005のドメイン読み込みクラス
 * 
 * @author satoh
 * 
 */
public class SqlServer2005DomainReader extends DomainReader {

	protected SqlServer2005DomainReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Domain> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final List<Domain> result = list();
		SqlNode node = getSqlSqlNode(productVersionInfo);
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Domain obj = createDomain(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("domains2005.sql");
	}

	protected Domain createDomain(ExResultSet rs) throws SQLException {
		String name = getString(rs, DOMAIN_NAME);
		Domain obj = new Domain(name);
		String productDataType = getString(rs, "base_type_name");
		Long byteLength = getLong(rs, "max_length");
		Long max_length = SqlServerUtils.getMaxLength(productDataType,
				byteLength);
		Long prec = this.getLong(rs, "precision");
		Integer scale = getInteger(rs, "scale");
		obj.setNullable(rs.getBoolean("is_nullable"));
		getDialect().setDbType(productDataType, notZero(max_length, prec),
				scale, obj);
		obj.setCatalogName(getString(rs, CATALOG_NAME));
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		obj.setCollation(getString(rs, COLLATION_NAME));
		setSpecifics(rs, "rule_name", obj);
		setSpecifics(rs, "assembly_name", obj);
		setSpecifics(rs, "assembly_class", obj);
		// TODO バイナリをどうするか?
		// setDbSpecificInfo(rs, "content", obj);
		return obj;
	}

}
