/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb.util;

import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Table;

import io.github.spannm.jackcess.Relationship;
import io.github.spannm.jackcess.RelationshipBuilder;

/**
 * Internal mapping shared by JDBC metadata, file loading and direct writers.
 */
public final class MdbRelationshipSupport {

	private MdbRelationshipSupport() {
	}

	public static boolean isRelationshipIndex(final io.github.spannm.jackcess.Index index) {
		// These are recreated from relationships, including hidden .rB indexes.
		// Creating them separately also makes Jackcess rename the relationship.
		return index.isForeignKey();
	}

	public static ForeignKeyConstraint toForeignKey(final Relationship source) {
		final ForeignKeyConstraint key = new ForeignKeyConstraint(source.getName());
		key.setTableName(source.getToTable().getName());
		final Table child = new Table(source.getToTable().getName());
		final Table parent = new Table(source.getFromTable().getName());
		for (int i = 0; i < source.getToColumns().size(); i++) {
			final Column childColumn = new Column(source.getToColumns().get(i).getName());
			child.getColumns().add(childColumn);
			key.getColumns().add(childColumn);
			final Column column = new Column(source.getFromColumns().get(i).getName());
			parent.getColumns().add(column);
			key.addRelatedColumn(column);
		}
		key.setEnable(source.hasReferentialIntegrity());
		key.setUpdateRule(source.cascadeUpdates() ? CascadeRule.Cascade : CascadeRule.None);
		key.setDeleteRule(source.cascadeNullOnDelete() ? CascadeRule.SetNull
				: source.cascadeDeletes() ? CascadeRule.Cascade : CascadeRule.None);
		return key;
	}

	public static RelationshipBuilder toBuilder(final ForeignKeyConstraint key) {
		if (key.getUpdateRule() == CascadeRule.SetNull || key.getUpdateRule() == CascadeRule.SetDefault
				|| key.getDeleteRule() == CascadeRule.SetDefault) {
			throw new IllegalArgumentException("Access relationship " + key.getName()
					+ " does not support ON UPDATE SET NULL/SET DEFAULT or ON DELETE SET DEFAULT");
		}
		final RelationshipBuilder builder = new RelationshipBuilder(key.getRelatedTableName(), key.getTable().getName())
				.withName(key.getName());
		if (key.isEnable()) {
			builder.withReferentialIntegrity();
		}
		for (int i = 0; i < key.getColumns().size(); i++) {
			builder.addColumns(key.getRelatedColumns().get(i).getName(), key.getColumns().get(i).getName());
		}
		if (key.getUpdateRule() == CascadeRule.Cascade) {
			builder.withCascadeUpdates();
		}
		if (key.getDeleteRule() == CascadeRule.SetNull) {
			builder.withCascadeNullOnDelete();
		} else if (key.getDeleteRule() == CascadeRule.Cascade) {
			builder.withCascadeDeletes();
		}
		return builder;
	}

	public static void bindColumns(final ForeignKeyConstraint key, final Table child, final Table parent) {
		key.setColumns(
				key.getColumns().stream().map(column -> resolveColumn(child, column.getName())).toArray(Column[]::new));
		if (parent != null) {
			key.setRelatedColumns(key.getRelatedColumns().stream()
					.map(column -> resolveColumn(parent, column.getName())).toArray(Column[]::new));
		}
	}

	private static Column resolveColumn(final Table table, final String name) {
		final Column column = table.getColumns().get(name);
		if (column == null) {
			throw new IllegalStateException("Access relationship column not found: " + table.getName() + "." + name);
		}
		return column;
	}
}
