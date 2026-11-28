/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/** Connection-free YAML definition of SELECT-defined models and their tests. */
@Getter
@Setter
public class MigrationModelConfiguration {
	private List<Model> models = new ArrayList<>();
	private List<Source> sources = new ArrayList<>();
	private List<UnitTest> unitTests = new ArrayList<>();

	@Getter
	@Setter
	public static class Model {
		private String name;
		/** Optional schema in which the relation is created. */
		private String schema;
		/** SQL file, relative to the configuration file. */
		private String sql;
		/** Inline SQL; alternative to {@link #sql}. */
		private String query;
		/** {@code view} (default) or {@code table}. */
		private String materialized;
		private Map<String, Object> parameters = new LinkedHashMap<>();
		/**
		 * Optional output columns, in order. Requires {@code materialized: table}.
		 */
		private List<Column> columns = new ArrayList<>();
		/** Optional primary key; requires {@link #columns}. */
		private List<String> primaryKey = new ArrayList<>();
		/** Checks of the built model. */
		private List<DataTest> dataTests = new ArrayList<>();
	}

	/**
	 * One check of a built model. Set exactly one of the rule properties; name and
	 * severity are optional.
	 */
	@Getter
	@Setter
	public static class DataTest {
		/** Unique test name; defaults to the model, rule and columns. */
		private String name;
		/** {@code error} (default) fails the run, {@code warn} only reports. */
		private String severity;
		/** Columns that must not be NULL. */
		private List<String> notNull;
		/** Columns whose combination must be unique. */
		private List<String> unique;
		private AcceptedValues acceptedValues;
		private Relationships relationships;
		/** Condition that must be true for every row. */
		private String expression;
		private RowCount rowCount;
		/** Query that returns the failing rows; may use $ref and $source. */
		private String sql;
	}

	@Getter
	@Setter
	public static class AcceptedValues {
		private String column;
		private List<Object> values = new ArrayList<>();
	}

	/** Every non-NULL value must exist in the referenced model or source. */
	@Getter
	@Setter
	public static class Relationships {
		private String column;
		private List<String> columns;
		/** Referenced model or source. */
		private String to;
		private String field;
		private List<String> fields;
	}

	@Getter
	@Setter
	public static class RowCount {
		private Long min;
		private Long max;
	}

	@Getter
	@Setter
	public static class Column {
		private String name;
		/** sqlapp DataType with optional length and scale, such as VARCHAR(50). */
		private String type;
		private boolean notNull;
	}

	@Getter
	@Setter
	public static class Source {
		private String name;
		private String schema;
		/** Existing table, optionally written as {@code schema.table}. */
		private String table;
		/** Data file, relative to the configuration file. */
		private String file;
		/** Optional file format when the extension is not sufficient. */
		private String format;
	}

	@Getter
	@Setter
	public static class UnitTest {
		private String name;
		private String model;
		private Map<String, Given> given = new LinkedHashMap<>();
		private Expect expect;
		private boolean ordered;
		/**
		 * Test an incremental model as when it adds rows: isIncremental is true and the
		 * fixture named after the model is its existing table.
		 */
		private boolean incremental;
	}

	@Getter
	@Setter
	public static class Given {
		private List<String> columns = new ArrayList<>();
		private List<List<Object>> rows = new ArrayList<>();
		private String sql;
	}

	@Getter
	@Setter
	public static class Expect {
		private List<List<Object>> rows = new ArrayList<>();
	}
}
