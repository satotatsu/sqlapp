package com.sqlapp.data.db.dialect.mariadb;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;

class MariadbDefaultExpressionSupportTest {
	@Test
	void expressionDefaultsFollowTheMariadbVersion() {
		final var current = DialectResolver.getInstance().getDialect("MariaDB", 11, 8, 0).getDefaultExpressionSupport();
		assertInstanceOf(MariadbDefaultExpressionSupport.class, current);
		assertEquals("(CURRENT_DATE)",
				current.temporalCandidates(new Column("c").setDataType(DataType.DATE)).getFirst().sql());
		final var old = DialectResolver.getInstance().getDialect("MariaDB", 10, 1, 0).getDefaultExpressionSupport();
		assertTrue(old.temporalCandidates(new Column("c").setDataType(DataType.DATE)).isEmpty());
	}
}
