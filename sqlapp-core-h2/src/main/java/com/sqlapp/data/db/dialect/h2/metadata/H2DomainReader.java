/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-h2.
 *
 * sqlapp-core-h2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-h2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-h2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.h2.metadata;

import static com.sqlapp.util.CommonUtils.list;

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
 * H2のドメイン作成クラス
 * 
 * @author satoh
 * 
 */
public class H2DomainReader extends DomainReader {

	protected H2DomainReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Domain> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		boolean modern = productVersionInfo != null && productVersionInfo.getMajorVersion() != null
				&& productVersionInfo.getMajorVersion() >= 2;
		final List<Domain> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String domain_catalog = getString(rs, "DOMAIN_CATALOG");
				String domain_schema = getString(rs, "DOMAIN_SCHEMA");
				String domain_Name = getString(rs, DOMAIN_NAME);
				Domain domain = new Domain(domain_Name);
				domain.setCatalogName(domain_catalog);
				domain.setSchemaName(domain_schema);
				String isNullable = getString(rs, "IS_NULLABLE");
				domain.setNullable("YES".equalsIgnoreCase(isNullable));
				domain.setDefaultValue(getString(rs, "COLUMN_DEFAULT"));
				domain.setOnUpdate(getString(rs, "DOMAIN_ON_UPDATE"));
				String productDataType = getString(rs, "TYPE_NAME");
				domain.setLength(getLong(rs, "PRECISION"));
				domain.setScale(rs.getInt("SCALE"));
				domain.setDataTypeName(productDataType);
				if (modern) {
					getDialect().setDbType(productDataType, domain.getLength(), domain.getScale(), domain);
					String type = getString(rs, "TYPE_SQL");
					if (type != null && (type.contains(" ARRAY") || type.startsWith("ROW(")
							|| type.startsWith("INTERVAL ") || type.startsWith("GEOMETRY("))) {
						domain.setDataTypeName(type);
						domain.setDataType(com.sqlapp.data.db.datatype.DataType.OTHER);
					} else if (type != null && type.startsWith("ENUM(")) {
						domain.setDataTypeName(type);
						var values = domain.getValues().stream().map(value -> value.replace("''", "'")).toList();
						domain.getValues().clear();
						domain.getValues().addAll(values);
					} else if ("JSON".equals(type)) {
						domain.setDataType(com.sqlapp.data.db.datatype.DataType.JSON);
						domain.setLength((Long) null);
					}
					String parent = getString(rs, "PARENT_DOMAIN_NAME");
					if (parent != null) {
						domain.getSpecifics().put("h2.parentDomainName", parent);
						domain.getSpecifics().put("h2.parentDomainSchema", getString(rs, "PARENT_DOMAIN_SCHEMA"));
					}
				}
				String checkConstraint = getString(rs, "CHECK_CONSTRAINT");
				domain.setCheck(checkConstraint);
				String sqlText = getString(rs, "SQL");
				domain.setDefinition(sqlText);
				domain.setRemarks(getString(rs, REMARKS));
				result.add(domain);
			}
		});
		if (modern && !result.isEmpty()) {
			var domains = new java.util.HashMap<java.util.List<String>, Domain>();
			for (var domain : result)
				domains.put(java.util.Arrays.asList(domain.getCatalogName(), domain.getSchemaName(), domain.getName()),
						domain);
			execute(connection, getSqlNodeCache().getString("domainChecks_200.sql"), context,
					new ResultSetNextHandler() {
						@Override
						public void handleResultSetNext(ExResultSet rows) throws SQLException {
							var domain = domains.get(java.util.Arrays.asList(getString(rows, "DOMAIN_CATALOG"),
									getString(rows, "DOMAIN_SCHEMA"), getString(rows, "DOMAIN_NAME")));
							if (domain != null)
								domain.getChecks().add(new com.sqlapp.data.schemas.DomainCheckConstraint(
										getString(rows, "CONSTRAINT_NAME"), getString(rows, "CHECK_CLAUSE")));
						}
					});
			for (var domain : result)
				if (!domain.getChecks().isEmpty())
					domain.setCheck(null);
		}
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		if (productVersionInfo != null && productVersionInfo.getMajorVersion() != null
				&& productVersionInfo.getMajorVersion() >= 2) {
			return getSqlNodeCache().getString("domains_200.sql");
		}
		return getSqlNodeCache().getString("domains.sql");
	}

}
