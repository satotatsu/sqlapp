/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-derby.
 *
 * sqlapp-core-derby is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-derby is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-derby.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.derby.metadata;

import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.trim;
import static com.sqlapp.util.CommonUtils.upperMap;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ReferenceColumn;

public class DerbyUtils {

	private DerbyUtils() {
	}

	/**
	 * 指定したテーブルの指定した位置のカラムを取得します
	 * 
	 * @param connection
	 * @param dialect
	 * @param schemaName
	 * @param tableName
	 * @param columnIndexes
	 */
	protected static List<ReferenceColumn> getKeyColumns(Connection connection,
			Dialect dialect, String schemaName, String tableName,
			String[] colIds) {
		ColumnReader reader = dialect.getCatalogReader().getSchemaReader()
				.getTableReader().getColumnReader();
		reader.setSchemaName(schemaName);
		reader.setObjectName(tableName);
		List<Column> columns = reader.getAllFull(connection);
		if (isEmpty(columns)) {
			return list();
		}
		List<ReferenceColumn> result = list(colIds.length);
		for (int i = 0; i < colIds.length; i++) {
			String col = colIds[i];
			Integer pos = null;
			ReferenceColumn ref = null;
			if (col.contains("DESC")) {
				pos = Integer.valueOf(col.replaceAll("[\\s]*DESC", ""));
				ref = new ReferenceColumn(columns.get(pos - 1), Order.Desc);
			} else {
				pos = Integer.valueOf(col);
				ref = new ReferenceColumn(columns.get(pos - 1));
			}
			result.add(ref);
		}
		return result;
	}

	private static final Pattern INDEX_INFO_PATTERN = Pattern
			.compile(
					"(UNIQUE|UNIQUE WITH DUPLICATE NULLS){0,1}[\\s]*([^\\s]+){0,1}[\\s]*\\((.*)\\)",
					Pattern.CASE_INSENSITIVE);

	/**
	 * Derbyのインデックス定義を解析してインデックスを返します
	 * 
	 * @param connection
	 * @param dialect
	 * @param schemaName
	 * @param tableName
	 * @param indexName
	 * @param definition
	 */
	protected static Index parseIndexDescriptor(Connection connection,
			Dialect dialect, String schemaName, String tableName,
			String indexName, String definition) {
		Index index = new Index(indexName);
		Matcher matcher = INDEX_INFO_PATTERN.matcher(definition);
		if (matcher.matches()) {
			String val = matcher.group(1);
			if ("UNIQUE".equalsIgnoreCase(val)) {
				index.setUnique(true);
			}
			val = matcher.group(2);
			index.setIndexType(IndexType.parse(val));
			val = matcher.group(3);
			String[] colIds = trim(val).split("[\\s]*,[\\s]*");
			List<ReferenceColumn> columns = getKeyColumns(connection, dialect,
					index.getSchemaName(), index.getTableName(), colIds);
			index.getColumns().addAll(columns);
		}
		return index;
	}

	protected static CascadeRule getCascadeRule(String val) {
		if ("R".equalsIgnoreCase(val)) {
			return CascadeRule.None;
		} else if ("S".equalsIgnoreCase(val)) {
			return CascadeRule.Restrict;
		} else if ("C".equalsIgnoreCase(val)) {
			return CascadeRule.Cascade;
		} else if ("U".equalsIgnoreCase(val)) {
			return CascadeRule.SetNull;
		}
		return CascadeRule.None;
	}

	private static final Map<String, String> PRIV_CACHE = upperMap();
	static {
		PRIV_CACHE.put("S", "SELECT");
		PRIV_CACHE.put("I", "INSERT");
		PRIV_CACHE.put("U", "UPDATE");
		PRIV_CACHE.put("X", "EXECUTE");
		PRIV_CACHE.put("D", "DELETE");
		PRIV_CACHE.put("R", "REFERENCES");
		PRIV_CACHE.put("A", "ALL");
	}

	protected static String getPrivilege(String priv) {
		String privilege = PRIV_CACHE.get(priv);
		if (privilege != null) {
			return privilege;
		}
		return priv;
	}
}
