/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.util.ResourceFinder.ResourceInfo;

/**
 * リソース検索テスト
 * 
 * @author tatsuo satoh
 * 
 */
public class ResourceFinderTest {

	@Test
	public void testFromJar() {
		ResourceFinder finder = new ResourceFinder();
		finder.setExtensions("text");
		List<ResourceInfo> rerouces = finder.find("org.hsqldb.lib.tar.rb");
		assertTrue(rerouces.size() > 0);
		System.out.println(rerouces);
		ResourceInfo resourceInfo = CommonUtils.first(rerouces);
		List<String> texts = resourceInfo.readAsText();
		System.out.println(texts);
		assertTrue(texts.size() > 0);
	}

	@Test
	public void testFromFile() {
		ResourceFinder finder = new ResourceFinder();
		finder.setExtensions("sql");
		List<ResourceInfo> rerouces = finder
				.find("com.sqlapp.data.db.dialect.information_schema.metadata");
		assertTrue(rerouces.size() > 0);
		System.out.println(rerouces);
		ResourceInfo resourceInfo = CommonUtils.first(rerouces);
		List<String> texts = resourceInfo.readAsText();
		System.out.println(texts);
		assertTrue(texts.size() > 0);
	}

	@Test
	public void testFromJarWithSimpleURLClassLoader() {
		SimpleURLClassLoader classLoader = new SimpleURLClassLoader(new File(
				"src/test/resources"));
		ResourceFinder finder = new ResourceFinder(classLoader);
		List<ResourceInfo> rerouces = finder.find("");
		assertTrue(rerouces.size() > 0);
		System.out.println(rerouces);
		ResourceInfo resourceInfo = CommonUtils.first(rerouces);
		List<String> texts = resourceInfo.readAsText();
		System.out.println(texts);
	}

}
