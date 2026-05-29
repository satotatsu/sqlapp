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
