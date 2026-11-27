/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.host.pli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * YAML configuration of {@link PliSchemaImportCommand}.
 *
 * <p>
 * The minimum configuration is {@code schemaName} and one {@code structures}
 * entry with a {@code primaryKey}. Declaration names and item paths are matched
 * case-insensitively. Item paths are dot separated from the level-1 name, for
 * example {@code COMPANY_MASTER.DEPARTMENT_GROUP.EMPLOYEE_LIST}.
 * </p>
 */
@Getter
@Setter
public class PliImportConfiguration {

	/** Supported configuration format version. */
	public static final int FORMAT_VERSION = 1;

	private int formatVersion = FORMAT_VERSION;

	/** Target schema name. Required. */
	private String schemaName;

	/**
	 * Level-1 declarations to import. When empty, every declaration with a
	 * {@code structures} entry is imported.
	 */
	private List<String> includeDeclarations = new ArrayList<>();

	/** Level-1 declarations to skip. */
	private List<String> excludeDeclarations = new ArrayList<>();

	/** Root table settings per level-1 declaration. */
	private List<Structure> structures = new ArrayList<>();

	/**
	 * Item paths that are not imported. Their storage is still counted when offsets
	 * are calculated.
	 */
	private List<String> excludeItems = new ArrayList<>();

	/** Table names of array items, keyed by item path. */
	private Map<String, String> generatedTableNames = new LinkedHashMap<>();

	/** Column names of scalar items, keyed by item path. */
	private Map<String, String> columnNames = new LinkedHashMap<>();

	/**
	 * Occurrence column name pattern. {@code ${groupName}} is the array item name
	 * and {@code ${tableName}} the generated table name.
	 */
	private String occurrenceColumnNamePattern = "${groupName}_NO";

	/** Occurrence column names of array items, keyed by item path. */
	private Map<String, String> occurrenceColumnNames = new LinkedHashMap<>();

	/** Prefix column remarks with the remarks of enclosing non-array groups. */
	private boolean prefixGroupRemarks = true;

	/** How comments that are not inside an item are assigned. */
	private CommentPlacement commentPlacement = CommentPlacement.AUTO;

	/**
	 * Source margins as {@code [left, right]} 1-based inclusive columns, for
	 * example {@code [2, 72]} for sources with carriage control and sequence
	 * numbers. Empty means the whole line.
	 */
	private List<Integer> margins = new ArrayList<>();

	/** Map {@code BIT(1)} to BOOLEAN instead of BINARY(1). */
	private boolean bit1AsBoolean = true;

	/**
	 * Root table settings of one level-1 declaration.
	 */
	@Getter
	@Setter
	public static class Structure {
		/** Level-1 declaration name. Required. */
		private String name;
		/** Table name. Defaults to the declaration name. */
		private String tableName;
		/** Primary key column names of the root table. Required. */
		private List<String> primaryKey = new ArrayList<>();
	}

	/**
	 * Assignment of comments that appear between items.
	 */
	public enum CommentPlacement {
		/**
		 * A comment starting on the line where an item ends belongs to that item. Other
		 * comments belong to the next item unless a blank line separates them.
		 */
		AUTO,
		/** Comments belong to the preceding item. */
		TRAILING,
		/** Comments belong to the following item. */
		LEADING
	}
}
