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
import com.sqlapp.data.db.metadata.SettingReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Setting;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * OracleのSettingReader
 * 
 * @author satoh
 * 
 */
public class OracleSettingReader extends SettingReader {

	protected OracleSettingReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Setting> doGetAll(final Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final List<Setting> result = list();
		final boolean dba = OracleMetadataUtils.hasSelectPrivilege(connection,
				this.getDialect(), "SYS", "V$SYSTEM_PARAMETER");
		if (dba){
			SqlNode node = getSqlSqlNode(productVersionInfo);
			execute(connection, node, context, new ResultSetNextHandler() {
				@Override
				public void handleResultSetNext(ExResultSet rs) throws SQLException {
					Setting obj = createSetting(rs);
					result.add(obj);
				}
			});
		}
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("settings.sql");
	}

	protected Setting createSetting(ExResultSet rs) throws SQLException {
		Setting obj = new Setting(getString(rs, "NAME"));
		obj.setValue(getString(rs, "VALUE"));
		obj.setDisplayValue(getString(rs, "DISPLAY_VALUE"));
		obj.setDefault("TRUE".equalsIgnoreCase(getString(rs, "ISDEFAULT")));
		obj.setRemarks(getString(rs, "DESCRIPTION"));
		this.setSpecifics(rs, "ISSES_MODIFIABLE", obj);
		this.setSpecifics(rs, "ISSYS_MODIFIABLE", obj);
		this.setSpecifics(rs, "ISINSTANCE_MODIFIABLE", obj);
		this.setSpecifics(rs, "ISMODIFIED", obj);
		this.setSpecifics(rs, "ISDEPRECATED", obj);
		this.setSpecifics(rs, "ISBASIC", obj);
		this.setSpecifics(rs, "UPDATE_COMMENT", obj);
		this.setSpecifics(rs, "HASH", obj);
		this.setStatistics(rs, "ISADJUSTED", obj);
		return obj;
	}
}
