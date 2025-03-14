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

import java.util.List;
import java.util.Optional;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.util.CommonUtils;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * カラム設定
 */
@Getter
@Setter
@EqualsAndHashCode
public class ColumnDataGeneratorSetting {
	/** シート列名 */
	private String colString;
	/** 名前 */
	private String name;
	/** データ型 */
	private DataType dataType;
	/** 生成タイプ */
	private String generationGroup;
	/** INSERT除外カラム */
	private boolean insertExclude;
	/** 開始値 */
	private String startValue;
	/** 最大値 */
	private String maxValue;
	/** 次の値の式 */
	private String nextValue;
	/** 値のバリエーション */
	private List<Object> values;
	/** 開始値(オブジェクト) */
	private Object startValueObject;
	/** 最大値(オブジェクト) */
	private Object maxValueObject;

	private QueryDefinitionDataGeneratorSetting queryDefinitionDataGeneratorSetting;

	/**
	 * 値をインデックスを指定して取得します。
	 * 
	 * @param i
	 * @return 値
	 */
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
