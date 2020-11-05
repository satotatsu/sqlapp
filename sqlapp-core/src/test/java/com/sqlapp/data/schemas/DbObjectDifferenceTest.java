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

import java.util.Map;

import org.junit.jupiter.api.Test;

public class DbObjectDifferenceTest {

	@Test
	public void testProperties() {
		Table table1 = TableTest.getTable("Tablea");
		Table table2 = TableTest.getTable("Tablea");
		table1.getColumns().get(1).setDefaultValue("'1'");
		DbObjectDifference diff = new DbObjectDifference(table1, table2);
		Map<String, Difference<?>> modifiedMap = diff.getProperties(State.Modified);
		for (Map.Entry<String, Difference<?>> entry : modifiedMap.entrySet()) {
			Difference<?> difference=entry.getValue();
			System.out.println(difference);
		}
		System.out.println(diff);
	}

}
