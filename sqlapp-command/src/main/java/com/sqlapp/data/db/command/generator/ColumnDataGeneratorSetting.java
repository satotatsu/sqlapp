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
