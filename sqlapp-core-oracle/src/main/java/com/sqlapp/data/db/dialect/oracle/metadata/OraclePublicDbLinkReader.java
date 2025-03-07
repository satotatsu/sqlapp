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

import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.StringUtils.getGroupString;

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
import com.sqlapp.util.SqlExecuter;

public class OraclePublicDbLinkReader extends PublicDbLinkReader {

	protected OraclePublicDbLinkReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<PublicDbLink> doGetAll(final Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode();
		final List<PublicDbLink> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				PublicDbLink dbLink = createDbLink(rs);
				result.add(dbLink);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode() {
		return getSqlNodeCache().getString("publicDbLinks.sql");
	}

	protected PublicDbLink createDbLink(ExResultSet rs) throws SQLException {
		String name = getString(rs, "DB_LINK");
		Timestamp created = rs.getTimestamp("CREATED");
		PublicDbLink dbLink = new PublicDbLink(name);
		dbLink.setUserId(getString(rs, "USERNAME"));
		dbLink.setDataSource(getString(rs, "HOST"));
		dbLink.setCreatedAt(created);
		return dbLink;
	}

	/**
	 * DBリンクのパスワード設定
	 * 
	 * @param connection
	 * @param dbLink
	 * @throws SQLException
	 */
	@Override
	protected void setMetadataDetail(Connection connection, PublicDbLink dbLink)
			throws SQLException {
		if (!isEmpty(dbLink.getUserId())) {
			return;
		}
		SqlExecuter sql = new SqlExecuter("SELECT ");
		sql.addSql("DBMS_METADATA.GET_DDL(");
		sql.addSqlLine("FROM ALL_DB_LINKS");
		sql.addSqlLine("WHERE 0=0");
		String ddl = OracleMetadataUtils.getDdl(connection, "DB_LINK",
				"PUBLIC", dbLink.getName());
		String val = getGroupString(OracleDbLinkReader.DB_LINK_PATTERN1, ddl, 1);
		if (val != null) {
			dbLink.setPasswordEncrypted(true);
			dbLink.setPassword(val);
		} else {
			val = getGroupString(OracleDbLinkReader.DB_LINK_PATTERN2, ddl, 1);
			dbLink.setPassword(val);
		}
	}

}
