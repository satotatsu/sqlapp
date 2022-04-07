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

package com.sqlapp.data.db.dialect.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.test.AbstractTest;

public class SqlTokenizerTest extends AbstractTest{

	@Test
	public void testHasNext() {
		final SqlTokenizer sqlTokenizer=new SqlTokenizer(this.getResource("split.sql"));
		sqlTokenizer.hasNext();
		String value=sqlTokenizer.next();
		assertEquals("use master/*#schemaNameSuffix*/", value);
		sqlTokenizer.hasNext();
		value=sqlTokenizer.next();
		assertEquals("SET SESSION FOREIGN_KEY_CHECKS=0", value);
		sqlTokenizer.hasNext();
		value=sqlTokenizer.next();
		assertEquals("/* Create Tables */", value);
		sqlTokenizer.hasNext();
		value=sqlTokenizer.next();
		assertEquals("CREATE TABLE apps\n(\n	id bigint NOT NULL,\n	created_at datetime,\n	updated_at datetime,\n	PRIMARY KEY (id),\n	UNIQUE (created_at)\n)", value);
		sqlTokenizer.hasNext();
		value=sqlTokenizer.next();
		assertEquals("CREATE TABLE app_icons\n(\n	id bigint NOT NULL,\n	binary_data mediumblob NOT NULL,\n	PRIMARY KEY (id)\n)", value);
		assertEquals(false, sqlTokenizer.hasNext());
	}
	
	@Test
	public void test2() {
		final SqlTokenizer sqlTokenizer=new SqlTokenizer("    \nGO  \n");
		
	}

}
