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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class ModuleHelperTest {

	@Test
	void testForName() throws ClassNotFoundException {
		String modulePath = System.getProperty("jdk.module.path");
		if (!CommonUtils.isEmpty(modulePath)) {
			Class<?> clazz = ModuleHelper.getInstance().forName("com.sqlapp.data.db.dialect.Dialect");
			assertEquals("com.sqlapp.data.db.dialect.Dialect", clazz.getName());
		}
	}

	@Test
	void getResourceAsStream() throws IOException {
		String modulePath = System.getProperty("jdk.module.path");
		if (!CommonUtils.isEmpty(modulePath)) {
			assertTrue(ModuleHelper.getInstance().getResourceAsStream("com/sqlapp/data/messages.properties") != null);
		}
	}

}
