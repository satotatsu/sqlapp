/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.generator;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.sqlapp.util.CommonUtils;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * クエリー生成設定
 */
@Getter
@Setter
@EqualsAndHashCode
public class QueryDefinitionDataGeneratorSetting {
	/** シート列名 */
	private String colString;
	/** 生成タイプ */
	private String generationGroup;
	/** SELECT SQL */
	private String selectSql;

	private List<Map<String, Object>> values = CommonUtils.list();

	/**
	 * DBからデータを読み込みます
	 * 
	 * @param conn DBコネクション
	 * @throws SQLException
	 */
	public void loadData(final Connection conn) throws SQLException {
		try (final Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
			try (final ResultSet rs = stmt.executeQuery(selectSql)) {
				final Map<Integer, String> indexNamelMap = CommonUtils.map();
				final ResultSetMetaData resultSetMetaData = rs.getMetaData();
				int colCount = resultSetMetaData.getColumnCount();
				for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
					final String label = resultSetMetaData.getColumnLabel(i);
					indexNamelMap.put((i - 1), label.intern());
				}
				while (rs.next()) {
					final Map<String, Object> map = CommonUtils.map();
					for (int i = 0; i < colCount; i++) {
						String name = indexNamelMap.get(i);
						Object value = rs.getObject(i + 1);
						map.put(name, value);
					}
					values.add(map);
				}
			}
		}
	}

	/**
	 * 値をインデックスを指定して取得します。
	 * 
	 * @param i
	 * @return
	 */
	public Map<String, Object> getValueMap(int i) {
		if (values.isEmpty()) {
			return Collections.emptyMap();
		}
		int size = values.size();
		int pos = i % size;
		return values.get(pos);
	}
}
