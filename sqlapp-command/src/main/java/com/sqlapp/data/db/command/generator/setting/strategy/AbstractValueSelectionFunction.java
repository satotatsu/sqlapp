package com.sqlapp.data.db.command.generator.setting.strategy;

import java.util.List;
import java.util.Map;

import lombok.Getter;

@Getter
public abstract class AbstractValueSelectionFunction implements ValueSelectionFunction {
	private final List<Map<String, Object>> values;

	public AbstractValueSelectionFunction(List<Map<String, Object>> values) {
		this.values = values;
	}
}
