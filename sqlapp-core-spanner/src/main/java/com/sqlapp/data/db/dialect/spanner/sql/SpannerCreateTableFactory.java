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
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.CommonUtils;

/**
 * GoogleSQL Cloud Spanner CREATE TABLE.
 */
public class SpannerCreateTableFactory
		extends AbstractCreateTableFactory<SpannerSqlBuilder> {

	public static final String LOCALITY_GROUP = "LOCALITY_GROUP";
	public static final String COLUMNAR_POLICY = "COLUMNAR_POLICY";
	public static final String FULLTEXT_DICTIONARY_TABLE =
			"FULLTEXT_DICTIONARY_TABLE";
	public static final String FULLTEXT_DICTIONARY_STALENESS =
			"FULLTEXT_DICTIONARY_STALENESS";

	@Override
	protected void addCreateObject(final Table table,
			final SpannerSqlBuilder builder) {
		builder.create().table()
				.ifNotExists(getOptions().isCreateIfNotExists()).space()
				.name(table, getOptions().isDecorateSchemaName());
	}

	@Override
	protected void addUniqueConstraintDefinitions(final Table table,
			final SpannerSqlBuilder builder) {
		// GoogleSQL represents UNIQUE constraints as unique indexes.
		// They are emitted after CREATE TABLE by addOtherDefinitions().
	}

	@Override
	protected void addOtherDefinitions(final Table table,
			final List<SqlOperation> result) {
		for (UniqueConstraint constraint
				: table.getConstraints().getUniqueConstraints()) {
			if (!constraint.isPrimaryKey()) {
				addUniqueIndex(table, constraint, result);
			}
		}
	}

	private void addUniqueIndex(final Table table,
			final UniqueConstraint constraint,
			final List<SqlOperation> result) {
		if (CommonUtils.isEmpty(constraint.getName())) {
			throw new IllegalArgumentException(
					"Cloud Spanner UNIQUE constraint requires a name: "
							+ table.getName());
		}
		final SpannerSqlBuilder builder = createSqlBuilder();
		builder.create().unique().index()
				.ifNotExists(getOptions().isCreateIfNotExists()).space()
				.name(constraint, false).on().name(table,
						getOptions().isDecorateSchemaName())
				.space()._add("(");
		int i = 0;
		for (ReferenceColumn column : constraint.getColumns()) {
			builder.comma(i > 0).name(column);
			if (column.getOrder() != null) {
				builder.space()._add(column.getOrder());
			}
			i++;
		}
		builder.space()._add(")");
		add(result, createOperation(builder.toString(), SqlType.CREATE,
				constraint));
	}

	@Override
	protected void addOption(final Table table,
			final SpannerSqlBuilder builder) {
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
		addTableOptions(table, builder);
	}

	private void addTableOptions(final Table table,
			final SpannerSqlBuilder builder) {
		final String localityGroup = table.getSpecifics().get(
				LOCALITY_GROUP);
		final String columnarPolicy = table.getSpecifics().get(
				COLUMNAR_POLICY);
		final Boolean dictionary = table.getSpecifics().get(
				FULLTEXT_DICTIONARY_TABLE, Boolean.class);
		final String staleness = table.getSpecifics().get(
				FULLTEXT_DICTIONARY_STALENESS);
		if (CommonUtils.isEmpty(localityGroup)
				&& CommonUtils.isEmpty(columnarPolicy)
				&& dictionary == null
				&& CommonUtils.isEmpty(staleness)) {
			return;
		}
		builder.space()._add("OPTIONS").space()._add("(");
		int count = 0;
		if (!CommonUtils.isEmpty(localityGroup)) {
			builder.comma(count > 0)._add("locality_group = ")
					.sqlChar(localityGroup);
			count++;
		}
		if (!CommonUtils.isEmpty(columnarPolicy)) {
			builder.comma(count > 0)._add("columnar_policy = ")
					.sqlChar(columnarPolicy);
			count++;
		}
		if (dictionary != null) {
			builder.comma(count > 0)._add(
					"fulltext_dictionary_table = ")
					._add(dictionary.booleanValue());
			count++;
		}
		if (!CommonUtils.isEmpty(staleness)) {
			builder.comma(count > 0)._add(
					"fulltext_dictionary_staleness = ")
					.sqlChar(staleness);
		}
		builder.space()._add(")");
	}

	private UniqueConstraint findPrimaryKey(final Table table) {
		for (UniqueConstraint constraint
				: table.getConstraints().getUniqueConstraints()) {
			if (constraint.isPrimaryKey()) {
				return constraint;
			}
		}
		return null;
	}
}
