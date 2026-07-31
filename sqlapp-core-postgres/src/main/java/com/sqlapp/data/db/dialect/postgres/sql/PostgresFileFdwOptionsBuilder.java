/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.util.CommonUtils;

/**
 * {@code file_fdw} foreign-table options builder.
 */
public class PostgresFileFdwOptionsBuilder {
	private static final Set<String> FORMATS = Set.of("text", "csv", "binary");
	private static final Set<String> ON_ERRORS = Set.of("stop", "ignore");
	private static final Set<String> LOG_VERBOSITIES =
			Set.of("default", "verbose", "silent");

	private final Dialect dialect;
	private final Map<String, String> options = new LinkedHashMap<>();
	private String filename;
	private String program;
	private String format = "text";
	private String onError;
	private Long rejectLimit;
	private String logVerbosity;

	public PostgresFileFdwOptionsBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	public PostgresFileFdwOptionsBuilder filename(String value) {
		this.filename = require(value, "filename");
		return this;
	}

	public PostgresFileFdwOptionsBuilder program(String value) {
		this.program = require(value, "program");
		return this;
	}

	public PostgresFileFdwOptionsBuilder format(String value) {
		this.format = normalized(value, "format", FORMATS);
		return this;
	}

	public PostgresFileFdwOptionsBuilder header(boolean value) {
		options.put("header", Boolean.toString(value));
		return this;
	}

	public PostgresFileFdwOptionsBuilder option(String name, String value) {
		String normalizedName = require(name, "optionName")
				.toLowerCase(Locale.ROOT);
		if (Set.of("filename", "program", "format", "on_error",
				"reject_limit", "log_verbosity").contains(normalizedName)) {
			throw new IllegalArgumentException(
					normalizedName + " must be set using its dedicated method.");
		}
		options.put(normalizedName,
				Objects.requireNonNull(value, "optionValue"));
		return this;
	}

	public PostgresFileFdwOptionsBuilder onError(String value) {
		this.onError = normalized(value, "onError", ON_ERRORS);
		return this;
	}

	public PostgresFileFdwOptionsBuilder rejectLimit(long value) {
		if (value <= 0) {
			throw new IllegalArgumentException("rejectLimit must be positive.");
		}
		this.rejectLimit = value;
		return this;
	}

	public PostgresFileFdwOptionsBuilder logVerbosity(String value) {
		this.logVerbosity = normalized(
				value, "logVerbosity", LOG_VERBOSITIES);
		return this;
	}

	public String build() {
		validate();
		Map<String, String> all = new LinkedHashMap<>();
		if (filename != null) {
			all.put("filename", filename);
		} else {
			all.put("program", program);
		}
		all.put("format", format);
		all.putAll(options);
		if (onError != null) {
			all.put("on_error", onError);
		}
		if (rejectLimit != null) {
			all.put("reject_limit", rejectLimit.toString());
		}
		if (logVerbosity != null) {
			all.put("log_verbosity", logVerbosity);
		}
		StringBuilder builder = new StringBuilder("OPTIONS (");
		int index = 0;
		for (Map.Entry<String, String> entry : all.entrySet()) {
			if (index++ > 0) {
				builder.append(", ");
			}
			builder.append(entry.getKey()).append(" ")
					.append(sqlString(entry.getValue()));
		}
		return builder.append(")").toString();
	}

	private void validate() {
		if ((filename == null) == (program == null)) {
			throw new IllegalArgumentException(
					"Exactly one of filename or program must be specified.");
		}
		if (onError != null || rejectLimit != null || logVerbosity != null) {
			if (dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
				throw new IllegalArgumentException(
						"file_fdw error-handling options require PostgreSQL 18 or later.");
			}
		}
		if ("ignore".equals(onError)
				&& !Set.of("text", "csv").contains(format)) {
			throw new IllegalArgumentException(
					"onError ignore requires text or csv format.");
		}
		if (rejectLimit != null && !"ignore".equals(onError)) {
			throw new IllegalArgumentException(
					"rejectLimit requires onError ignore.");
		}
		if (logVerbosity != null && !"ignore".equals(onError)) {
			throw new IllegalArgumentException(
					"logVerbosity requires onError ignore.");
		}
	}

	private String normalized(String value, String name, Set<String> values) {
		String normalized = require(value, name).toLowerCase(Locale.ROOT);
		if (!values.contains(normalized)) {
			throw new IllegalArgumentException(
					name + " must be one of " + values + ".");
		}
		return normalized;
	}

	private String sqlString(String value) {
		return "'" + value.replace("'", "''") + "'";
	}

	private String require(String value, String name) {
		if (CommonUtils.isEmpty(value)) {
			throw new IllegalArgumentException(name + " must not be empty.");
		}
		return value;
	}
}
