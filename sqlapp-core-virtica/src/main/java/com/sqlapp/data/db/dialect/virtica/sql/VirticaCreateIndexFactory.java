/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.virtica.sql;

import com.sqlapp.data.db.dialect.virtica.util.VirticaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateIndexFactory;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Table;

/**
 * Creates text indices with explicitly supplied ID, text and optional payload
 * columns.
 */
public class VirticaCreateIndexFactory extends AbstractCreateIndexFactory<VirticaSqlBuilder> {
	@Override
	public void addCreateObject(Index index, VirticaSqlBuilder builder) {
		if (index.getDefinition() != null && !index.getDefinition().isEmpty()) {
			if (index.getIndexType() != IndexType.FullText)
				throw new IllegalArgumentException(
						"Vertica index definitions require a text index: " + index.getName());
			if (index.getDefinition().stream().allMatch(s -> s == null || s.isBlank()))
				throw new IllegalArgumentException(
						"Vertica text index CREATE definition must not be blank: " + index.getName());
			builder._add(index.getDefinition());
			return;
		}
		super.addCreateObject(index, builder);
	}

	@Override
	protected boolean createIndex(Index index) {
		return true;
	}

	@Override
	public void addObjectDetail(Index index, Table table, VirticaSqlBuilder builder) {
		if (index.getIndexType() != IndexType.FullText || index.isUnique())
			throw new IllegalArgumentException(
					"Vertica supports CREATE TEXT INDEX, not ordinary/unique indexes: " + index.getName());
		if (index.getColumns().size() < 2)
			throw new IllegalArgumentException(
					"Vertica text index requires ID column first and text column second; TEXT_INDICES exposes only TEXT_COL: "
							+ index.getName());
		if (table == null || table.getName() == null)
			throw new IllegalArgumentException(
					"Vertica text index requires an attached source table: " + index.getName());
		for (String key : new String[] { "TOKENIZER_NAME", "STEMMER_NAME" }) {
			String value = index.getSpecifics().get(key, String.class);
			if (value != null && !value.isBlank())
				throw new IllegalArgumentException("Vertica text index " + key
						+ " requires a function input signature unavailable in TEXT_INDICES; supply vendor DDL separately: "
						+ index.getName());
		}
		if (!index.getIncludes().isEmpty() || index.getWhere() != null)
			throw new IllegalArgumentException(
					"Vertica text index payload columns belong after the text column; INCLUDE/WHERE are unsupported: "
							+ index.getName());
		builder.space()._add("TEXT INDEX ");
		String schema = index.getSpecifics().get("INDEX_SCHEMA_NAME", String.class);
		if (schema == null)
			schema = index.getSchemaName();
		if (schema != null)
			builder.name(schema)._add(".");
		builder.name(index.getName()).on().name(table, true).space()._add("(");
		for (int i = 0; i < index.getColumns().size(); i++) {
			var column = index.getColumns().get(i);
			if (column.getName() == null || column.getName().isBlank()
					|| (column.getOrder() != null && column.getOrder() != com.sqlapp.data.schemas.Order.Asc))
				throw new IllegalArgumentException(
						"Vertica text index requires column names without ordering: " + index.getName());
			builder.comma(i > 0).name(column.getName());
		}
		builder._add(")");
	}
}
