package com.sqlapp.data.db.command.generator.setting.strategy;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ValueSelectStrategy {
	NEXT_VALUE() {
		@Override
		public AbstractValueSelectionFunction createValueSelectionFunction(List<Map<String, Object>> values) {
			return new SimpleValueSelectionFunction(values);
		}
	},
	RANDOM() {
		@Override
		public AbstractValueSelectionFunction createValueSelectionFunction(List<Map<String, Object>> values) {
			return new RandomValueSelectionFunction(values);
		}
	};

	public AbstractValueSelectionFunction createValueSelectionFunction(List<Map<String, Object>> values) {
		return null;
	}

	/**
	 * 文字列からenumオブジェクトを取得します
	 * 
	 * @param value
	 */
	@JsonCreator
	public static ValueSelectStrategy parse(String value) {
		if (value == null) {
			return NEXT_VALUE;
		}
		for (ValueSelectStrategy enm : ValueSelectStrategy.values()) {
			if (value.equalsIgnoreCase(enm.name())) {
				return enm;
			}
		}
		return NEXT_VALUE;
	}
}
