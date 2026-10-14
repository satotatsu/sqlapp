package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;

class PostgresExtensionSettingsBuilderTest {
	@Test
	void validatesPasswordLengthWithServerBounds() {
		var builder = new PostgresExtensionSettingsBuilder(DialectHolder.postgreSQL180);
		assertEquals("SET passwordcheck.min_password_length = 12", builder.passwordMinimumLength(12));
		assertEquals("SET passwordcheck.min_password_length = 0", builder.passwordMinimumLength(0));
		assertEquals("SET passwordcheck.min_password_length = 2147483647", builder.passwordMinimumLength(Integer.MAX_VALUE));
		assertThrows(IllegalArgumentException.class, () -> builder.passwordMinimumLength(-1));
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresExtensionSettingsBuilder(DialectHolder.postgreSQL170).passwordMinimumLength(12));
	}
}
