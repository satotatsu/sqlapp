/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-saphana.
 *
 * sqlapp-core-saphana is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-saphana is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-saphana.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.saphana.metadata;

import static com.sqlapp.util.CommonUtils.tripleKeyMap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.TripleKeyMap;

/**
 * SAP HAANA Full Text Index Reader
 * 
 * @author satoh
 * 
 */
public class SapHanaFulltextIndexReader extends IndexReader {

	public SapHanaFulltextIndexReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Index> doGetAll(final Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final TripleKeyMap<String, String, String, Index> map = tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String name = getString(rs, INDEX_NAME);
				String schemaName = getString(rs, SCHEMA_NAME);
				String tableName = getString(rs, TABLE_NAME);
				Index index = map.get(schemaName, tableName, name);
				if (index == null) {
					index = createIndex(connection, rs);
					map.put(index.getSchemaName(), index.getTableName(),
							index.getName(), index);
				}
				index.getColumns().add(
						new Column(getString(rs, "INTERNAL_COLUMN_NAME")));
			}
		});
		return map.toList();
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("fulltextindexes.sql");
	}

	protected Index createIndex(final Connection connection, ExResultSet rs)
			throws SQLException {
		Index obj = new Index(getString(rs, INDEX_NAME));
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		obj.setTableName(getString(rs, TABLE_NAME));
		setDbSpecificInfo(rs, obj);
		return obj;
	}

	protected void setDbSpecificInfo(ExResultSet rs, Index obj)
			throws SQLException {
		setSpecifics(rs, "LANGUAGE_COLUMN", obj);
		setSpecifics(rs, "MIME_TYPE_COLUMN", obj);
		setSpecifics(rs, "LANGUAGE_DETECTION", obj);
		setSpecifics(rs, "FAST_PREPROCESS", obj);
		setSpecifics(rs, "FUZZY_SEARCH_INDEX", obj);
		setSpecifics(rs, "SEARCH_ONLY", obj);
		setSpecifics(rs, "IS_EXPLICIT", obj);
		setSpecifics(rs, "FLUSH_AFTER_DOCUMENTS", obj);
		setSpecifics(rs, "FLUSH_EVERY_MINUTES", obj);
		setSpecifics(rs, "CONFIGURATION", obj);
		// setDbSpecificInfo(rs, "INTERNAL_COLUMN_NAME", obj);
		setSpecifics(rs, "PHRASE_INDEX_RATIO", obj);
	}

}
