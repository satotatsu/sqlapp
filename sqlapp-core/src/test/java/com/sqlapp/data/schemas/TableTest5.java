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

package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

public class TableTest5 {

	@Test
	public void test() throws XMLStreamException {
		final Table table = new Table();
		String text = """
				<table xml:space="preserve" name="test">
					<rows>
						<row>
							<value key="id">10</value>
							<value key="created_at">2016-09-05 17:19:30.000000000</value>
							<value key="updated_at">2016-09-05 17:19:30.000000000</value>
							<value key="version_no">1</value>
							<value key="name">name1</value>
							<value key="description">desc1</value>
						</row>
						<row>
							<value key="id">20</value>
							<value key="created_at">2016-09-05 17:19:30.000000000</value>
							<value key="updated_at">2016-09-05 17:19:30.000000000</value>
							<value key="version_no">1</value>
							<value key="name">name2</value>
							<value key="description">desc2</value>
						</row>
						</rows>
				</table>""";
		StringReader reader = new StringReader(text);
		table.loadXml(reader);
		assertEquals(6, table.getColumns().size());
		assertEquals(2, table.getRows().size());
		int i = 0;
		Row row = table.getRows().get(i++);
		assertEquals(10L, (Long) row.get("id"));
		row = table.getRows().get(i++);
		assertEquals(20L, (Long) row.get("id"));
	}
}
