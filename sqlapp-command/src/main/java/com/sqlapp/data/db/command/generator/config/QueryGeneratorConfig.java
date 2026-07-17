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

package com.sqlapp.data.db.command.generator.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sqlapp.data.db.command.generator.config.strategy.AbstractValueSelectionFunction;
import com.sqlapp.data.db.command.generator.config.strategy.ValueSelectStrategy;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.util.CommonUtils;

import lombok.Getter;
import lombok.Setter;

/**
 * クエリー生成設定
 */
@Getter
@Setter
public class QueryGeneratorConfig {
	/** 生成タイプ */
	@JsonProperty(index = 0)
	private String generationGroup;
	/** SELECT SQL */
	@JsonProperty(index = 1)
	private String selectSql;
	/** data mapping */
	@JsonProperty(index = 2)
	private String columnMappingExpression;
	@JsonProperty(index = 3)
	private int limit = Integer.MAX_VALUE;
	@JsonProperty(index = 4)
	private int offset = 0;
	/** ValueSelectStrateg */
	@JsonProperty(index = 5)
	private ValueSelectStrategy selectionStrategy;
	@JsonProperty(index = 6)
	private String selectionStrategyWeightExpression;
	@JsonIgnore
	private List<Map<String, Object>> values;
	@JsonIgnore
	private List<Column> relationColumns;
	@JsonIgnore
	private AbstractValueSelectionFunction valueSelectionFunction;
	@JsonIgnore
	private TableGeneratorConfig tableGeneratorConfig;

	public QueryGeneratorConfig() {
		selectionStrategy = ValueSelectStrategy.NEXT_VALUE;
	}

	@JsonIgnore
	public String getConditionKey() {
		StringBuilder builder = new StringBuilder();
		builder.append(selectSql.trim().hashCode());
		builder.append(":");
		builder.append(limit);
		builder.append(":");
		builder.append(offset);
		builder.append(":");
		builder.append(this.getClass().hashCode());
		return builder.toString();
	}

	/**
	 * DBからデータを読み込みます
	 * 
	 * @param conn DBコネクション
	 * @throws SQLException
	 */
	public void loadData(final Connection conn) throws SQLException {
		this.values = CommonUtils.list();
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
						final String name = indexNamelMap.get(i);
						final Object value = rs.getObject(i + 1);
						map.put(name.intern(), value);
					}
					values.add(map);
					if (values.size() == limit) {
						break;
					}
				}
			}
			valueSelectionFunction = selectionStrategy.createValueSelectionFunction(this.values,
					selectionStrategyWeightExpression, this.tableGeneratorConfig.getEvaluator());
		}
	}

	/**
	 * コピー元からクエリの結果をコピーします
	 * 
	 * @param original コピー元
	 */
	public void copyData(QueryGeneratorConfig original) throws SQLException {
		this.values = original.values;
		valueSelectionFunction = selectionStrategy.createValueSelectionFunction(original.values,
				selectionStrategyWeightExpression, this.tableGeneratorConfig.getEvaluator());
	}

	/**
	 * 値をインデックスを指定して取得します。
	 * 
	 * @param i
	 * @return
	 */
	public Map<String, Object> getValueMap(int i) {
		final Map<String, Object> map = valueSelectionFunction.get(i);
		if (map == null) {
			return map;
		}
		return CommonUtils.caseInsensitiveMap(convertValue(map));
	}

	private Map<String, Object> convertValue(Map<String, Object> obj) {
		if (CommonUtils.isEmpty(columnMappingExpression)) {
			return obj;
		}
		return tableGeneratorConfig.eval(columnMappingExpression, obj);
	}
}
