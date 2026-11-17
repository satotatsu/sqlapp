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
