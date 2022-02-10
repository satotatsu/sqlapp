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

package com.sqlapp.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.sqlapp.exceptions.JdbcDriverNotFoundException;

public class JdbcUtilsTest {

	/**
	 * URLからJDBCドライバークラス名の取得テスト
	 */
	@Test
	public void testgetDriverClassNameByUrl() {
		assertEquals("org.hsqldb.jdbcDriver", JdbcUtils.getDriverClassNameByUrl("jdbc:hsqldb:."));
	}

	/**
	 * URLからJDBCドライバークラス名の取得テスト
	 */
	@Test
	public void testgetDriverClassNameByUrlError() {
		try{
			JdbcUtils.getDriverClassNameByUrl("jdbc:oracle:thin:@127.0.0.1:1521:DBName");
			assertTrue(false);
		} catch(JdbcDriverNotFoundException e){
			assertTrue(true);
		}
	}

}
