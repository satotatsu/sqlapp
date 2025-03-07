/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.hsql.metadata;

import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.DomainReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Deferrability;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * HSQLのドメイン読み込みクラス
 * 
 * @author satoh
 * 
 */
public class HsqlDomainReader extends DomainReader {

	protected HsqlDomainReader(Dialect dialect) {
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

	private final static Pattern TYPE_PATTERN = Pattern.compile("[^(]+\\(.*",
			Pattern.CASE_INSENSITIVE);

	protected Domain createDomain(ExResultSet rs) throws SQLException {
		String domain_Name = getString(rs, DOMAIN_NAME);
		Domain domain = new Domain(domain_Name);
		domain.setCatalogName(getString(rs, "domain_catalog"));
		domain.setSchemaName(getString(rs, "domain_schema"));
		domain.setDefaultValue(getString(rs, "domain_default"));
		String productDataType = getString(rs, "data_type");
		String dtdIdentifier = getString(rs, "dtd_identifier");
		Long length = this.getLong(rs, "length");
		Integer scale =this.getInteger(rs, "numeric_scale");
		// dtd_identifierの方が適切な型を返す
		if(dtdIdentifier!=null){
			productDataType = dtdIdentifier;
		}
		domain.setDataTypeName(productDataType);
		// Column column=getDialect().getDbDataTypes().parse(productDataType);
		// domain.setDbType(column.getDbType());
		getDialect().setDbType(productDataType, length,scale,
				domain);
		String is_deferrable = getString(rs, "is_deferrable");
		if (!isEmpty(is_deferrable)) {
			String initially_deferred = getString(rs, "initially_deferred");
			domain.setDeferrability(Deferrability.getDeferrability(
					"YES".equalsIgnoreCase(is_deferrable),
					"YES".equalsIgnoreCase(initially_deferred)));
		}
		// TODO チェック制約が取れない？
		// String checkConstraint=getString(rs, "CHECK_CONSTRAINT");
		// domain.setCheckConstraint(checkConstraint);
		// domain.setRemarks(getString(rs, "REMARKS"));
		return domain;
	}

}
