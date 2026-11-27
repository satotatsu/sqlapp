/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import com.sqlapp.data.db.dialect.Dialect;

/** Serializes generated Schema SQL using the actual target delimiter policy. */
public final class TransferSchemaSqlScript {
	private TransferSchemaSqlScript() {
	}

	/** A manual draft, never an execution approval or an automatic retry script. */
	public static String render(TransferSchemaPreparation.Plan plan, Dialect target, String jobFingerprint) {
		if (plan.statements().isEmpty())
			return null;
		final var text = new StringBuilder("-- sqlapp transfer Schema SQL draft\n")
				.append("-- Review dependencies, data impact, delimiters and transaction behavior before use.\n")
				.append("-- This file does not authorize execution or synchronize migration history.\n")
				.append("-- Job: ").append(line(jobFingerprint)).append('\n').append("-- Schema plan: ")
				.append(line(plan.fingerprint())).append('\n');
		for (var warning : plan.warnings())
			text.append("-- WARNING: ").append(line(warning)).append('\n');
		for (int index = 0; index < plan.statements().size(); index++) {
			final var statement = plan.statements().get(index);
			final String sql = statement.sql();
			if (sql == null || sql.isBlank())
				throw new IllegalArgumentException("Schema SQL statement is empty.");
			final var delimiter = target.createSqlDelimiter(sql);
			final String terminator = delimiter.getTerminator() == null || delimiter.getTerminator().isBlank() ? ";"
					: delimiter.getTerminator();
			final String start = delimiter.getStartStatementTerminator();
			final String end = delimiter.getEndStatementTerminator();
			text.append("\n-- Statement ").append(index + 1).append(" / ").append(line(statement.taskId()))
					.append('\n');
			if (start != null && !start.isBlank())
				text.append(start).append('\n');
			text.append(sql.stripTrailing()).append('\n');
			if (!(terminator.strip().equals(";") && sql.stripTrailing().endsWith(";")))
				text.append(terminator.stripTrailing()).append('\n');
			if (end != null && !end.isBlank() && !end.strip().equals(terminator.strip()))
				text.append(end).append('\n');
		}
		return text.toString();
	}

	private static String line(String value) {
		return value == null ? "not recorded" : value.replaceAll("\\p{Cntrl}", " ");
	}
}
