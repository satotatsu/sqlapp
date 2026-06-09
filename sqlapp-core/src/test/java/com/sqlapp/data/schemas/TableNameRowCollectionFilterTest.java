/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TableNameRowCollectionFilterTest {

	@Test
	void test() {
		TableNameRowCollectionFilter filter = new TableNameRowCollectionFilter();
		Table tablea = new Table("taba");
		Table tableb = new Table("tabb");
		Table tablec = new Table("tabc");
		assertTrue(filter.test(tablea.getRows()));
		assertTrue(filter.test(tableb.getRows()));
		assertTrue(filter.test(tablec.getRows()));
		filter.setIncludes("taba", "tabc");
		assertTrue(filter.test(tablea.getRows()));
		assertFalse(filter.test(tableb.getRows()));
		assertTrue(filter.test(tablec.getRows()));
	}

}
