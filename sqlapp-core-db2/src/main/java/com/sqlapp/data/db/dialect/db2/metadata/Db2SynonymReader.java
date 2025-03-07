/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SynonymReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Synonym;

/**
 * DB2のシノニム読み込み
 * 
 * @author satoh
 * 
 */
public class Db2SynonymReader extends SynonymReader {

	protected Db2SynonymReader(Dialect dialect) {
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
		SqlNode node = getSqlNode(productVersionInfo);
		final List<Synonym> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Synonym obj = createSynonym(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("synonyms.sql");
	}

	protected Synonym createSynonym(ExResultSet rs) throws SQLException {
		Synonym obj = new Synonym(getString(rs, OBJECT_NAME));
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		obj.setObjectSchemaName(getString(rs, "base_schema"));
		obj.setObjectName(getString(rs, "base_object"));
		obj.setCreatedAt(rs.getTimestamp("CREATE_TIME"));
		obj.setRemarks(getString(rs, REMARKS));
		return obj;
	}
}
