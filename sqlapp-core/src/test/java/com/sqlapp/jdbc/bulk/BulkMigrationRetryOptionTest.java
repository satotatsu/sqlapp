/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.sql.SQLTransientException;

import org.junit.jupiter.api.Test;

class BulkMigrationRetryOptionTest {
	@Test
	void selectsOnlyConfiguredRetryableFailuresAndHonorsTheLimit() {
		final var retry = BulkMigrationRetryOption.builder().maxRetries(2)
				.initialBackoffMillis(10).backoffMultiplier(3).maxBackoffMillis(50)
				.sqlState("40001").errorCode(1205).build();
		retry.validate();

		assertTrue(retry.shouldRetry(new SQLTransientException("transient"), 0));
		assertTrue(retry.shouldRetry(new SQLException("state", "40001"), 0));
		assertTrue(retry.shouldRetry(new SQLException("code", null, 1205), 1));
		assertFalse(retry.shouldRetry(new SQLException("other", "22000"), 0));
		assertFalse(retry.shouldRetry(new SQLTransientException("limit"), 2));
		assertEquals(10, retry.backoffMillis(1));
		assertEquals(30, retry.backoffMillis(2));
		assertEquals(50, retry.backoffMillis(3));
	}
}
