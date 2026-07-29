/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-spanner.
 */
package com.sqlapp.data.db.dialect.spanner.sql;

import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.AbstractCreateIndexFactory;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.VectorDistanceType;

/**
 * GoogleSQL Cloud Spanner CREATE INDEX.
 */
public class SpannerCreateIndexFactory
		extends AbstractCreateIndexFactory<SpannerSqlBuilder> {

	public static final String IS_NULL_FILTERED = "IS_NULL_FILTERED";
	public static final String TREE_DEPTH = "TREE_DEPTH";
	public static final String NUM_LEAVES = "NUM_LEAVES";
	public static final String NUM_BRANCHES = "NUM_BRANCHES";
	public static final String DISABLE_SEARCH = "DISABLE_SEARCH";
	public static final String SORT_ORDER_SHARDING = "SORT_ORDER_SHARDING";

	@Override
	public void addObjectDetail(final Index index, final Table table,
			final SpannerSqlBuilder builder) {
		if (index.getIndexType() == IndexType.Vector) {
			addVectorIndex(index, table, builder);
			return;
		}
		if (index.getIndexType() == IndexType.FullText) {
			addSearchIndex(index, table, builder);
			return;
		}
		builder.unique(index.isUnique());
		if (Boolean.TRUE.equals(index.getSpecifics().get(IS_NULL_FILTERED,
				Boolean.class))) {
			if (index.isUnique()) {
				builder.space();
			}
			builder._add("NULL_FILTERED").space();
		}
		builder.index()
				.ifNotExists(table != null
						&& getOptions().isCreateIfNotExists())
				.space().name(index, false);
		if (table != null) {
			builder.on().name(table,
					getOptions().isDecorateSchemaName());
		}
		builder.space()._add("(");
		int i = 0;
		for (ReferenceColumn column : index.getColumns()) {
			builder.comma(i > 0).name(column);
			if (column.getOrder() != null
					&& column.getOrder() != Order.Asc) {
				builder.space()._add(column.getOrder());
			}
			i++;
		}
		builder.space()._add(")");
		if (!index.getIncludes().isEmpty()) {
			builder.space()._add("STORING").space()._add("(");
			i = 0;
			for (ReferenceColumn column : index.getIncludes()) {
				builder.comma(i > 0).name(column);
				i++;
			}
			builder.space()._add(")");
		}
	}

	private void addVectorIndex(final Index index, final Table table,
			final SpannerSqlBuilder builder) {
		validateVectorIndex(index, table);
		builder.space()._add("VECTOR").space().index()
				.ifNotExists(getOptions().isCreateIfNotExists())
				.space().name(index, false).on()
				.name(table, getOptions().isDecorateSchemaName())
				.space()._add("(");
		int i = 0;
		for (ReferenceColumn column : index.getColumns()) {
			builder.comma(i > 0).name(column);
			i++;
		}
		builder.space()._add(")");
		if (!index.getIncludes().isEmpty()) {
			builder.space()._add("STORING").space()._add("(");
			i = 0;
			for (ReferenceColumn column : index.getIncludes()) {
				builder.comma(i > 0).name(column);
				i++;
			}
			builder.space()._add(")");
		}
		if (index.getWhere() != null && !index.getWhere().isBlank()) {
			builder.space().where().space()._add(index.getWhere());
		}
		builder.space()._add("OPTIONS")._add("(")
				._add("distance_type = ")
				.sqlChar(toSpannerDistance(index.getVectorDistanceType()));
		addIntegerOption(index, builder, TREE_DEPTH);
		addIntegerOption(index, builder, NUM_LEAVES);
		addIntegerOption(index, builder, NUM_BRANCHES);
		final Boolean disableSearch = index.getSpecifics().get(
				DISABLE_SEARCH, Boolean.class);
		if (disableSearch != null) {
			builder.comma()._add("disable_search = ")
					._add(disableSearch.booleanValue());
		}
		builder._add(")");
	}

	private void addSearchIndex(final Index index, final Table table,
			final SpannerSqlBuilder builder) {
		validateSearchIndex(index, table);
		builder.space()._add("SEARCH").space().index()
				.space().name(index, false).on()
				.name(table, getOptions().isDecorateSchemaName())
				.space()._add("(");
		int i = 0;
		for (ReferenceColumn column : index.getColumns()) {
			builder.comma(i > 0).name(column);
			i++;
		}
		builder.space()._add(")");
		if (!index.getIncludes().isEmpty()) {
			builder.space()._add("STORING").space()._add("(");
			i = 0;
			for (ReferenceColumn column : index.getIncludes()) {
				builder.comma(i > 0).name(column);
				i++;
			}
			builder.space()._add(")");
		}
		if (index.getWhere() != null && !index.getWhere().isBlank()) {
			builder.space().where().space()._add(index.getWhere());
		}
		final Boolean sortOrderSharding = index.getSpecifics().get(
				SORT_ORDER_SHARDING, Boolean.class);
		if (sortOrderSharding != null) {
			builder.space()._add("OPTIONS").space()._add("(")
					._add("sort_order_sharding = ")
					._add(sortOrderSharding.booleanValue())
					.space()._add(")");
		}
	}

	private void validateSearchIndex(final Index index, final Table table) {
		if (table == null) {
			throw new IllegalArgumentException(
					"Cloud Spanner SEARCH index requires a parent table: "
							+ index.getName());
		}
		if (index.getColumns().isEmpty()) {
			throw new IllegalArgumentException(
					"Cloud Spanner SEARCH index requires a TOKENLIST column: "
							+ index.getName());
		}
		for (ReferenceColumn reference : index.getColumns()) {
			final Column column = table.getColumns().get(reference.getName());
			if (column == null || column.getDataType() != DataType.OTHER
					|| !"TOKENLIST".equalsIgnoreCase(
							column.getDataTypeName())) {
				throw new IllegalArgumentException(
						"Cloud Spanner SEARCH index columns must have "
								+ "TOKENLIST data type: "
								+ reference.getName());
			}
		}
	}

	private void validateVectorIndex(final Index index, final Table table) {
		if (table == null) {
			throw new IllegalArgumentException(
					"Cloud Spanner VECTOR index requires a parent table: "
							+ index.getName());
		}
		if (index.getColumns().isEmpty()) {
			throw new IllegalArgumentException(
					"Cloud Spanner VECTOR index requires an embedding column: "
							+ index.getName());
		}
		final ReferenceColumn reference = index.getColumns().get(0);
		final Column column = table.getColumns().get(reference.getName());
		if (column == null || column.getArrayDimension() != 1
				|| (column.getDataType() != DataType.REAL
						&& column.getDataType() != DataType.DOUBLE)
				|| column.getSpecifics().get(
						SpannerSqlBuilder.VECTOR_LENGTH,
						Integer.class) == null) {
			throw new IllegalArgumentException(
					"Cloud Spanner VECTOR index requires a fixed-length "
							+ "FLOAT32 or FLOAT64 array as its first column: "
							+ reference.getName());
		}
		toSpannerDistance(index.getVectorDistanceType());
		final Integer treeDepth = integerOption(index, TREE_DEPTH);
		final Integer leaves = integerOption(index, NUM_LEAVES);
		final Integer branches = integerOption(index, NUM_BRANCHES);
		if (treeDepth != null && treeDepth != 2 && treeDepth != 3) {
			throw new IllegalArgumentException(
					"TREE_DEPTH must be 2 or 3: " + treeDepth);
		}
		if (leaves != null && leaves <= 0) {
			throw new IllegalArgumentException(
					"NUM_LEAVES must be positive: " + leaves);
		}
		if (branches != null
				&& (treeDepth == null || treeDepth != 3)) {
			throw new IllegalArgumentException(
					"NUM_BRANCHES requires TREE_DEPTH 3: " + branches);
		}
		if (branches != null && branches <= 0) {
			throw new IllegalArgumentException(
					"NUM_BRANCHES must be positive: " + branches);
		}
		if (branches != null && leaves != null && branches >= leaves) {
			throw new IllegalArgumentException(
					"NUM_BRANCHES must be less than NUM_LEAVES: "
							+ branches);
		}
	}

	private String toSpannerDistance(final VectorDistanceType type) {
		if (type == VectorDistanceType.Cosine) {
			return "COSINE";
		}
		if (type == VectorDistanceType.DotProduct) {
			return "DOT_PRODUCT";
		}
		if (type == VectorDistanceType.Euclidean) {
			return "EUCLIDEAN";
		}
		throw new IllegalArgumentException(
				"Cloud Spanner VECTOR index requires COSINE, DOT_PRODUCT "
						+ "or EUCLIDEAN distance: " + type);
	}

	private void addIntegerOption(final Index index,
			final SpannerSqlBuilder builder, final String name) {
		final Integer value = integerOption(index, name);
		if (value != null) {
			builder.comma()._add(name.toLowerCase())._add(" = ")
					._add(value);
		}
	}

	private Integer integerOption(final Index index, final String name) {
		return index.getSpecifics().get(name, Integer.class);
	}
}
