/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import com.sqlapp.data.db.command.migration.transfer.TransferJobPreparationService.PreparationException;

/**
 * Self-authored guidance and structured codes only; never retains a driver
 * exception.
 */
final class TransferActionFailure extends IllegalArgumentException {
	private static final long serialVersionUID = 1L;
	final String phase;
	final String reason;
	final String nextStep;
	final String technical;

	TransferActionFailure(String phase, String reason, String nextStep, String technical) {
		super(reason);
		this.phase = phase;
		this.reason = reason;
		this.nextStep = nextStep;
		this.technical = technical;
	}

	static TransferActionFailure preparation(PreparationException failure) {
		final String state = failure.sqlState();
		String reason = failure.validationDetail();
		String next = "Use Test connection for each endpoint, then check the saved job's source schema, selected tables and target settings.";
		if (reason == null) {
			if (state != null && state.startsWith("28")) {
				reason = "Database authentication failed.";
				next = "Check the username/password for the failing endpoint, then use Test connection.";
			} else if ("3D000".equals(state)) {
				reason = "The database selected by the JDBC URL was not found.";
				next = "Check the database name in the JDBC URL. A PostgreSQL URL path selects a database; select the target schema separately in Job settings.";
			} else if (state != null && state.startsWith("08")) {
				reason = "The database connection could not be opened or was lost.";
				next = "Use Test connection for the failing endpoint. Check server/port and database availability; for file databases also check the file path, access permissions and locks.";
			} else if ("42501".equals(state)) {
				reason = "Database permissions are insufficient for this operation.";
				next = "Check metadata/table access for the failing endpoint and permissions for any requested DDL.";
			} else if (state != null && state.startsWith("42"))
				reason = "Database metadata or SQL could not be resolved with the current settings.";
			else
				reason = "The transfer could not be prepared at the stage shown below.";
		} else if (reason.contains("target table was not found"))
			next = "If you want to create the table, enable Create missing target tables in Job settings. Otherwise prepare the matching empty target table, then review again.";
		else if (reason.contains("must be empty"))
			next = "Choose an empty target table or a different destination schema, then review again. Existing rows are never cleared automatically.";
		else if (reason.contains("missing columns"))
			next = "Align the existing target columns with the source, or choose another empty target. This workflow does not alter existing tables.";
		else
			next = "Correct the setting or target condition described above, then review again. Existing rows are never cleared automatically.";
		return new TransferActionFailure(failure.phase(), reason, next,
				"Exception type: " + failure.exceptionType() + "\nSQLState: " + (state == null ? "not provided" : state)
						+ "; vendor code: " + (failure.vendorCode() == null ? "not provided" : failure.vendorCode()));
	}

	String summary() {
		return "Transfer action failed\n\nFailed while: " + phase + "\n\nReason: " + reason
				+ "\n\nWhat to check next:\n" + nextStep + "\n\n" + technical
				+ "\n\nNo transfer is approved by this failure. Review again after fixing the problem.";
	}
}
