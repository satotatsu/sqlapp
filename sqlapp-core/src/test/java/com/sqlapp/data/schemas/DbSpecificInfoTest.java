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
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.Map;

import org.junit.jupiter.api.Test;

public class DbSpecificInfoTest {

	private DbInfo getInitial() {
		DbInfo storage = new DbInfo();
		storage.put("tablespace", "db2tablespace");
		return storage;
	}

	@Test
	public void testEquals() {
		DbInfo dbInfo1 = getInitial();
		DbInfo dbInfo2 = getInitial();
		assertEquals(dbInfo1, dbInfo2);
	}

	@Test
	public void testEquals1() {
		DbInfo dbInfo1 = getInitial();
		DbInfo dbInfo2 = getInitial();
		dbInfo1.put("tablespace", "db2tablespace2");
		assertNotSame(dbInfo1, dbInfo2);
	}

	@Test
	public void testGetStringString() {
		DbInfo storage = getInitial();
		assertEquals(storage.get("tablespace"), "db2tablespace");
	}

	@Test
	public void testEntrySet() {
		DbInfo storage = getInitial();
		for (Map.Entry<String, String> entry : storage.entrySet()) {
			if ("tablespace".equalsIgnoreCase(entry.getKey())) {
				assertEquals("db2tablespace", entry.getValue());
			}
		}
	}
}
