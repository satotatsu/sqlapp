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
	public static final String DISABLE_WAL = "phoenix.disableWal";
	public static final String APPEND_ONLY_SCHEMA = "phoenix.appendOnlySchema";
	public static final String UPDATE_CACHE_FREQUENCY = "phoenix.updateCacheFrequency";
	public static final String GUIDE_POSTS_WIDTH = "phoenix.guidePostsWidth";
	public static final String TRANSACTIONAL = "phoenix.transactional";
	public static final String TRANSACTION_PROVIDER = "phoenix.transactionProvider";
	public static final String STORE_NULLS = "phoenix.storeNulls";

	private PhoenixDdlOptions() {
	}

	static void append(DbInfo specifics, boolean index, AbstractSqlBuilder<?> builder) {
		final List<Runnable> options = new ArrayList<>();
		final Integer buckets = saltBuckets(specifics, index);
		if (buckets != null) {
			options.add(() -> builder._add("SALT_BUCKETS=")._add(buckets));
		}
		if (!index) {
			final Boolean transactional = booleanValue(specifics, TRANSACTIONAL);
			final String provider = specifics.get(TRANSACTION_PROVIDER);
			if (Boolean.TRUE.equals(transactional))
				throw new UnsupportedOperationException(
						"Phoenix transactional table recreation requires a provider-aware native migration; "
								+ "transactional=" + transactional + ", provider=" + provider
								+ "; provider defaults are not inferred");
			if (provider != null)
				throw new IllegalArgumentException(
						"Phoenix TRANSACTION_PROVIDER requires a transactional table and a provider-aware migration");
			if (transactional != null) {
				if (!(builder.getDialect() instanceof com.sqlapp.data.db.dialect.phoenix.Phoenix phoenix)
						|| !phoenix.supportsTransactionalTables())
					throw new UnsupportedOperationException("Phoenix TRANSACTIONAL requires version 4.7 or later");
				booleanOption(specifics, TRANSACTIONAL, "TRANSACTIONAL", builder, options);
			}
			if (specifics.get(STORE_NULLS) != null) {
				if (!(builder.getDialect() instanceof com.sqlapp.data.db.dialect.phoenix.Phoenix phoenix)
						|| !phoenix.supportsStoreNulls())
					throw new UnsupportedOperationException("Phoenix STORE_NULLS requires version 4.3 or later");
				booleanOption(specifics, STORE_NULLS, "STORE_NULLS", builder, options);
			}
			for (String key : List.of(UPDATE_CACHE_FREQUENCY, GUIDE_POSTS_WIDTH)) {
				final String value = tuningValue(specifics, key, builder.getDialect());
				if (value != null)
					options.add(() -> {
						builder._add(tuningName(key))._add("=");
						appendTuningValue(builder, value);
					});
			}
			booleanOption(specifics, IMMUTABLE_ROWS, "IMMUTABLE_ROWS", builder, options);
			booleanOption(specifics, MULTI_TENANT, "MULTI_TENANT", builder, options);
			booleanOption(specifics, DISABLE_WAL, "DISABLE_WAL", builder, options);
			if (specifics.get(APPEND_ONLY_SCHEMA) != null) {
				if (!(builder.getDialect() instanceof com.sqlapp.data.db.dialect.phoenix.Phoenix phoenix)
						|| !phoenix.supportsAppendOnlySchema())
					throw new UnsupportedOperationException("Phoenix APPEND_ONLY_SCHEMA requires version 4.8 or later");
				booleanOption(specifics, APPEND_ONLY_SCHEMA, "APPEND_ONLY_SCHEMA", builder, options);
			}
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

	static Integer saltBuckets(DbInfo specifics, boolean index) {
		final String value = specifics.get(SALT_BUCKETS);
		if (value == null)
			return null;
		final int buckets;
		try {
			buckets = Integer.parseInt(value.strip());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(SALT_BUCKETS + " must be an integer", e);
		}
		if (buckets < (index ? 0 : 1) || buckets > 256)
			throw new IllegalArgumentException(SALT_BUCKETS + " must be between " + (index ? 0 : 1) + " and 256");
		return buckets;
	}

	private static void booleanOption(DbInfo specifics, String key, String sqlName, AbstractSqlBuilder<?> builder,
			List<Runnable> options) {
		final Boolean value = booleanValue(specifics, key);
		if (value == null)
			return;
		options.add(() -> builder._add(sqlName)._add("=")._add(value ? "TRUE" : "FALSE"));
	}

	static Boolean booleanValue(DbInfo specifics, String key) {
		final String value = specifics.get(key);
		if (value == null)
			return null;
		if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
			throw new IllegalArgumentException(key + " must be true or false");
		return Boolean.valueOf(value);
	}

	/**
	 * Canonical native tuning value; shared by catalog recovery and SQL generation.
	 */
	public static String normalizeTuningValue(String key, String value) {
		if (!UPDATE_CACHE_FREQUENCY.equals(key) && !GUIDE_POSTS_WIDTH.equals(key))
			throw new IllegalArgumentException("Unknown Phoenix tuning option: " + key);
		if (value == null)
			return null;
		final String text = value.strip().toUpperCase(java.util.Locale.ROOT);
		if (UPDATE_CACHE_FREQUENCY.equals(key) && (text.equals("ALWAYS") || text.equals("NEVER")))
			return text;
		if (GUIDE_POSTS_WIDTH.equals(key) && text.equals("NULL"))
			return text;
		try {
			final long number = Long.parseLong(text);
			if (number < 0)
				throw new NumberFormatException("negative value");
			return Long.toString(number);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(key + " requires a nonnegative BIGINT"
					+ (UPDATE_CACHE_FREQUENCY.equals(key) ? " or ALWAYS/NEVER" : " or explicit NULL"), e);
		}
	}

	static String tuningValue(DbInfo specifics, String key, com.sqlapp.data.db.dialect.Dialect dialect) {
		final String value = normalizeTuningValue(key, specifics.get(key));
		if (value != null && (!(dialect instanceof com.sqlapp.data.db.dialect.phoenix.Phoenix phoenix)
				|| !(UPDATE_CACHE_FREQUENCY.equals(key) ? phoenix.supportsUpdateCacheFrequency()
						: phoenix.supportsGuidePostsWidth())))
			throw new UnsupportedOperationException(tuningName(key) + " requires Phoenix "
					+ (UPDATE_CACHE_FREQUENCY.equals(key) ? "4.7" : "4.9") + " or later");
		return value;
	}

	static String tuningName(String key) {
		return UPDATE_CACHE_FREQUENCY.equals(key) ? "UPDATE_CACHE_FREQUENCY" : "GUIDE_POSTS_WIDTH";
	}

	static void appendTuningValue(AbstractSqlBuilder<?> builder, String value) {
		if (value.equals("ALWAYS") || value.equals("NEVER"))
			builder.sqlChar(value);
		else
			builder._add(value);
	}
}
