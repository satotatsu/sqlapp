/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sqlapp.util.JsonConverter;

class TransferFailureDiagnosticTest {
	@ParameterizedTest
	@CsvSource({ "23505, UNIQUE_CONSTRAINT", "23503, FOREIGN_KEY", "23502, NOT_NULL", "23513, CHECK_CONSTRAINT",
			"23514, CHECK_CONSTRAINT", "23000, CONSTRAINT", "22003, VALUE_CONVERSION", "42501, PERMISSION",
			"28000, AUTHENTICATION", "08006, CONNECTION", "3D000, DATABASE", "3F000, SCHEMA", "42P01, SQL_OR_METADATA",
			"0A000, UNSUPPORTED", "40001, TRANSACTION", "HYT00, TIMEOUT_OR_CANCELLATION",
			"57014, TIMEOUT_OR_CANCELLATION", "ZZ999, UNKNOWN" })
	void retainsOnlyCodesAndSelfAuthoredGuidance(String state, TransferFailureDiagnostic.Kind kind) {
		var failure = new SQLException("password=private-value jdbc:private SQL row-secret", state, 987);
		var diagnostic = TransferFailureDiagnostic.capture(new IllegalStateException("wrapper-secret", failure));
		assertEquals(kind, diagnostic.kind());
		assertEquals(state, diagnostic.sqlState());
		assertEquals(987, diagnostic.vendorCode());
		assertFalse(diagnostic.reason().isBlank());
		assertFalse(diagnostic.nextStep().isBlank());
		var displayed = diagnostic.toString() + diagnostic.reason() + diagnostic.nextStep()
				+ new JsonConverter().toJsonString(diagnostic);
		assertFalse(displayed.contains("private"));
		assertFalse(displayed.contains("secret"));
	}

	@Test
	void batchWrapperUsesRecognizedChainedStateAndTerminatesOnCycles() {
		var batch = new BatchUpdateException("private SQL", "HY000", 1, new int[0]);
		var constraint = new SQLException("private row", "23505", 2);
		batch.setNextException(constraint);
		constraint.initCause(batch);
		var diagnostic = TransferFailureDiagnostic.capture(batch);
		assertEquals(TransferFailureDiagnostic.Kind.UNIQUE_CONSTRAINT, diagnostic.kind());
		assertEquals("23505", diagnostic.sqlState());
		assertEquals(2, diagnostic.vendorCode());
		var generic = new BatchUpdateException("private", "23000", 1, new int[0]);
		generic.setNextException(new SQLException("private", "23505", 2));
		assertEquals(TransferFailureDiagnostic.Kind.UNIQUE_CONSTRAINT,
				TransferFailureDiagnostic.capture(generic).kind());
		var unknown = new SQLException("secret", "ZZ999", 3);
		var wrapper = new IllegalStateException(unknown);
		unknown.initCause(wrapper);
		assertEquals(TransferFailureDiagnostic.Kind.UNKNOWN, TransferFailureDiagnostic.capture(wrapper).kind());
	}

	@Test
	void malformedMissingAndUnknownCodesAreSafeAndDoNotInventCauses() {
		var malformed = TransferFailureDiagnostic.capture(new SQLException("private", "secret\nURL", 42));
		assertNull(malformed.sqlState());
		assertEquals(42, malformed.vendorCode());
		assertEquals(TransferFailureDiagnostic.Kind.UNKNOWN, malformed.kind());
		assertEquals(TransferFailureDiagnostic.Kind.UNKNOWN,
				TransferFailureDiagnostic.capture(new IllegalArgumentException("secret validation detail")).kind());
		var timeout = TransferFailureDiagnostic.capture(new SQLTimeoutException("private"));
		assertEquals(TransferFailureDiagnostic.Kind.TIMEOUT_OR_CANCELLATION, timeout.kind());
		assertNull(timeout.sqlState());
		assertEquals(TransferFailureDiagnostic.Kind.VERIFICATION_MISMATCH,
				TransferFailureDiagnostic.capture(new IllegalStateException("private"), true).kind());
		assertEquals(TransferFailureDiagnostic.Kind.CONNECTION,
				TransferFailureDiagnostic.capture(new SQLException("private", "08006"), true).kind());
		assertThrows(IllegalArgumentException.class,
				() -> new TransferFailureDiagnostic(TransferFailureDiagnostic.Kind.UNKNOWN, "private", null));
	}
}
