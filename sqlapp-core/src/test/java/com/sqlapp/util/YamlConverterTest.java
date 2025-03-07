/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.util;

import java.io.File;
import java.text.ParseException;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.AbstractTest;

/**
 * Json用のユーティリティのテストケース
 * 
 * 
 */
public class YamlConverterTest extends AbstractTest {

	private final YamlConverter yamlConverter = new YamlConverter();
	private final JsonConverter jsonConverter = new JsonConverter();

	/**
	 * YAMLのテストを行います
	 * 
	 * @throws ParseException
	 */
	@Test
	public void testToYaml() throws ParseException {
		final Object obj = jsonConverter
				.fromJsonString(FileUtils.getInputStream(new File("src/test/resources/test.json")), Object.class);
		final String text = yamlConverter.toJsonString(obj);
		System.out.println(text);
	}

	@Test
	public void testToYaml2() throws ParseException {
		final Map<String, Object> map = CommonUtils.linkedMap();
		map.put("id", 1);
		map.put("name", "name1");
		final String text = yamlConverter.toJsonString(map);
		System.out.println(text);
	}

}
