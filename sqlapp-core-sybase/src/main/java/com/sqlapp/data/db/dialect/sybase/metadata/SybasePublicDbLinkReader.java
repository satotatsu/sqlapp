/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sybase.
 *
 * sqlapp-core-sybase is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sybase is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sybase.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sybase.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.PublicDbLinkReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.PublicDbLink;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
/**
 * SQLServerのDBリンク作成クラス
 * 
 * @author satoh
 * 
 */
public class SybasePublicDbLinkReader extends PublicDbLinkReader {

	protected SybasePublicDbLinkReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<PublicDbLink> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<PublicDbLink> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				PublicDbLink obj = createDbLink(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(final ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("publicDbLinks.sql");
	}

	protected PublicDbLink createDbLink(ExResultSet rs) throws SQLException {
		String name = getString(rs, "name");
		String connection_catalog = getString(rs, "catalog");
		String userName = getString(rs, "user_name");
		String data_source = getString(rs, "data_source");
		Timestamp modifyDate = rs.getTimestamp("modify_date");
		PublicDbLink obj = new PublicDbLink(name);
		obj.setUserId(userName);
		obj.setDataSource(data_source);
		obj.setConnectionCatalog(connection_catalog);
		obj.setLastAlteredAt(modifyDate);
		setSpecifics(rs, "provider_string", obj);
		return obj;
	}
}
