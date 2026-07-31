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

	public String json(String expression, boolean formatJson, boolean uniqueKeys) {
		checkVersion();
		require(expression, "expression");
		StringBuilder builder = new StringBuilder("JSON(").append(expression);
		if (formatJson) {
			builder.append(" FORMAT JSON");
		}
		if (uniqueKeys) {
			builder.append(" WITH UNIQUE KEYS");
		}
		return builder.append(")").toString();
	}

	public String jsonScalar(String expression) {
		checkVersion();
		require(expression, "expression");
		return "JSON_SCALAR(" + expression + ")";
	}

	public JsonObjectExpression jsonObject() {
		return new JsonObjectExpression();
	}

	public JsonArrayExpression jsonArray() {
		return new JsonArrayExpression();
	}

	public JsonArrayAggregateExpression jsonArrayAgg(String valueExpression) {
		return new JsonArrayAggregateExpression(valueExpression);
	}

	public JsonObjectAggregateExpression jsonObjectAgg(String keyExpression,
			String valueExpression) {
		return new JsonObjectAggregateExpression(keyExpression, valueExpression);
	}

	public IsJsonPredicate isJson(String expression) {
		return new IsJsonPredicate(expression);
	}

	public final class JsonObjectExpression {
		private final List<JsonObjectEntry> entries = new ArrayList<>();
		private boolean absentOnNull;
		private boolean uniqueKeys;
		private String returningType;

		public JsonObjectExpression entry(String keyExpression, String valueExpression) {
			return entry(keyExpression, valueExpression, false);
		}

		public JsonObjectExpression entry(String keyExpression, String valueExpression,
				boolean formatJson) {
			require(keyExpression, "keyExpression");
			require(valueExpression, "valueExpression");
			entries.add(new JsonObjectEntry(keyExpression, valueExpression, formatJson));
			return this;
		}

		public JsonObjectExpression absentOnNull(boolean value) {
			this.absentOnNull = value;
			return this;
		}

		public JsonObjectExpression uniqueKeys(boolean value) {
			this.uniqueKeys = value;
			return this;
		}

		public JsonObjectExpression returning(String sqlType) {
			this.returningType = sqlType;
			return this;
		}

		public String build() {
			checkConstructorVersion();
			StringBuilder builder = new StringBuilder("JSON_OBJECT(");
			for (int i = 0; i < entries.size(); i++) {
				if (i > 0) {
					builder.append(", ");
				}
				JsonObjectEntry entry = entries.get(i);
				builder.append(entry.keyExpression).append(" VALUE ")
						.append(entry.valueExpression);
				if (entry.formatJson) {
					builder.append(" FORMAT JSON");
				}
			}
			if (absentOnNull) {
				builder.append(" ABSENT ON NULL");
			}
			if (uniqueKeys) {
				builder.append(" WITH UNIQUE KEYS");
			}
			if (!CommonUtils.isEmpty(returningType)) {
				builder.append(" RETURNING ").append(returningType);
			}
			return builder.append(")").toString();
		}
	}

	public final class JsonArrayExpression {
		private final List<JsonArrayEntry> entries = new ArrayList<>();
		private String queryExpression;
		private boolean absentOnNull;
		private String returningType;

		public JsonArrayExpression value(String expression) {
			return value(expression, false);
		}

		public JsonArrayExpression value(String expression, boolean formatJson) {
			require(expression, "expression");
			if (!CommonUtils.isEmpty(queryExpression)) {
				throw new IllegalArgumentException(
						"JSON_ARRAY cannot combine values with a query expression.");
			}
			entries.add(new JsonArrayEntry(expression, formatJson));
			return this;
		}

		public JsonArrayExpression query(String expression) {
			require(expression, "query expression");
			if (!entries.isEmpty()) {
				throw new IllegalArgumentException(
						"JSON_ARRAY cannot combine a query expression with values.");
			}
			this.queryExpression = expression;
			return this;
		}

		public JsonArrayExpression absentOnNull(boolean value) {
			this.absentOnNull = value;
			return this;
		}

		public JsonArrayExpression returning(String sqlType) {
			this.returningType = sqlType;
			return this;
		}

		public String build() {
			checkConstructorVersion();
			StringBuilder builder = new StringBuilder("JSON_ARRAY(");
			if (!CommonUtils.isEmpty(queryExpression)) {
				builder.append(queryExpression);
			} else {
				for (int i = 0; i < entries.size(); i++) {
					if (i > 0) {
						builder.append(", ");
					}
					JsonArrayEntry entry = entries.get(i);
					builder.append(entry.expression);
					if (entry.formatJson) {
						builder.append(" FORMAT JSON");
					}
				}
			}
			if (absentOnNull) {
				builder.append(" ABSENT ON NULL");
			}
			if (!CommonUtils.isEmpty(returningType)) {
				builder.append(" RETURNING ").append(returningType);
			}
			return builder.append(")").toString();
		}
	}

	public final class JsonArrayAggregateExpression {
		private final String valueExpression;
		private String orderBy;
		private boolean absentOnNull;
		private String returningType;

		private JsonArrayAggregateExpression(String valueExpression) {
			require(valueExpression, "valueExpression");
			this.valueExpression = valueExpression;
		}

		public JsonArrayAggregateExpression orderBy(String expression) {
			require(expression, "orderBy");
			this.orderBy = expression;
			return this;
		}

		public JsonArrayAggregateExpression absentOnNull(boolean value) {
			this.absentOnNull = value;
			return this;
		}

		public JsonArrayAggregateExpression returning(String sqlType) {
			require(sqlType, "sqlType");
			this.returningType = sqlType;
			return this;
		}

		public String build() {
			checkConstructorVersion();
			StringBuilder builder = new StringBuilder("JSON_ARRAYAGG(")
					.append(valueExpression);
			if (!CommonUtils.isEmpty(orderBy)) {
				builder.append(" ORDER BY ").append(orderBy);
			}
			if (absentOnNull) {
				builder.append(" ABSENT ON NULL");
			}
			appendReturning(builder, returningType);
			return builder.append(")").toString();
		}
	}

	public final class JsonObjectAggregateExpression {
		private final String keyExpression;
		private final String valueExpression;
		private String orderBy;
		private boolean absentOnNull;
		private boolean uniqueKeys;
		private String returningType;

		private JsonObjectAggregateExpression(String keyExpression,
				String valueExpression) {
			require(keyExpression, "keyExpression");
			require(valueExpression, "valueExpression");
			this.keyExpression = keyExpression;
			this.valueExpression = valueExpression;
		}

		public JsonObjectAggregateExpression orderBy(String expression) {
			require(expression, "orderBy");
			this.orderBy = expression;
			return this;
		}

		public JsonObjectAggregateExpression absentOnNull(boolean value) {
			this.absentOnNull = value;
			return this;
		}

		public JsonObjectAggregateExpression uniqueKeys(boolean value) {
			this.uniqueKeys = value;
			return this;
		}

		public JsonObjectAggregateExpression returning(String sqlType) {
			require(sqlType, "sqlType");
			this.returningType = sqlType;
			return this;
		}

		public String build() {
			checkConstructorVersion();
			StringBuilder builder = new StringBuilder("JSON_OBJECTAGG(")
					.append(keyExpression).append(" VALUE ").append(valueExpression);
			if (!CommonUtils.isEmpty(orderBy)) {
				builder.append(" ORDER BY ").append(orderBy);
			}
			if (absentOnNull) {
				builder.append(" ABSENT ON NULL");
			}
			if (uniqueKeys) {
				builder.append(" WITH UNIQUE KEYS");
			}
			appendReturning(builder, returningType);
			return builder.append(")").toString();
		}
	}

	public final class IsJsonPredicate {
		private final String expression;
		private boolean not;
		private String type;
		private Boolean uniqueKeys;

		private IsJsonPredicate(String expression) {
			require(expression, "expression");
			this.expression = expression;
		}

		public IsJsonPredicate not(boolean value) {
			this.not = value;
			return this;
		}

		/**
		 * VALUE, SCALAR, ARRAY or OBJECT.
		 */
		public IsJsonPredicate type(String value) {
			require(value, "type");
			this.type = value;
			return this;
		}

		public IsJsonPredicate uniqueKeys(boolean value) {
			this.uniqueKeys = value;
			return this;
		}

		public String build() {
			checkConstructorVersion();
			StringBuilder builder = new StringBuilder(expression).append(" IS ");
			if (not) {
				builder.append("NOT ");
			}
			builder.append("JSON");
			if (!CommonUtils.isEmpty(type)) {
				builder.append(" ").append(type);
			}
			if (uniqueKeys != null) {
				builder.append(uniqueKeys ? " WITH UNIQUE KEYS"
						: " WITHOUT UNIQUE KEYS");
			}
			return builder.toString();
		}
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

	private void appendReturning(StringBuilder builder, String value) {
		if (!CommonUtils.isEmpty(value)) {
			builder.append(" RETURNING ").append(value);
		}
	}

	private void checkConstructorVersion() {
		Dialect postgres16 = DialectHolder.postgreSQL160;
		if (dialect.compareTo(postgres16) < 0) {
			throw new IllegalArgumentException(
					"SQL/JSON constructors and predicates require PostgreSQL 16 or later.");
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

	private static final class JsonObjectEntry {
		private final String keyExpression;
		private final String valueExpression;
		private final boolean formatJson;
		private JsonObjectEntry(String keyExpression, String valueExpression,
				boolean formatJson) {
			this.keyExpression = keyExpression;
			this.valueExpression = valueExpression;
			this.formatJson = formatJson;
		}
	}

	private static final class JsonArrayEntry {
		private final String expression;
		private final boolean formatJson;
		private JsonArrayEntry(String expression, boolean formatJson) {
			this.expression = expression;
			this.formatJson = formatJson;
		}
	}
}
