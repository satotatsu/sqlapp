package com.sqlapp.data.db.command.generator.setting.strategy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.sqlapp.util.CommonUtils;

public class SimpleValueSelectionFunction extends AbstractValueSelectionFunction {

	public SimpleValueSelectionFunction(List<Map<String, Object>> values) {
		super(values);
	}

	@Override
	public Optional<Map<String, Object>> get(int i) {
		if (CommonUtils.isEmpty(this.getValues())) {
			return Optional.empty();
		}
		int size = this.getValues().size();
		int pos = i % size;
		Map<String, Object> value = this.getValues().get(pos);
		return Optional.of(value);
	}

}
