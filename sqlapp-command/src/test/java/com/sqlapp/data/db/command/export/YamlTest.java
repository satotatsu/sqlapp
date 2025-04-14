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

/**
 * This file is part of sqlapp.
 *
 * sqlapp is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.command.export;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.AbstractTest;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.YamlConverter;

public class YamlTest extends AbstractTest {

	@Test
	public void test() {
		YamlConverter converter = new YamlConverter();
		List<Map<String, Object>> list = CommonUtils.list();
		Map<String, Object> map = CommonUtils.linkedMap();
		map.put("id", 1);
		map.put("name", "name1");
		list.add(map);
		map = CommonUtils.linkedMap();
		map.put("id", 2);
		map.put("name", "name2");
		list.add(map);
		System.out.println(converter.toJsonString(list));
	}

	@Test
	public void testParse() {
		YamlConverter converter = new YamlConverter();
		String text = this.getResource("TAB1.yaml");
		@SuppressWarnings("unchecked")
		List<Object> list = converter.fromJsonString(text, List.class);
		assertEquals(10, list.size());
	}
}