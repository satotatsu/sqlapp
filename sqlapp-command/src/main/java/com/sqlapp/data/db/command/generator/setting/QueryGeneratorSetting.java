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

package com.sqlapp.data.db.command.generator.setting;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sqlapp.data.db.command.generator.setting.strategy.AbstractValueSelectionFunction;
import com.sqlapp.data.db.command.generator.setting.strategy.ValueSelectStrategy;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.util.CommonUtils;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * クエリー生成設定
 */
@Getter
@Setter
@EqualsAndHashCode(exclude = { "values" })
public class QueryGeneratorSetting {
	/** 生成タイプ */
	@JsonProperty(index = 0)
	private String generationGroup;
	/** SELECT SQL */
	@JsonProperty(index = 1)
	private String selectSql;
	@JsonProperty(index = 2)
	private int limit = Integer.MAX_VALUE;
	@JsonProperty(index = 3)
	private int offset = 0;
	/** ValueSelectStrateg */
	@JsonProperty(index = 4)
	private ValueSelectStrategy selectionStrategy;
	@JsonIgnore
	private final List<Map<String, Object>> values = CommonUtils.list();
	@JsonIgnore
	private Column[] relationColumns;
	@JsonIgnore
	private AbstractValueSelectionFunction valueSelectionFunction;

	public QueryGeneratorSetting() {
		selectionStrategy = ValueSelectStrategy.NEXT_VALUE;
	}

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
				int cnt = 0;
				while (rs.next()) {
					if (cnt < offset) {
						cnt++;
						continue;
					} else {
						cnt++;
					}
					final Map<String, Object> map = CommonUtils.map();
					for (int i = 0; i < colCount; i++) {
						String name = indexNamelMap.get(i);
						Object value = rs.getObject(i + 1);
						map.put(name.intern(), value);
					}
					values.add(map);
					if (values.size() == limit) {
						break;
					}
				}
			}
			valueSelectionFunction = selectionStrategy.createValueSelectionFunction(this.values);
		}
	}

	/**
	 * 値をインデックスを指定して取得します。
	 * 
	 * @param i
	 * @return
	 */
	public Optional<Map<String, Object>> getValueMap(int i) {
		return valueSelectionFunction.get(i);
	}
}
