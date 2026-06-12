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

import org.junit.jupiter.api.Test;

import com.sqlapp.util.CommonUtils;

class WeightedSelectionFunctionTest {

	@Test
	void test() {
		List<Map<String, Object>> list = CommonUtils.list();
		String key1 = "名前";
		String key2 = "weight";
		list.add(Map.of(key1, "佐藤", key2, 1000));
		list.add(Map.of(key1, "鈴木", key2, 80));
		list.add(Map.of(key1, "田中", key2, 80));
		list.add(Map.of(key1, "加藤", key2, 50));
		WeightedSelectionFunction func = new WeightedSelectionFunction(list, key2);
		for (int i = 0; i < 100; i++) {
			Map<String, Object> map = func.get(i);
			System.out.println(map.get(key1));
		}
	}
}
