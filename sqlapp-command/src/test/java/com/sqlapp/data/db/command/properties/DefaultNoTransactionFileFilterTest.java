/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.data.db.command.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;

import org.junit.jupiter.api.Test;

class DefaultNoTransactionFileFilterTest {

	@Test
	void test() {
		DefaultNoTransactionFileFilter filter = new DefaultNoTransactionFileFilter();
		assertEquals(false, filter.test(new File("123_ABC.sql")));
		assertEquals(false, filter.test(new File("123_ABC")));
		assertEquals(true, filter.test(new File("123_ABC_NO_TRAN.sql")));
		assertEquals(true, filter.test(new File("123_ABC_NO_TRANSACTION.sql")));
		assertEquals(true, filter.test(new File("123_ABC_NO_TRAN")));
		assertEquals(true, filter.test(new File("123_ABC_NO_TRANSACTION")));
		assertEquals(true, filter.test(new File("123_ABC_NoTransaction")));
		assertEquals(true, filter.test(new File("123_ABC_NoTran")));
	}

}
