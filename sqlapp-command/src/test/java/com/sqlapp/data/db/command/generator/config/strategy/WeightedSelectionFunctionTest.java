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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;


class WeightedSelectionFunctionTest {
	private WeightedSelectionFunction function(List<Map<String, Object>> values, long seed) {
		return new WeightedSelectionFunction(values, "weight",
				com.sqlapp.data.db.command.generator.util.CachedMvelEvaluatorUtils.getCachedMvelEvaluator(),
				new java.util.Random(seed));
	}

	@Test
	void skipsZeroWeightsIncludingRepeatedCumulativeWeights() {
		var values = List.<Map<String, Object>>of(Map.of("weight", 0), Map.of("weight", 1),
				Map.of("weight", 0), Map.of("weight", 0));
		var f = function(values, 42);
		for (int i = 0; i < 100; i++) {
			org.junit.jupiter.api.Assertions.assertSame(values.get(1), f.get(i));
		}
	}

	@Test
	void supportsLongWeightsAndReproducesSelection() {
		var values = List.<Map<String, Object>>of(Map.of("weight", 3000000000L), Map.of("weight", 4000000000L));
		var a = function(values, 42);
		var b = function(values, 42);
		var selected = new java.util.HashSet<>();
		for (int i = 0; i < 100; i++) {
			var value = a.get(i);
			org.junit.jupiter.api.Assertions.assertEquals(value, b.get(i));
			selected.add(value);
		}
		org.junit.jupiter.api.Assertions.assertEquals(2, selected.size());
	}

	@Test
	void rejectsInvalidWeights() {
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> function(List.of(), 1));
		for (long weight : new long[] {0, -1}) {
			org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
					() -> function(List.of(Map.of("weight", weight)), 1));
		}
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> function(List.of(Map.of("weight", Long.MAX_VALUE), Map.of("weight", 1)), 1));
	}
}
