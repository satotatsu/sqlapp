/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.io.Serializable;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.util.List;

import lombok.Builder;
import lombok.Value;

/** Safe retry selection and exponential backoff for transactional chunks. */
@Value
@Builder
public class BulkMigrationRetryOption implements Serializable {
	private static final long serialVersionUID = 1L;
	@Builder.Default
	int maxRetries = 0;
	@Builder.Default
	long initialBackoffMillis = 1_000;
	@Builder.Default
	double backoffMultiplier = 2d;
	@Builder.Default
	long maxBackoffMillis = 30_000;
	@Builder.Default
	boolean retryTransientExceptions = true;
	@lombok.Singular("sqlState")
	List<String> sqlStates;
	@lombok.Singular("errorCode")
	List<Integer> errorCodes;

	public static BulkMigrationRetryOption none() {
		return builder().build();
	}

	void validate() {
		if (maxRetries < 0 || initialBackoffMillis < 0 || maxBackoffMillis < 0) {
			throw new IllegalArgumentException("retry counts and backoff must not be negative");
		}
		if (!Double.isFinite(backoffMultiplier) || backoffMultiplier < 1d) {
			throw new IllegalArgumentException("backoffMultiplier must be finite and at least one");
		}
		if (maxBackoffMillis < initialBackoffMillis) {
			throw new IllegalArgumentException(
					"maxBackoffMillis must not be less than initialBackoffMillis");
		}
		for (final String state : sqlStates) {
			if (state == null || state.isBlank()) {
				throw new IllegalArgumentException("retry SQLState must not be empty");
			}
		}
	}

	boolean shouldRetry(final SQLException failure, final int retriesAlreadyAttempted) {
		if (retriesAlreadyAttempted >= maxRetries) {
			return false;
		}
		for (SQLException current = failure; current != null;
				current = current.getNextException()) {
			if (retryTransientExceptions && current instanceof SQLTransientException
					|| current.getSQLState() != null && sqlStates.contains(current.getSQLState())
					|| errorCodes.contains(current.getErrorCode())) {
				return true;
			}
		}
		return false;
	}

	long backoffMillis(final int retryNumber) {
		if (retryNumber <= 0) {
			throw new IllegalArgumentException("retryNumber must be positive");
		}
		final double calculated = initialBackoffMillis
				* Math.pow(backoffMultiplier, retryNumber - 1d);
		return calculated >= maxBackoffMillis || !Double.isFinite(calculated)
				? maxBackoffMillis : (long) calculated;
	}
}
