/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-saphana.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.saphana.metadata;

import static com.sqlapp.data.db.metadata.IndexReader.INDEX_TYPE;
import static com.sqlapp.data.db.metadata.MetadataReader.INDEX_NAME;
import static com.sqlapp.data.db.metadata.MetadataReader.SCHEMA_NAME;
import static com.sqlapp.data.db.metadata.MetadataReader.TABLE_NAME;
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SqlNodeCache;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.AbstractDbObject;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

public class SapHanaUtils {

	protected static Index createIndex(Dialect dialect, final Connection connection, ResultSet rs) throws SQLException{
        Index index=new Index(getString(rs, INDEX_NAME));
        index.setSchemaName(getString(rs, SCHEMA_NAME));
        index.setTableName(getString(rs, TABLE_NAME));
        index.setId(""+rs.getLong("INDEX_OID"));
        String type=getString(rs, INDEX_TYPE);
        boolean unique=type.contains("UNIQUE");
        index.setUnique(unique);
        type=type.replace(" UNIQUE", "");
        index.setIndexType(IndexType.parse(type));
        index.setCompression(type.startsWith("CP"));
        index.setUnique(type.contains("UNIQUE"));
		setSpecifics(dialect, rs, "BTREE_FILL_FACTOR", index);
		setSpecifics(dialect, rs, "BTREE_SPLIT_TYPE", index);
		setSpecifics(dialect, rs, "BTREE_SPLIT_POSITION", index);
        return index;
	}

	protected static String getString(ResultSet rs, String columnLabel) throws SQLException{
		return rs.getNString(columnLabel);
	}
	
	protected static JdbcQueryHandler execute(final Connection connection, SqlNode node
			, final ParametersContext context, ResultSetNextHandler handler) {
		JdbcQueryHandler jdbcQueryHandler=new JdbcQueryHandler(node, handler);
		return jdbcQueryHandler.execute(connection, context);
	}

	
	protected static SqlNodeCache getSqlNodeCache(){
		return SqlNodeCache.getInstance(SapHanaUtils.class);
	}

	/**
	 * DB固有情報を設定します
	 * @param rs
	 * @param columnName
	 * @param obj
	 * @throws SQLException
	 */
	protected static void setSpecifics(Dialect dialect, ResultSet rs, String columnName
			, AbstractDbObject<?> obj) throws SQLException{
		Object val=rs.getObject(columnName);
		if (!isEmpty(val)){
			String text=Converters.getDefault().convertString(val, val.getClass());
			obj.getSpecifics().put(columnName, text);
		}
	}
}
