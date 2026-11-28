/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import com.sqlapp.data.db.sql.AbstractCreateIndexFactory;
import com.sqlapp.data.db.dialect.phoenix.util.PhoenixSqlBuilder;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.IndexType;

public class PhoenixCreateIndexFactory extends AbstractCreateIndexFactory<PhoenixSqlBuilder> {
	/** Catalog key names alone cannot reconstruct the original index definition. */
	public static final String NATIVE_KEY_METADATA_ONLY = "phoenix.nativeKeyMetadataOnly";
	/** Complete, user-supplied Phoenix CREATE INDEX statement. */
	public static final String NATIVE_DEFINITION = "phoenix.indexDefinition";

	@Override
	protected boolean createIndex(Index index) {
		if (index.getSpecifics().get(NATIVE_DEFINITION) == null)
			validate(index, index.getTable());
		else if (index.getTable() == null || index.getName() == null || index.getName().isBlank())
			throw new IllegalArgumentException("Phoenix native index DDL requires an index name and owning table/view");
		return super.createIndex(index);
	}

	@Override
	public void addCreateObject(Index index, PhoenixSqlBuilder builder) {
		final String definition = index.getSpecifics().get(NATIVE_DEFINITION);
		if (definition == null) {
			super.addCreateObject(index, builder);
			return;
		}
		if (definition.isBlank())
			throw new IllegalArgumentException("Phoenix native index definition must not be blank: " + index.getName());
		builder._add(definition);
	}

	static boolean hasOpaqueDependencies(Index index) {
		return index.getSpecifics().get(NATIVE_DEFINITION) != null
				|| Boolean.parseBoolean(index.getSpecifics().get(NATIVE_KEY_METADATA_ONLY))
				|| index.getColumns().stream().anyMatch(column -> expression(index, column));
	}

	private void validate(Index index, Table table) {
		if (Boolean.parseBoolean(index.getSpecifics().get(NATIVE_KEY_METADATA_ONLY)))
			throw new UnsupportedOperationException(
					"Phoenix index contains catalog-only key names; provide a complete native definition: "
							+ index.getName());
		if (table == null || index.getName() == null || index.getName().isBlank() || index.getColumns().isEmpty())
			throw new IllegalArgumentException(
					"Phoenix CREATE INDEX requires a name, parent table and key columns: " + index.getName());
		if ((index.getIndexType() != null && index.getIndexType() != IndexType.BTree
				&& index.getIndexType() != IndexType.Function)
				|| (index.getWhere() != null && !index.getWhere().isBlank()))
			throw new UnsupportedOperationException(
					"Phoenix modeled index type/predicate cannot be preserved by this factory: " + index.getName());
		for (var key : index.getColumns()) {
			if (key.getLength() != null || key.getNullsOrder() != null)
				throw new UnsupportedOperationException(
						"Phoenix CREATE INDEX cannot preserve key length or NULL ordering: " + key.getName());
		}
		if (index.getIndexType() == IndexType.Function) {
			if (!(getDialect() instanceof com.sqlapp.data.db.dialect.phoenix.Phoenix phoenix)
					|| !phoenix.supportsFunctionalIndexes())
				throw new UnsupportedOperationException("Phoenix functional indexes require version 4.3 or later");
			for (var column : index.getColumns()) {
				if (column.getName() == null || column.getName().isBlank())
					throw new IllegalArgumentException("Phoenix functional index keys must not be blank");
				if (expression(index, column)
						&& column.getSpecifics().get(PhoenixCreateTableFactory.COLUMN_FAMILY) != null)
					throw new IllegalArgumentException(
							"Put family-qualified identifiers inside the Phoenix index expression");
			}
		}
	}

	@Override
	public void addObjectDetail(Index index, Table table, PhoenixSqlBuilder builder) {
		validate(index, table);
		if (index.isUnique())
			throw new IllegalArgumentException("Phoenix secondary indexes do not enforce UNIQUE: " + index.getName());
		final String type = index.getSpecifics().get("phoenix.indexType");
		if (type != null && !type.equalsIgnoreCase("GLOBAL") && !type.equalsIgnoreCase("LOCAL"))
			throw new IllegalArgumentException("Unknown Phoenix index type: " + type);
		final String state = index.getSpecifics().get("phoenix.indexState");
		if (state != null && !state.equalsIgnoreCase("a") && !state.equalsIgnoreCase("ACTIVE"))
			throw new UnsupportedOperationException(
					"Recreating a non-active Phoenix index requires a separate state transition: " + index.getName());
		if ("LOCAL".equalsIgnoreCase(type)) {
			if (index.getSpecifics().get(PhoenixDdlOptions.SALT_BUCKETS) != null)
				throw new IllegalArgumentException(
						"Phoenix LOCAL index salting cannot be declared separately: " + index.getName());
			builder.space()._add("LOCAL");
		}
		builder.index().ifNotExists(getOptions().isCreateIfNotExists()).name(index, false).on()
				.name(table, getOptions().isDecorateSchemaName()).space()._add("(");
		int i = 0;
		for (var column : index.getColumns()) {
			builder.comma(i++ > 0);
			if (expression(index, column))
				builder._add(column.getName());
			else
				appendColumnName(column, table, builder);
			if (column.getOrder() != null && column.getOrder() != Order.Asc)
				builder.space()._add(column.getOrder());
		}
		builder._add(")");
		if (!index.getIncludes().isEmpty()) {
			builder.space()._add("INCLUDE (");
			i = 0;
			for (var column : index.getIncludes()) {
				builder.comma(i++ > 0);
				appendColumnName(column, table, builder);
			}
			builder._add(")");
		}
		PhoenixDdlOptions.append(index.getSpecifics(), true, builder);
	}

	static boolean expression(Index index, ReferenceColumn column) {
		return index.getIndexType() == IndexType.Function && column.getColumn() == null;
	}

	private void appendColumnName(ReferenceColumn reference, Table table, PhoenixSqlBuilder builder) {
		if (reference.getName() == null || reference.getName().isBlank())
			throw new IllegalArgumentException("Phoenix index column names must not be blank");
		final String family = reference.getSpecifics().get(PhoenixCreateTableFactory.COLUMN_FAMILY);
		if (family != null) {
			if (family.isBlank())
				throw new IllegalArgumentException(
						"Phoenix index column family must not be blank: " + reference.getName());
			builder.name(family)._add(".").name(reference);
			return;
		}
		final var column = table.getColumns().get(reference.getName());
		if (column != null)
			PhoenixCreateTableFactory.appendColumnName(column, builder);
		else
			builder.name(reference);
	}
}
