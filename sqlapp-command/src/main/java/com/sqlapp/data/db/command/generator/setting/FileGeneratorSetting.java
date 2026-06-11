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

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

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
@EqualsAndHashCode(exclude = { "values", "relationColumns", "valueSelectionFunction", "tableGeneratorSetting" })
public class FileGeneratorSetting {
	/** 生成タイプ */
	@JsonProperty(index = 0)
	private String generationGroup;
	/** data expression */
	@JsonProperty(index = 1)
	private String dataSourceExpression;
	/** data mapping */
	@JsonProperty(index = 1)
	private String dataMappingExpression;
	@JsonProperty(index = 2)
	private int limit = Integer.MAX_VALUE;
	@JsonProperty(index = 3)
	private int offset = 0;
	/** ValueSelectStrateg */
	@JsonProperty(index = 4)
	private ValueSelectStrategy selectionStrategy;
	@JsonIgnore
	private List<Map<String, Object>> values;
	@JsonIgnore
	private Column[] relationColumns;
	@JsonIgnore
	private AbstractValueSelectionFunction valueSelectionFunction;
	@JsonIgnore
	private TableGeneratorSetting tableGeneratorSetting;

	public FileGeneratorSetting() {
		selectionStrategy = ValueSelectStrategy.NEXT_VALUE;
	}

	@JsonIgnore
	public String getFileConditionKey() {
		StringBuilder builder = new StringBuilder();
		builder.append(dataSourceExpression.trim().hashCode());
		builder.append(":");
		builder.append(limit);
		builder.append(":");
		builder.append(offset);
		builder.append(":");
		builder.append(this.getClass().hashCode());
		return builder.toString();
	}

	/**
	 * 式でデータを読み込みます
	 * 
	 */
	public void loadData() {
		this.values = CommonUtils.list();
		long i = 0;
		final Iterable<Map<String, Object>> itr = tableGeneratorSetting.eval(dataSourceExpression);
		for (Map<String, Object> obj : itr) {
			if (i < offset) {
				i++;
				continue;
			}
			values.add(convertValue(obj));
			if (values.size() == limit) {
				break;
			}
			i++;
			valueSelectionFunction = selectionStrategy.createValueSelectionFunction(this.values);
		}
	}

	private Map<String, Object> convertValue(Map<String, Object> obj) {
		if (CommonUtils.isEmpty(dataMappingExpression)) {
			return obj;
		}
		return tableGeneratorSetting.eval(dataMappingExpression, obj);
	}

	/**
	 * コピー元からクエリの結果をコピーします
	 * 
	 * @param original コピー元
	 */
	public void copyData(FileGeneratorSetting original) throws SQLException {
		this.values = original.values;
		valueSelectionFunction = selectionStrategy.createValueSelectionFunction(original.values);
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
		return CommonUtils.caseInsensitiveMap(map);
	}
}
