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

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.rtrim;
import static com.sqlapp.util.CommonUtils.trim;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.DomainReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.DoubleKeyMap;

/**
 * Oracleのドメイン作成クラス
 * 
 * @author satoh
 * 
 */
public class OracleDomainReader extends DomainReader {

	protected OracleDomainReader(Dialect dialect) {
		super(dialect);
	}

	private static final Pattern DOMAIN_PATTERN = Pattern.compile(
			".*TYPE\\s+.*\\s+(IS|AS)\\s+VARRAY\\s*\\((?<arrayMax>.*)\\)\\s+OF\\s+(?<type>.*)",
			Pattern.CASE_INSENSITIVE + Pattern.MULTILINE);

	private static final Pattern DOMAIN_PATTERN2 = Pattern.compile(
			".*TYPE\\s+.*\\s+(IS|AS)\\s+TABLE\\s+OF\\s+(?<type>.*)",
			Pattern.CASE_INSENSITIVE + Pattern.MULTILINE);

	private static final Pattern NOT_NULL = Pattern.compile(
			"(?<type>.*)\\s+NOT\\s+NULL\\s*",
			Pattern.CASE_INSENSITIVE + Pattern.MULTILINE);

	
	private static final String OBJECT_TYPE = "TYPE";

	@Override
	protected List<Domain> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Domain> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Domain obj = createDomain(rs, connection, context,
						productVersionInfo);
				result.add(obj);
			}
		});
		ParametersContext cnt = new ParametersContext();
		DoubleKeyMap<String, String, List<String>> routines=OracleMetadataUtils.getRoutineSources(connection, this.getDialect(), cnt, result, OBJECT_TYPE);
		for(Domain obj:result){
			List<String> text = routines.get(obj.getSchemaName(), obj.getName());
			StringBuilder builder = new StringBuilder();
			for (String line : text) {
				builder.append(line);
				builder.append(" ");
			}
			String def=builder.toString();
			Matcher matcher = DOMAIN_PATTERN.matcher(def);
			if (matcher.matches()) {
				String arrayMax = matcher.group("arrayMax");
				obj.setArrayDimension(1);
				obj.setArrayDimensionUpperBound(Integer.parseInt(trim(arrayMax)));
				String productDataType = trim(rtrim(trim(matcher.group("type")), ';'));
				Matcher notNullMatcher=NOT_NULL.matcher(productDataType);
				if (notNullMatcher.matches()){
					productDataType=trim(notNullMatcher.group("type"));
					obj.setNotNull(true);
				}
				getDialect().setDbType(productDataType, null,null,
						obj);
			} else{
				matcher =DOMAIN_PATTERN2.matcher(def);
				if (matcher.matches()) {
					String productDataType = trim(rtrim(trim(matcher.group("type")), ';'));
					Matcher notNullMatcher=NOT_NULL.matcher(productDataType);
					if (notNullMatcher.matches()){
						productDataType=trim(notNullMatcher.group("type"));
						obj.setNotNull(true);
					}
					getDialect().setDbType(productDataType, null,null,
							obj);
				} else{
					logger.warn("Parse Error.Domain="+obj+", contents="+def);
				}
			}
		}
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("domains.sql");
	}

	protected Domain createDomain(ExResultSet rs, final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) throws SQLException {
		String name = getString(rs, "TYPE_NAME");
		Domain obj = new Domain(name);
		obj.setSchemaName(getString(rs, "OWNER"));
		obj.setCreatedAt(rs.getTimestamp("CREATED"));
		obj.setLastAlteredAt(rs.getTimestamp("LAST_DDL_TIME"));
		if (!"VALID".equalsIgnoreCase(getString(rs, "STATUS"))) {
			obj.setValid(false);
		}
		return obj;
	}

}
