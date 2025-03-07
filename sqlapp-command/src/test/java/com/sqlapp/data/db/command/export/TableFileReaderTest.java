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
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.data.db.command.export;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.export.TableFileReader.TableFilesPair;
import com.sqlapp.data.schemas.Catalog;

class TableFileReaderTest {

	@Test
	public void test() {
		TableFileReader reader=new TableFileReader();
		Catalog catalog=new Catalog();
		catalog.getSchemas().add(s->{
			s.setName("schema1");
			s.getTables().add(t->{
				t.setName("table1_1");
			});
			s.getTables().add(t->{
				t.setName("table1_2");
			});
		});
		catalog.getSchemas().add(s->{
			s.setName("schema2");
			s.getTables().add(t->{
				t.setName("table2_1");
			});
			s.getTables().add(t->{
				t.setName("table2_2");
			});
		});
		List<TableFilesPair> list=reader.getTableFilePairs(catalog);
		assertEquals(4, list.size());
	}

}
