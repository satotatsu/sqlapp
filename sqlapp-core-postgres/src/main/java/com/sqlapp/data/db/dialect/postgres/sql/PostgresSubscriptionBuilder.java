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
 * PostgreSQL logical-replication subscription SQL builder.
 */
public class PostgresSubscriptionBuilder {
	public enum Streaming {
		OFF("off"), ON("on"), PARALLEL("parallel");

		private final String sql;

		Streaming(String sql) {
			this.sql = sql;
		}
	}

	private final Dialect dialect;
	private final String subscriptionName;
	private final List<String> publications = new ArrayList<>();
	private String connection;
	private Streaming streaming;
	private Boolean twoPhase;
	private Boolean enabled;
	private Boolean copyData;

	public PostgresSubscriptionBuilder(Dialect dialect,
			String subscriptionName) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
		this.subscriptionName = require(subscriptionName, "subscriptionName");
	}

	public PostgresSubscriptionBuilder connection(String value) {
		this.connection = require(value, "connection");
		return this;
	}

	public PostgresSubscriptionBuilder publication(String name) {
		publications.add(require(name, "publication"));
		return this;
	}

	public PostgresSubscriptionBuilder streaming(Streaming value) {
		this.streaming = Objects.requireNonNull(value, "streaming");
		return this;
	}

	public PostgresSubscriptionBuilder twoPhase(boolean value) {
		this.twoPhase = value;
		return this;
	}

	public PostgresSubscriptionBuilder enabled(boolean value) {
		this.enabled = value;
		return this;
	}

	public PostgresSubscriptionBuilder copyData(boolean value) {
		this.copyData = value;
		return this;
	}

	public Streaming getEffectiveDefaultStreaming() {
		return dialect.compareTo(DialectHolder.postgreSQL180) >= 0
				? Streaming.PARALLEL : Streaming.OFF;
	}

	public String buildCreate() {
		require(connection, "connection");
		if (publications.isEmpty()) {
			throw new IllegalArgumentException(
					"At least one publication must be specified.");
		}
		checkStreamingVersion();
		StringBuilder builder = new StringBuilder("CREATE SUBSCRIPTION ")
				.append(dialect.quote(subscriptionName))
				.append(" CONNECTION ").append(sqlString(connection))
				.append(" PUBLICATION ");
		appendNames(builder, publications);
		List<String> options = new ArrayList<>();
		if (streaming != null) {
			options.add("streaming = " + streaming.sql);
		}
		if (twoPhase != null) {
			options.add("two_phase = " + twoPhase);
		}
		if (enabled != null) {
			options.add("enabled = " + enabled);
		}
		if (copyData != null) {
			options.add("copy_data = " + copyData);
		}
		if (!options.isEmpty()) {
			builder.append(" WITH (").append(String.join(", ", options))
					.append(")");
		}
		return builder.toString();
	}

	/**
	 * PostgreSQL 18 can change the two-phase state of an existing subscription.
	 */
	public String alterTwoPhase(boolean value) {
		if (dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
			throw new IllegalArgumentException(
					"ALTER SUBSCRIPTION two_phase requires PostgreSQL 18 or later.");
		}
		return "ALTER SUBSCRIPTION " + dialect.quote(subscriptionName)
				+ " SET (two_phase = " + value + ")";
	}

	private void checkStreamingVersion() {
		if (streaming == Streaming.PARALLEL
				&& dialect.compareTo(DialectHolder.postgreSQL160) < 0) {
			throw new IllegalArgumentException(
					"Subscription streaming=parallel requires PostgreSQL 16 or later.");
		}
	}

	private void appendNames(StringBuilder builder, List<String> names) {
		for (int i = 0; i < names.size(); i++) {
			if (i > 0) {
				builder.append(", ");
			}
			builder.append(dialect.quote(names.get(i)));
		}
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
