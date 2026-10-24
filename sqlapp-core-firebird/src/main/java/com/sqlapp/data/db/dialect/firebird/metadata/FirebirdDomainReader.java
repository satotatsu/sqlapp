/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.firebird.metadata;

import static com.sqlapp.util.CommonUtils.abs;
import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.trim;

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
 * Firebirdのドメイン作成クラス
 * 
 * @author satoh
 * 
 */
public class FirebirdDomainReader extends DomainReader {

	protected FirebirdDomainReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Domain> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("domains.sql");
		final List<Domain> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Domain domain = createDomain(rs);
				result.add(domain);
			}
		});
		readArrayBounds(connection, context, result);
		return result;
	}

	private void readArrayBounds(Connection connection, ParametersContext context, List<Domain> domains) {
		var arrays = new java.util.HashMap<String, Domain>();
		for (Domain domain : domains)
			if (domain.getArrayDimension() > 0)
				arrays.put(domain.getName(), domain);
		if (arrays.isEmpty())
			return;
		var bounds = new java.util.HashMap<String, java.util.List<String>>();
		execute(connection, getSqlNodeCache().getString("domainDimensions.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String name = trim(getString(rs, "DOMAIN_NAME"));
				Domain domain = arrays.get(name);
				if (domain == null)
					return;
				var dimensions = bounds.computeIfAbsent(name, key -> new java.util.ArrayList<>());
				if (rs.getInt("DIMENSION_NUMBER") != dimensions.size())
					throw new SQLException("Non-contiguous Firebird domain array dimensions: " + name);
				int lower = rs.getInt("LOWER_BOUND");
				int upper = rs.getInt("UPPER_BOUND");
				if (dimensions.isEmpty())
					domain.setArrayDimensionLowerBound(lower).setArrayDimensionUpperBound(upper);
				dimensions.add(lower + ":" + upper);
			}
		});
		arrays.forEach((name, domain) -> {
			var dimensions = bounds.get(name);
			if (dimensions == null || dimensions.size() != domain.getArrayDimension())
				throw new IllegalStateException("Incomplete Firebird domain array dimensions: " + name);
			domain.getSpecifics().put(com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder.ARRAY_BOUNDS,
					String.join(",", dimensions));
		});
	}

	protected Domain createDomain(ExResultSet rs) throws SQLException {
		Domain obj = new Domain(trim(getString(rs, "RDB$FIELD_NAME")));
		obj.setRemarks(getString(rs, "RDB$DESCRIPTION"));
		obj.setCharacterSet(trim(getString(rs, "CHARACTER_SET_NAME")));
		obj.setCollation(trim(getString(rs, "COLLATION_NAME")));
		if (obj.getCollation() != null)
			obj.getSpecifics().put("firebird.domain.collationId", String.valueOf(rs.getInt("RDB$COLLATION_ID")));
		obj.setArrayDimension(rs.getInt("RDB$DIMENSIONS"));
		int nullFlag = rs.getInt("RDB$NULL_FLAG");
		obj.setNullable(1 != nullFlag);
		FirebirdUtils.setDefaultConstraint(obj, trim(getString(rs, "RDB$DEFAULT_SOURCE")));
		setCheckConstraint(obj, trim(getString(rs, "RDB$VALIDATION_SOURCE")));
		int segmentLength = rs.getInt("RDB$SEGMENT_LENGTH");
		int length = rs.getInt("RDB$FIELD_LENGTH");
		int precision = rs.getInt("RDB$FIELD_PRECISION");
		int scale = abs(rs.getInt("RDB$FIELD_SCALE"));
		int type = rs.getInt("RDB$FIELD_TYPE");
		int subType = rs.getInt("RDB$FIELD_SUB_TYPE");
		FirebirdUtils.setDbType(obj, type, subType, length, precision, scale, segmentLength);
		if (type == 14 || type == 37)
			obj.setLength(rs.getInt("RDB$CHARACTER_LENGTH"));
		return obj;
	}

	/**
	 * Firebirdのチェック条件を変換して設定
	 * 
	 * @param domain
	 * @param condition
	 */
	private void setCheckConstraint(Domain domain, String condition) {
		if (isEmpty(condition)) {
			return;
		}
		String val = FirebirdUtils.convertCheckConstraint(condition);
		domain.setCheck(val);
	}

}
