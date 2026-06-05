/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
