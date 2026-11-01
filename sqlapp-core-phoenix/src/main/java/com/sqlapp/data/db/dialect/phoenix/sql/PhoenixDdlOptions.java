/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.schemas.DbInfo;
import com.sqlapp.util.AbstractSqlBuilder;

/** Optional native properties carried by the existing Schema specifics map. */
public final class PhoenixDdlOptions {
	public static final String SALT_BUCKETS = "phoenix.saltBuckets";
	public static final String IMMUTABLE_ROWS = "phoenix.immutableRows";
	public static final String MULTI_TENANT = "phoenix.multiTenant";
	public static final String DEFAULT_COLUMN_FAMILY = "phoenix.defaultColumnFamily";

	private PhoenixDdlOptions() {
	}

	static void append(DbInfo specifics, boolean index, AbstractSqlBuilder<?> builder) {
		final List<Runnable> options = new ArrayList<>();
		final String salt = specifics.get(SALT_BUCKETS);
		if (salt != null) {
			final int buckets;
			try {
				buckets = Integer.parseInt(salt.strip());
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(SALT_BUCKETS + " must be an integer", e);
			}
			if (buckets < (index ? 0 : 1) || buckets > 256)
				throw new IllegalArgumentException(SALT_BUCKETS + " must be between " + (index ? 0 : 1) + " and 256");
			options.add(() -> builder._add("SALT_BUCKETS=")._add(buckets));
		}
		if (!index) {
			booleanOption(specifics, IMMUTABLE_ROWS, "IMMUTABLE_ROWS", builder, options);
			booleanOption(specifics, MULTI_TENANT, "MULTI_TENANT", builder, options);
			final String family = specifics.get(DEFAULT_COLUMN_FAMILY);
			if (family != null) {
				if (family.isBlank())
					throw new IllegalArgumentException(DEFAULT_COLUMN_FAMILY + " must not be blank");
				options.add(() -> builder._add("DEFAULT_COLUMN_FAMILY=").sqlChar(family));
			}
		}
		for (int i = 0; i < options.size(); i++) {
			if (i == 0)
				builder.space();
			else
				builder._add(", ");
			options.get(i).run();
		}
	}

	private static void booleanOption(DbInfo specifics, String key, String sqlName, AbstractSqlBuilder<?> builder,
			List<Runnable> options) {
		final String value = specifics.get(key);
		if (value == null)
			return;
		if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
			throw new IllegalArgumentException(key + " must be true or false");
		options.add(() -> builder._add(sqlName)._add("=")._add(value.toUpperCase(java.util.Locale.ROOT)));
	}
}
