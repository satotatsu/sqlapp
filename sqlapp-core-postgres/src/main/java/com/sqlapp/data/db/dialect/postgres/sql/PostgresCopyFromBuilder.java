/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.util.CommonUtils;

/**
 * PostgreSQL {@code COPY ... FROM STDIN} SQL builder.
 * <p>
 * PostgreSQL JDBC's {@code CopyManager} can execute the generated SQL. PostgreSQL
 * 17 options are checked against the supplied dialect.
 */
public class PostgresCopyFromBuilder {
	private final Dialect dialect;
	private final String schemaName;
	private final String tableName;
	private final List<String> columns = new ArrayList<>();
	private final List<String> options = new ArrayList<>();
	private String onError;
	private String logVerbosity;
	private Long rejectLimit;
	private boolean forceNullAll;
	private boolean forceNotNullAll;
	private boolean freeze;
	private boolean foreignTable;
	private String format;

	public PostgresCopyFromBuilder(Dialect dialect, String tableName) {
		this(dialect, null, tableName);
	}

	public PostgresCopyFromBuilder(Dialect dialect, String schemaName,
			String tableName) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
		require(tableName, "tableName");
		this.schemaName = schemaName;
		this.tableName = tableName;
	}

	public PostgresCopyFromBuilder column(String name) {
		require(name, "column");
		columns.add(name);
		return this;
	}

	public PostgresCopyFromBuilder format(String value) {
		require(value, "format");
		this.format = value;
		return option("FORMAT", value);
	}

	public PostgresCopyFromBuilder header(boolean value) {
		return option("HEADER", Boolean.toString(value));
	}

	public PostgresCopyFromBuilder freeze(boolean value) {
		this.freeze = value;
		return this;
	}

	/**
	 * Marks the target as a foreign table for version-specific validation.
	 */
	public PostgresCopyFromBuilder foreignTable(boolean value) {
		this.foreignTable = value;
		return this;
	}

	public PostgresCopyFromBuilder delimiter(String value) {
		require(value, "delimiter");
		options.add("DELIMITER " + sqlString(value));
		return this;
	}

	public PostgresCopyFromBuilder nullString(String value) {
		Objects.requireNonNull(value, "nullString");
		options.add("NULL " + sqlString(value));
		return this;
	}

	/**
	 * PostgreSQL 17 {@code ON_ERROR stop|ignore}.
	 */
	public PostgresCopyFromBuilder onError(String value) {
		require(value, "onError");
		if (!"stop".equalsIgnoreCase(value) && !"ignore".equalsIgnoreCase(value)) {
			throw new IllegalArgumentException(
					"onError must be stop or ignore.");
		}
		this.onError = value;
		return this;
	}

	/**
	 * PostgreSQL 17 {@code LOG_VERBOSITY default|verbose}.
	 */
	public PostgresCopyFromBuilder logVerbosity(String value) {
		require(value, "logVerbosity");
		if (!"default".equalsIgnoreCase(value)
				&& !"verbose".equalsIgnoreCase(value)
				&& !"silent".equalsIgnoreCase(value)) {
			throw new IllegalArgumentException(
					"logVerbosity must be default, verbose or silent.");
		}
		this.logVerbosity = value;
		return this;
	}

	/**
	 * PostgreSQL 18 maximum tolerated input conversion errors.
	 */
	public PostgresCopyFromBuilder rejectLimit(long value) {
		if (value <= 0) {
			throw new IllegalArgumentException(
					"rejectLimit must be greater than zero.");
		}
		this.rejectLimit = value;
		return this;
	}

	public PostgresCopyFromBuilder encoding(String value) {
		require(value, "encoding");
		options.add("ENCODING " + sqlString(value));
		return this;
	}

	/**
	 * PostgreSQL 17 CSV import option {@code FORCE_NULL *}.
	 */
	public PostgresCopyFromBuilder forceNullAll(boolean value) {
		this.forceNullAll = value;
		return this;
	}

	/**
	 * PostgreSQL 17 CSV import option {@code FORCE_NOT_NULL *}.
	 */
	public PostgresCopyFromBuilder forceNotNullAll(boolean value) {
		this.forceNotNullAll = value;
		return this;
	}

	public String build() {
		validateVersion();
		StringBuilder builder = new StringBuilder("COPY ");
		if (!CommonUtils.isEmpty(schemaName)) {
			builder.append(dialect.quote(schemaName)).append(".");
		}
		builder.append(dialect.quote(tableName));
		if (!columns.isEmpty()) {
			builder.append(" (");
			for (int i = 0; i < columns.size(); i++) {
				if (i > 0) {
					builder.append(", ");
				}
				builder.append(dialect.quote(columns.get(i)));
			}
			builder.append(")");
		}
		builder.append(" FROM STDIN");
		if (!options.isEmpty() || !CommonUtils.isEmpty(onError)
				|| !CommonUtils.isEmpty(logVerbosity) || rejectLimit != null
				|| freeze || forceNullAll || forceNotNullAll) {
			builder.append(" WITH (");
			List<String> allOptions = new ArrayList<>(options);
			if (freeze) {
				allOptions.add("FREEZE true");
			}
			if (!CommonUtils.isEmpty(onError)) {
				allOptions.add("ON_ERROR " + onError);
			}
			if (rejectLimit != null) {
				allOptions.add("REJECT_LIMIT " + rejectLimit);
			}
			if (!CommonUtils.isEmpty(logVerbosity)) {
				allOptions.add("LOG_VERBOSITY " + logVerbosity);
			}
			if (forceNullAll) {
				allOptions.add("FORCE_NULL *");
			}
			if (forceNotNullAll) {
				allOptions.add("FORCE_NOT_NULL *");
			}
			builder.append(String.join(", ", allOptions)).append(")");
		}
		return builder.toString();
	}

	private PostgresCopyFromBuilder option(String name, String value) {
		require(value, name);
		options.add(name + " " + value);
		return this;
	}

	private void validateVersion() {
		if ((!CommonUtils.isEmpty(onError) || !CommonUtils.isEmpty(logVerbosity))
				|| forceNullAll || forceNotNullAll) {
			if (dialect.compareTo(DialectHolder.postgreSQL170) < 0) {
				throw new IllegalArgumentException(
						"COPY ON_ERROR, LOG_VERBOSITY and all-column FORCE options require PostgreSQL 17 or later.");
			}
		}
		if (!CommonUtils.isEmpty(logVerbosity)
				&& !"ignore".equalsIgnoreCase(onError)) {
			throw new IllegalArgumentException(
					"COPY LOG_VERBOSITY requires ON_ERROR ignore.");
		}
		if (rejectLimit != null) {
			if (dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
				throw new IllegalArgumentException(
						"COPY REJECT_LIMIT requires PostgreSQL 18 or later.");
			}
			if (!"ignore".equalsIgnoreCase(onError)) {
				throw new IllegalArgumentException(
						"COPY REJECT_LIMIT requires ON_ERROR ignore.");
			}
		}
		if ("silent".equalsIgnoreCase(logVerbosity)
				&& dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
			throw new IllegalArgumentException(
					"COPY LOG_VERBOSITY silent requires PostgreSQL 18 or later.");
		}
		if ((forceNullAll || forceNotNullAll)
				&& !"csv".equalsIgnoreCase(format)) {
			throw new IllegalArgumentException(
					"COPY all-column FORCE options require FORMAT csv.");
		}
		if (freeze && foreignTable
				&& dialect.compareTo(DialectHolder.postgreSQL180) >= 0) {
			throw new IllegalArgumentException(
					"COPY FREEZE is not supported for foreign tables on PostgreSQL 18 or later.");
		}
	}

	private String sqlString(String value) {
		return "'" + value.replace("'", "''") + "'";
	}

	private void require(String value, String name) {
		if (CommonUtils.isEmpty(value)) {
			throw new IllegalArgumentException(name + " must not be empty.");
		}
	}
}
