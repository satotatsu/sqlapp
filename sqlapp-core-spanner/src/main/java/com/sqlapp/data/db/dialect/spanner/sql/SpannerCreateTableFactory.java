/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-spanner.
 */
package com.sqlapp.data.db.dialect.spanner.sql;

import java.util.List;

import com.sqlapp.data.db.sql.AbstractCreateTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.CommonUtils;

/**
 * GoogleSQL Cloud Spanner CREATE TABLE.
 */
public class SpannerCreateTableFactory extends AbstractCreateTableFactory<SpannerSqlBuilder> {

	public static final String LOCALITY_GROUP = "LOCALITY_GROUP";
	public static final String COLUMNAR_POLICY = "COLUMNAR_POLICY";
	public static final String FULLTEXT_DICTIONARY_TABLE = "FULLTEXT_DICTIONARY_TABLE";
	public static final String FULLTEXT_DICTIONARY_STALENESS = "FULLTEXT_DICTIONARY_STALENESS";
	public static final String PARENT_TABLE_NAME = "PARENT_TABLE_NAME";
	public static final String INTERLEAVE_TYPE = "INTERLEAVE_TYPE";
	public static final String ON_DELETE_ACTION = "ON_DELETE_ACTION";

	@Override
	protected void addCreateObject(final Table table, final SpannerSqlBuilder builder) {
		if (table.getName() == null || table.getName().isBlank())
			throw new IllegalArgumentException("Cloud Spanner CREATE TABLE requires a name");
		interleaveType(table); // Validate before generating any part of the statement.
		builder.create().table().ifNotExists(getOptions().isCreateIfNotExists()).space().name(table,
				getOptions().isDecorateSchemaName());
	}

	@Override
	protected void addUniqueConstraintDefinitions(final Table table, final SpannerSqlBuilder builder) {
		// GoogleSQL represents UNIQUE constraints as unique indexes.
		// They are emitted after CREATE TABLE by addOtherDefinitions().
	}

	@Override
	protected void addCreateIndexDefinition(final Table table, final Index index, final List<SqlOperation> result) {
		if (!SpannerIndexValidation.isManaged(index))
			super.addCreateIndexDefinition(table, index, result);
	}

	@Override
	protected void addOtherDefinitions(final Table table, final List<SqlOperation> result) {
		for (UniqueConstraint constraint : table.getConstraints().getUniqueConstraints()) {
			if (!constraint.isPrimaryKey()) {
				addUniqueIndex(table, constraint, result);
			}
		}
	}

	private void addUniqueIndex(final Table table, final UniqueConstraint constraint, final List<SqlOperation> result) {
		if (constraint.getName() == null || constraint.getName().isBlank() || constraint.getColumns().isEmpty()) {
			throw new IllegalArgumentException("Cloud Spanner UNIQUE constraint requires a name: " + table.getName());
		}
		final SpannerSqlBuilder builder = createSqlBuilder();
		builder.create().unique().index().ifNotExists(getOptions().isCreateIfNotExists()).space()
				.name(constraint, getOptions().isDecorateSchemaName()).on()
				.name(table, getOptions().isDecorateSchemaName()).space()._add("(");
		int i = 0;
		for (ReferenceColumn column : constraint.getColumns()) {
			builder.comma(i > 0).name(column);
			if (column.getOrder() != null) {
				builder.space()._add(column.getOrder());
			}
			i++;
		}
		builder.space()._add(")");
		add(result, createOperation(builder.toString(), SqlType.CREATE, constraint));
	}

	@Override
	protected void addOption(final Table table, final SpannerSqlBuilder builder) {
		final UniqueConstraint primaryKey = findPrimaryKey(table);
		builder.space()._add("PRIMARY KEY").space()._add("(");
		if (primaryKey != null) {
			int i = 0;
			for (ReferenceColumn column : primaryKey.getColumns()) {
				builder.comma(i > 0).name(column);
				if (column.getOrder() != null) {
					builder.space()._add(column.getOrder());
				}
				i++;
			}
		}
		builder.space()._add(")");
		addInterleave(table, builder);
		addTableOptions(table, builder);
	}

	@Override
	protected List<Table> sort(final List<Table> tables) {
		final var sorted = super.sort(tables);
		if (tables.stream().noneMatch(t -> t.getSpecifics().get(PARENT_TABLE_NAME) != null))
			return sorted;
		return SpannerTableOrder.sort(sorted);
	}

	static String interleaveType(final Table table) {
		final String parent = table.getSpecifics().get(PARENT_TABLE_NAME);
		String type = table.getSpecifics().get(INTERLEAVE_TYPE);
		final String action = table.getSpecifics().get(ON_DELETE_ACTION);
		if (parent == null || parent.isEmpty()) {
			if ((type != null && !type.isEmpty()) || (action != null && !action.isEmpty()))
				throw new IllegalArgumentException(
						"Spanner INTERLEAVE type/action requires a parent: " + table.getName());
			return null;
		}
		if (parent.isBlank() || parent.equalsIgnoreCase(table.getName()))
			throw new IllegalArgumentException(
					"Invalid Spanner INTERLEAVE parent for " + table.getName() + ": " + parent);
		if (action != null && !"CASCADE".equalsIgnoreCase(action) && !"NO ACTION".equalsIgnoreCase(action))
			throw new IllegalArgumentException("Unknown Spanner INTERLEAVE delete action: " + action);
		if (type == null || type.isEmpty()) {
			if (action == null)
				throw new IllegalArgumentException(
						"Spanner INTERLEAVE requires IN or IN PARENT when delete metadata is absent: "
								+ table.getName());
			type = "IN PARENT"; // Legacy catalog ON DELETE exists only for enforced parent-child relationships.
		}
		if (!"IN".equalsIgnoreCase(type) && !"IN PARENT".equalsIgnoreCase(type))
			throw new IllegalArgumentException("Unknown Spanner INTERLEAVE type: " + type);
		if ("IN".equalsIgnoreCase(type) && action != null)
			throw new IllegalArgumentException("ON DELETE requires INTERLEAVE IN PARENT: " + table.getName());
		return type.toUpperCase(java.util.Locale.ROOT);
	}

	private void addInterleave(final Table table, final SpannerSqlBuilder builder) {
		final String type = interleaveType(table);
		if (type == null)
			return;
		builder._add(", INTERLEAVE ")._add(type).space().name(
				new Table(table.getSpecifics().get(PARENT_TABLE_NAME)).setSchemaName(table.getSchemaName()),
				getOptions().isDecorateSchemaName());
		if ("IN PARENT".equals(type)) {
			final String action = table.getSpecifics().get(ON_DELETE_ACTION);
			builder._add(" ON DELETE ")._add(action == null ? "NO ACTION" : action.toUpperCase(java.util.Locale.ROOT));
		}
	}

	private void addTableOptions(final Table table, final SpannerSqlBuilder builder) {
		final String localityGroup = table.getSpecifics().get(LOCALITY_GROUP);
		final String columnarPolicy = table.getSpecifics().get(COLUMNAR_POLICY);
		final Boolean dictionary = table.getSpecifics().get(FULLTEXT_DICTIONARY_TABLE, Boolean.class);
		final String staleness = table.getSpecifics().get(FULLTEXT_DICTIONARY_STALENESS);
		if (CommonUtils.isEmpty(localityGroup) && CommonUtils.isEmpty(columnarPolicy) && dictionary == null
				&& CommonUtils.isEmpty(staleness)) {
			return;
		}
		builder._add(", OPTIONS").space()._add("(");
		int count = 0;
		if (!CommonUtils.isEmpty(localityGroup)) {
			builder.comma(count > 0)._add("locality_group = ").sqlChar(localityGroup);
			count++;
		}
		if (!CommonUtils.isEmpty(columnarPolicy)) {
			builder.comma(count > 0)._add("columnar_policy = ").sqlChar(columnarPolicy);
			count++;
		}
		if (dictionary != null) {
			builder.comma(count > 0)._add("fulltext_dictionary_table = ")._add(dictionary.booleanValue());
			count++;
		}
		if (!CommonUtils.isEmpty(staleness)) {
			builder.comma(count > 0)._add("fulltext_dictionary_staleness = ").sqlChar(staleness);
		}
		builder.space()._add(")");
	}

	private UniqueConstraint findPrimaryKey(final Table table) {
		for (UniqueConstraint constraint : table.getConstraints().getUniqueConstraints()) {
			if (constraint.isPrimaryKey()) {
				return constraint;
			}
		}
		return null;
	}
}
