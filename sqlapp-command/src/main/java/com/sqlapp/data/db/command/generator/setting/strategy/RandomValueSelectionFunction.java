package com.sqlapp.data.db.command.generator.setting.strategy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import com.sqlapp.util.CommonUtils;

public class RandomValueSelectionFunction extends AbstractValueSelectionFunction {

	private Random random;

	public RandomValueSelectionFunction(List<Map<String, Object>> values) {
		super(values);
		random = new Random();
	}

	@Override
	public Optional<Map<String, Object>> get(int i) {
		if (CommonUtils.isEmpty(this.getValues())) {
			return Optional.empty();
		}
		int size = random.nextInt(this.getValues().size());
		int pos = i % size;
		Map<String, Object> value = this.getValues().get(pos);
		return Optional.of(value);
	}

}
