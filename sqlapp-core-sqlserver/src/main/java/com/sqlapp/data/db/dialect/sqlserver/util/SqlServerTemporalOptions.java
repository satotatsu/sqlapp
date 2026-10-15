/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.util;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import com.sqlapp.data.schemas.SystemVersioning;

/** Optional SQL Server temporal settings stored in the shared Schema model. */
public final class SqlServerTemporalOptions {
	public static final String HISTORY_RETENTION_PERIOD = "HISTORY_RETENTION_PERIOD";
	private static final Pattern RETENTION = Pattern.compile("([1-9][0-9]*) (DAY|WEEK|MONTH|YEAR)S?");

	private SqlServerTemporalOptions() {
	}

	/**
	 * Sets a validated retention period. Use INFINITE to explicitly remove a finite
	 * limit.
	 */
	public static SystemVersioning historyRetentionPeriod(SystemVersioning configuration, String value) {
		Objects.requireNonNull(configuration, "configuration").getSpecifics().put(HISTORY_RETENTION_PERIOD,
				normalize(value));
		return configuration;
	}

	/** Returns the canonical setting, or null when no setting was specified. */
	public static String getHistoryRetentionPeriod(SystemVersioning configuration) {
		if (configuration == null || !configuration.getSpecifics().containsKey(HISTORY_RETENTION_PERIOD))
			return null;
		return normalize(configuration.getSpecifics().get(HISTORY_RETENTION_PERIOD));
	}

	private static String normalize(String value) {
		if (value != null) {
			String text = value.strip().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
			if (text.equals("INFINITE"))
				return text;
			var match = RETENTION.matcher(text);
			if (match.matches()) {
				try {
					int number = Integer.parseInt(match.group(1));
					return number + " " + match.group(2) + "S";
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException(
							"History retention duration exceeds the SQL Server integer range", e);
				}
			}
		}
		throw new IllegalArgumentException(
				"HISTORY_RETENTION_PERIOD requires INFINITE or a positive integer followed by DAYS, WEEKS, MONTHS or YEARS");
	}
}
