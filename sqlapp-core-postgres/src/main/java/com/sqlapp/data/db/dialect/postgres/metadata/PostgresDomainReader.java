/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-postgres.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.postgres.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.ltrim;
import static com.sqlapp.util.CommonUtils.notEmpty;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.DomainReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Deferrability;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;

/**
 * Postgresのドメイン作成クラス
 * 
 * @author satoh
 * 
 */
public class PostgresDomainReader extends DomainReader {

	protected PostgresDomainReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Domain> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
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

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("domains.sql");
	}

	protected Domain createDomain(ExResultSet rs) throws SQLException {
		String productDataType = getString(rs, "typname");
		int arrayDimension = rs.getInt("typndims");
		String intervalTypeName = getString(rs, "interval_type_name");
		if (arrayDimension > 0) {
			productDataType = ltrim(productDataType, '_');
		}
		productDataType = notEmpty(intervalTypeName, productDataType);
		Long maxLength = getLong(rs, "max_length");
		Long numericPrecision = getLong(rs, "numeric_precision");
		Integer numericScale = getInt(rs, "numeric_scale");
		Integer datetimeScale = getInt(rs, "datetime_scale");
		Integer intervalScale = getInt(rs, "interval_scale");
		Domain obj = new Domain(getString(rs, "domain_name"));
		obj.setNullable(!rs.getBoolean("typnotnull"));
		getDialect().setDbType(productDataType, CommonUtils.notZero(maxLength, numericPrecision)
				, CommonUtils.notZero(numericScale, datetimeScale, intervalScale), obj);
		obj.setId(getString(rs, "oid"));
		// obj.setCatalogName(getString(rs, "domain_catalog"));
		obj.setSchemaName(getString(rs, "domain_schema"));
		obj.setRemarks(getString(rs, "remarks"));
		obj.setArrayDimension(arrayDimension);
		obj.setDefaultValue(getString(rs, "typdefault"));
		obj.setCheck(getString(rs, "consrc")); // ドメイン制約式
		boolean is_deferrable = rs.getBoolean("is_deferrable");
		boolean initially_deferred = rs.getBoolean("initially_deferred");
		obj.setDeferrability(Deferrability.getDeferrability(is_deferrable,
				initially_deferred));
		return obj;
	}
}
