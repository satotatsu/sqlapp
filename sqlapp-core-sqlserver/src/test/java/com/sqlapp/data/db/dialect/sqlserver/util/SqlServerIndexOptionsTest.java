package com.sqlapp.data.db.dialect.sqlserver.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SqlServerIndexOptionsTest {

	@Test
	void test() {
		assertEquals(SqlServerIndexOptions.ALLOW_PAGE_LOCKS, SqlServerIndexOptions.parse("allowpagelocks"));
	}

}
