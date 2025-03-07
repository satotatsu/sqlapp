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
		return result;
	}

	protected Domain createDomain(ExResultSet rs) throws SQLException {
		Domain obj = new Domain(getString(rs, "RDB$FIELD_NAME"));
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
