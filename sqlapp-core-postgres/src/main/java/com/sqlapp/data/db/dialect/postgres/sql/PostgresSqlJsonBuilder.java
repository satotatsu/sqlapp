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
 * PostgreSQL 17 SQL/JSON query expression builder.
 * Context items, PASSING values, defaults and SQL type definitions are SQL
 * fragments. JSON paths and identifiers are escaped by the builder.
 */
public class PostgresSqlJsonBuilder {
	private final Dialect dialect;

	public PostgresSqlJsonBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	public JsonExpression jsonValue(String contextItem, String path) {
		return new JsonExpression("JSON_VALUE", contextItem, path);
	}

	public JsonExpression jsonQuery(String contextItem, String path) {
		return new JsonExpression("JSON_QUERY", contextItem, path);
	}

	public JsonExpression jsonExists(String contextItem, String path) {
		return new JsonExpression("JSON_EXISTS", contextItem, path);
	}

	public String jsonSerialize(String expression, String returningType) {
		checkVersion();
		require(expression, "expression");
		require(returningType, "returningType");
		return "JSON_SERIALIZE(" + expression + " RETURNING " + returningType + ")";
	}

	public final class JsonExpression {
		private final String function;
		private final String contextItem;
		private final String path;
		private final List<PassingValue> passingValues = new ArrayList<>();
		private String returningType;
		private String wrapper;
		private String quotes;
		private String onEmpty;
		private String onError;

		private JsonExpression(String function, String contextItem, String path) {
			this.function = function;
			this.contextItem = contextItem;
			this.path = path;
		}

		public JsonExpression passing(String valueExpression, String variableName) {
			require(valueExpression, "valueExpression");
			require(variableName, "variableName");
			passingValues.add(new PassingValue(valueExpression, variableName));
			return this;
		}

		public JsonExpression returning(String sqlType) {
			this.returningType = sqlType;
			return this;
		}

		/**
		 * Examples: WITH WRAPPER, WITH CONDITIONAL WRAPPER, WITHOUT WRAPPER.
		 */
		public JsonExpression wrapper(String value) {
			this.wrapper = value;
			return this;
		}

		/**
		 * Examples: KEEP QUOTES, OMIT QUOTES ON SCALAR STRING.
		 */
		public JsonExpression quotes(String value) {
			this.quotes = value;
			return this;
		}

		/**
		 * Examples: NULL, ERROR, EMPTY ARRAY, DEFAULT 0.
		 */
		public JsonExpression onEmpty(String behavior) {
			this.onEmpty = behavior;
			return this;
		}

		/**
		 * Examples: NULL, ERROR, TRUE, FALSE, UNKNOWN, DEFAULT 0.
		 */
		public JsonExpression onError(String behavior) {
			this.onError = behavior;
			return this;
		}

		public String build() {
			checkVersion();
			require(contextItem, "contextItem");
			require(path, "path");
			validateOptions();
			StringBuilder builder = new StringBuilder(function).append("(")
					.append(contextItem).append(", ").append(sqlString(path));
			if (!passingValues.isEmpty()) {
				builder.append(" PASSING ");
				for (int i = 0; i < passingValues.size(); i++) {
					if (i > 0) {
						builder.append(", ");
					}
					PassingValue passing = passingValues.get(i);
					builder.append(passing.expression).append(" AS ")
							.append(dialect.quote(passing.name));
				}
			}
			if (!CommonUtils.isEmpty(returningType)) {
				builder.append(" RETURNING ").append(returningType);
			}
			appendOption(builder, wrapper);
			appendOption(builder, quotes);
			if (!CommonUtils.isEmpty(onEmpty)) {
				builder.append(" ").append(onEmpty).append(" ON EMPTY");
			}
			if (!CommonUtils.isEmpty(onError)) {
				builder.append(" ").append(onError).append(" ON ERROR");
			}
			return builder.append(")").toString();
		}

		private void validateOptions() {
			if ("JSON_EXISTS".equals(function)) {
				if (!CommonUtils.isEmpty(returningType) || !CommonUtils.isEmpty(wrapper)
						|| !CommonUtils.isEmpty(quotes) || !CommonUtils.isEmpty(onEmpty)) {
					throw new IllegalArgumentException(
							"JSON_EXISTS does not support RETURNING, WRAPPER, QUOTES or ON EMPTY.");
				}
			} else if ("JSON_VALUE".equals(function)
					&& (!CommonUtils.isEmpty(wrapper) || !CommonUtils.isEmpty(quotes))) {
				throw new IllegalArgumentException("JSON_VALUE does not support WRAPPER or QUOTES.");
			}
		}
	}

	private void appendOption(StringBuilder builder, String value) {
		if (!CommonUtils.isEmpty(value)) {
			builder.append(" ").append(value);
		}
	}

	private void checkVersion() {
		Dialect postgres17 = DialectHolder.postgreSQL170;
		if (dialect.compareTo(postgres17) < 0) {
			throw new IllegalArgumentException(
					"SQL/JSON query functions require PostgreSQL 17 or later.");
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

	private static final class PassingValue {
		private final String expression;
		private final String name;
		private PassingValue(String expression, String name) {
			this.expression = expression;
			this.name = name;
		}
	}
}
