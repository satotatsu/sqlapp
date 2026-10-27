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
		INDEX_TYPE_MAP.put("VANN", IndexType.Vector);
	}

	public static IndexType getIndexType(String indexType) {
		return INDEX_TYPE_MAP.get(trim(indexType));
	}

	public static void setRutine(Dialect dialect, ExResultSet rs, ReaderOptions readerOption, Routine<?> obj)
			throws SQLException {
		obj.setName(getString(rs, ROUTINE_NAME));
		obj.setDialect(dialect);
		obj.setSpecificNameLiteral(getString(rs, SPECIFIC_NAME));
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		obj.setLanguage(getString(rs, "LANGUAGE"));
		String definition = getString(rs, "ROUTINE_DEFINITION");
		String replayDefinition = dialect instanceof com.sqlapp.data.db.dialect.db2.Db2_1110
				&& "SQL".equals(obj.getLanguage())
						? com.sqlapp.data.db.dialect.db2.util.Db2RoutineDefinition.preserveSpecificName(definition,
								obj.getSpecificName())
						: definition;
		if (readerOption.isReadDefinition()) {
			obj.setDefinition(replayDefinition);
		}
		if (readerOption.isReadStatement()) {
			int offset = rs.getInt("TEXT_BODY_OFFSET");
			if (dialect instanceof com.sqlapp.data.db.dialect.db2.Db2_1110 && definition != null
					&& (offset <= 0 || offset >= definition.length()
							|| "PL/SQL".equalsIgnoreCase(getString(rs, "DIALECT"))
							|| "Y".equals(getString(rs, "AUTONOMOUS")))) {
				// The requested body cannot safely recreate these native routine forms.
				obj.setDefinition(replayDefinition);
			} else if (offset > 0 && definition != null) {
				obj.setStatement(definition.substring(offset));
				if (dialect instanceof com.sqlapp.data.db.dialect.db2.Db2_1110 && "SQL".equals(obj.getLanguage()))
					obj.getSpecifics().put("DB2_ROUTINE_DDL_BASE64", java.util.Base64.getEncoder()
							.encodeToString(replayDefinition.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
			}
		}
		obj.setCreatedAt(rs.getTimestamp("CREATE_TIME"));
		obj.setLastAlteredAt(rs.getTimestamp("ALTER_TIME"));
		obj.setClassName(getString(rs, "CLASS"));
		obj.setValid("Y".equalsIgnoreCase(getString(rs, "VALID")));
		obj.setRemarks(getString(rs, "REMARKS"));
		if (dialect instanceof com.sqlapp.data.db.dialect.db2.Db2_1110) {
			for (String key : java.util.List.of("QUALIFIER", "FUNC_PATH", "RESULT_SETS", "SQL_DATA_ACCESS",
					"COMMIT_ON_RETURN", "AUTONOMOUS", "DETERMINISTIC")) {
				String value = getString(rs, key);
				if (value != null && !value.isBlank())
					obj.getSpecifics().put(key, value);
			}
		}
	}

	protected static String getString(ExResultSet rs, String name) throws SQLException {
		return rtrim(rs.getString(name));
	}

}
