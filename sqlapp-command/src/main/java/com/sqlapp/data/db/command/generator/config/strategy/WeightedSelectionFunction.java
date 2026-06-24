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

package com.sqlapp.data.db.command.generator.config.strategy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.util.eval.CachedEvaluator;
import com.sqlapp.util.eval.mvel.CachedMvelEvaluator;

public class WeightedSelectionFunction extends AbstractValueSelectionFunction {

	private static final Random RANDOM = new Random();

	private final long[] cumulativeWeights;
	private final long totalWeight;
	private CachedEvaluator evaluator;

	public WeightedSelectionFunction(List<Map<String, Object>> list, String weightExpression,
			CachedEvaluator evaluator) {
		super(list);
		this.evaluator = evaluator;
		this.cumulativeWeights = new long[list.size()];
		int sum = 0;
		for (int i = 0; i < list.size(); i++) {
			final Map<String, Object> map = list.get(i);
			Object obj = getWeight(map, weightExpression);
			long val;
			if (obj == null) {
				val = 1;
			} else {
				val = Converters.getDefault().convertObject(obj, long.class);
			}
			sum += val;
			cumulativeWeights[i] = sum;
		}
		this.totalWeight = sum;
	}

	public WeightedSelectionFunction(List<Map<String, Object>> list, String weightExpression) {
		this(list, weightExpression, CachedMvelEvaluator.getInstance());
	}

	private Object getWeight(Map<String, Object> map, String weightExpression) {
		return evaluator.eval(weightExpression, map);
	}

	@Override
	public Map<String, Object> get(int i) {
		// 0 ～ totalWeight-1
		long r = RANDOM.nextLong(totalWeight);
		// 1 ～ totalWeight に変換
		long key = r + 1;
		int index = Arrays.binarySearch(cumulativeWeights, key);
		if (index < 0) {
			index = -(index + 1);
		}
		return this.getValues().get(index);
	}
}
