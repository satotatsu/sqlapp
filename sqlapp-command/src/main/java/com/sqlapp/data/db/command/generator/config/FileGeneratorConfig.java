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

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.builder.HashCodeBuilder;

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
public class FileGeneratorConfig {
	/** 生成タイプ */
	@JsonProperty(index = 0)
	private String lookupGroup;
	/** data expression */
	@JsonProperty(index = 1)
	private String dataSourceExpression;
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
	private Column[] relationColumns;
	@JsonIgnore
	private AbstractValueSelectionFunction valueSelectionFunction;
	@JsonIgnore
	private TableGeneratorConfig tableGeneratorConfig;

	public FileGeneratorConfig() {
		selectionStrategy = ValueSelectStrategy.NEXT_VALUE;
	}

	@JsonIgnore
	public String getConditionKey() {
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
		final Object objTmp = tableGeneratorConfig.eval(dataSourceExpression);
		if (objTmp instanceof String) {

		} else if (objTmp instanceof Iterable) {
			final Iterable<Map<String, Object>> itr = tableGeneratorConfig.eval(dataSourceExpression);
			for (Map<String, Object> obj : itr) {
				if (i < offset) {
					i++;
					continue;
				}
				values.add(internKeyMap(obj));
				if (values.size() == limit) {
					break;
				}
				i++;
			}
		}
		valueSelectionFunction = selectionStrategy.createValueSelectionFunction(this.values,
				selectionStrategyWeightExpression, this.tableGeneratorConfig.getEvaluator());
	}

	private Map<String, Object> internKeyMap(Map<String, Object> map) {
		Map<String, Object> result = CommonUtils.linkedMapInternKey(map);
		return result;
	}

	/**
	 * コピー元からクエリの結果をコピーします
	 * 
	 * @param original コピー元
	 */
	public void copyData(FileGeneratorConfig original) {
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

	@Override
	public int hashCode() {
		HashCodeBuilder builder = new HashCodeBuilder();
		builder.append(this.getLookupGroup());
		builder.append(this.getClass());
		builder.append(this.getLimit());
		builder.append(this.getOffset());
		return builder.toHashCode();
	}

}
