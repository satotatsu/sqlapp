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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SynonymReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Synonym;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

public class OracleSynonymReader extends SynonymReader {

	protected OracleSynonymReader(Dialect dialect) {
		super(dialect);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.dialect.metadata.AbstractDBMetadataFactory#getMetadata
	 * (java.sql.Connection, com.sqlapp.data.parameter.ParametersContext)
	 */
	@Override
	protected List<Synonym> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final boolean dba = OracleMetadataUtils.hasSelectPrivilege(connection,
				this.getDialect(), "SYS", "DBA_SYNONYMS");
		SqlNode node = getSqlSqlNode(productVersionInfo);
		OracleMetadataUtils.setDba(dba, context);
		final List<Synonym> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Synonym synonym = createSynonym(rs);
				result.add(synonym);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("synonyms.sql");
	}

	protected Synonym createSynonym(ExResultSet rs) throws SQLException {
		String name = getString(rs, SYNONYM_NAME);
		String tableOwner = getString(rs, "TABLE_OWNER");
		String tableName = getString(rs, TABLE_NAME);
		String dbLink = getString(rs, "DB_LINK");
		Synonym synonym = new Synonym(name);
		synonym.setObjectSchemaName(tableOwner);
		synonym.setObjectName(tableName);
		synonym.setDbLinkName(dbLink);
		OracleMetadataUtils.setCommonInfo(rs, synonym);
		return synonym;
	}
}
