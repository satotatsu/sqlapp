/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.vertica.sql;

import com.sqlapp.data.db.dialect.vertica.util.VerticaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTableFactory;
import com.sqlapp.data.schemas.Table;

/**
 * Vertica CREATE TABLE.
 */
public class VerticaCreateTableFactory extends AbstractCreateTableFactory<VerticaSqlBuilder> {
	@Override
	protected void addCreateIndexDefinition(com.sqlapp.data.schemas.Index index, VerticaSqlBuilder builder) {
		if (index.getDefinition() != null && !index.getDefinition().isEmpty()) {
			builder._add(getSqlFactoryRegistry().createSql(index, com.sqlapp.data.db.sql.SqlType.CREATE).get(0)
					.getSqlText());
			return;
		}
		super.addCreateIndexDefinition(index, builder);
	}

	/**
	 * A complete CREATE definition is authoritative; catalog components are
	 * supplemental.
	 */
	@Override
	public java.util.List<com.sqlapp.data.db.sql.SqlOperation> createSql(Table table) {
		if (table.getDefinition() == null || table.getDefinition().isEmpty())
			return super.createSql(table);
		if (table.getDefinition().stream().allMatch(s -> s == null || s.isBlank()))
			throw new IllegalArgumentException("Vertica table CREATE definition must not be blank: " + table.getName());
		if (table.getTableType() == Table.TableType.Temporary
				|| Boolean.TRUE.equals(table.getSpecifics().get("IS_TEMP_TABLE", Boolean.class)))
			throw new IllegalArgumentException(
					"Vertica temporary definitions require explicit session semantics: " + table.getName());
		var result = new java.util.ArrayList<com.sqlapp.data.db.sql.SqlOperation>();
		var builder = createSqlBuilder();
		builder._add(table.getDefinition());
		addSql(result, builder, com.sqlapp.data.db.sql.SqlType.CREATE, table);
		return result;
	}

	@Override
	protected void addCreateObject(final Table table, final VerticaSqlBuilder builder) {
		if (table.getTableType() == Table.TableType.Temporary
				|| Boolean.TRUE.equals(table.getSpecifics().get("IS_TEMP_TABLE", Boolean.class)))
			throw new IllegalArgumentException(
					"Vertica temporary tables require CREATE_TEMPORARY with explicit session semantics: "
							+ table.getName());
		String external = table.getSpecifics().get("TABLE_DEFINITION", String.class);
		if (external != null && !external.isBlank())
			throw new IllegalArgumentException(
					"Vertica external table COPY definition requires vendor DDL; ordinary CREATE TABLE would lose it: "
							+ table.getName());
		builder.create();
		if (table.getTableType() == Table.TableType.Flex
				|| Boolean.TRUE.equals(table.getSpecifics().get("IS_FLEXTABLE", Boolean.class))) {
			if (table.getColumns().contains("__raw__") || table.getColumns().contains("__identity__"))
				throw new IllegalArgumentException(
						"Vertica Flex automatic columns require vendor DDL for faithful recreation: "
								+ table.getName());
			builder.space()._add("FLEX");
		}
		builder.table().ifNotExists(getOptions().isCreateIfNotExists()).space().name(table,
				getOptions().isDecorateSchemaName());
	}

	@Override
	protected void addOption(final Table table, final VerticaSqlBuilder builder) {
		String expression = table.getSpecifics().get("PARTITION_EXPRESSION", String.class);
		if (expression != null && !expression.isBlank())
			builder.lineBreak()._add("PARTITION BY ")._add(expression);
	}
}
