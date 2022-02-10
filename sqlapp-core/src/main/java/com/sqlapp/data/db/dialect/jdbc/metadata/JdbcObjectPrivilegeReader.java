/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.jdbc.metadata;

import static com.sqlapp.util.CommonUtils.emptyToNull;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.DbUtils.close;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ObjectPrivilegeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ObjectPrivilege;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.util.CommonUtils;

/**
 * JDBCの権限読み込みクラス
 * 
 * @author satoh
 * 
 */
public class JdbcObjectPrivilegeReader extends ObjectPrivilegeReader {

	public JdbcObjectPrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ObjectPrivilege> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		ResultSet rs = null;
		try {
			DatabaseMetaData databaseMetaData = connection.getMetaData();
			rs = databaseMetaData.getTablePrivileges(CommonUtils.coalesce(emptyToNull(this.getCatalogName(context)), emptyToNull(this.getCatalogName()))
					,CommonUtils.coalesce(emptyToNull(this.getSchemaName(context)), emptyToNull(this.getSchemaName()))
					,CommonUtils.coalesce(emptyToNull(this.getObjectName(context)), emptyToNull(this.getObjectName())));
			List<ObjectPrivilege> result = list();
			while (rs.next()) {
				ObjectPrivilege columnPrivilege = createPrivilege(rs);
				result.add(columnPrivilege);
			}
			return result;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			close(rs);
		}
	}

	/**
	 * Privilegeを作成します
	 * 
	 * @param rs
	 * @throws SQLException
	 */
	protected ObjectPrivilege createPrivilege(ResultSet rs) throws SQLException {
		String catalogName = getString(rs, "TABLE_CAT");
		String schemaName = getString(rs, "TABLE_SCHEM");
		String objectName = getString(rs, TABLE_NAME);
		ObjectPrivilege obj = new ObjectPrivilege();
		obj.setCatalogName(catalogName);
		obj.setSchemaName(schemaName);
		obj.setObjectName(objectName);
		obj.setGrantorName(getString(rs, GRANTOR));
		obj.setGranteeName(getString(rs, GRANTEE));
		obj.setPrivilege(getString(rs, "PRIVILEGE"));
		obj.setGrantable("YES".equals(getString(rs, "IS_GRANTABLE")));
		return obj;
	}
}
