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

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.util.CommonUtils;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * カラム設定
 */
@Getter
@Setter
@ToString
public class ColumnGeneratorSetting {
	/** 名前 */
	@JsonProperty(index = 0)
	private String name;
	/** データ型 */
	@JsonProperty(index = 1)
	private DataType dataType;
	/** 生成タイプ */
	@JsonProperty(index = 2)
	private String generationGroup;
	/** 開始値 */
	@JsonProperty(index = 3)
	private String minValue;
	/** 最大値 */
	@JsonProperty(index = 4)
	private String maxValue;
	/** 次の値の式 */
	@JsonProperty(index = 5)
	private String nextValue;
	/** 値のバリエーション */
	@JsonProperty(index = 6)
	private List<Object> values;
	/** 開始値(SQL) */
	@JsonIgnore
	private Object startValueObject;
	/** 最小値(オブジェクト) */
	@JsonIgnore
	private Object minValueObject;
	/** 最大値(オブジェクト) */
	@JsonIgnore
	private Object maxValueObject;
	@JsonIgnore
	private QueryGeneratorSetting queryGeneratorSetting;
	@JsonIgnore
	private FileGeneratorSetting fileGeneratorSetting;
	@JsonIgnore
	private Column column;
	@JsonIgnore
	private boolean primaryKeyOrIdentityColumn;
	@JsonIgnore
	private boolean primaryKeyAndForeignKeyColumn;

	/**
	 * 値をインデックスを指定して取得します。
	 * 
	 * @param i
	 * @return 値
	 */
	@JsonIgnore
	public Optional<Object> getValue(int i) {
		if (CommonUtils.isEmpty(values)) {
			return Optional.empty();
		}
		int size = values.size();
		int pos = i % size;
		Object value = values.get(pos);
		return Optional.of(value);
	}
}
