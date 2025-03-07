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

import static com.sqlapp.data.db.metadata.MetadataReader.ROUTINE_NAME;
import static com.sqlapp.data.db.metadata.MetadataReader.SCHEMA_NAME;
import static com.sqlapp.data.db.metadata.MetadataReader.SPECIFIC_NAME;
import static com.sqlapp.util.CommonUtils.rtrim;
import static com.sqlapp.util.CommonUtils.trim;
import static com.sqlapp.util.CommonUtils.upperMap;

import java.sql.SQLException;
import java.util.Map;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ReaderOptions;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Routine;
import com.sqlapp.jdbc.ExResultSet;

public class Db2Utils {

	private static final Map<String, IndexType> INDEX_TYPE_MAP = upperMap();

	static {
		INDEX_TYPE_MAP.put("REG", IndexType.BTree);
		INDEX_TYPE_MAP.put("CLUS", IndexType.Clustered);
	}

	public static IndexType getIndexType(String indexType) {
		return INDEX_TYPE_MAP.get(trim(indexType));
	}

	public static void setRutine(Dialect dialect, ExResultSet rs,
			ReaderOptions readerOption, Routine<?> obj) throws SQLException {
		obj.setName(getString(rs, ROUTINE_NAME));
		obj.setDialect(dialect);
		obj.setSpecificName(getString(rs, SPECIFIC_NAME));
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		obj.setLanguage(getString(rs, "LANGUAGE"));
		String definition = getString(rs, "ROUTINE_DEFINITION");
		if (readerOption.isReadDefinition()) {
			obj.setDefinition(definition);
		}
		if (readerOption.isReadStatement()) {
			int offset = rs.getInt("TEXT_BODY_OFFSET");
			if (offset > 0 && definition != null) {
				obj.setStatement(definition.substring(offset));
			}
		}
		obj.setCreatedAt(rs.getTimestamp("CREATE_TIME"));
		obj.setLastAlteredAt(rs.getTimestamp("ALTER_TIME"));
		obj.setClassName(getString(rs, "CLASS"));
		obj.setValid("Y".equalsIgnoreCase(getString(rs, "VALID")));
		obj.setRemarks(getString(rs, "REMARKS"));
	}

	protected static String getString(ExResultSet rs, String name)
			throws SQLException {
		return rtrim(rs.getString(name));
	}

}
