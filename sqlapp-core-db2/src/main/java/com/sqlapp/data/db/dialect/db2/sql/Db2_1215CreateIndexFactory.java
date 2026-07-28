/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 */
package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.CreateIndexFactory;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.VectorDistanceType;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * CREATE INDEX factory with VECTOR INDEX support introduced in Db2 LUW 12.1.5.
 */
public class Db2_1215CreateIndexFactory extends CreateIndexFactory {

	public static final String COMPRESSED_VECTORS_TABLE_SPACE_NAME = "COMPRESSED_VECTORS_TABLE_SPACE_NAME";
	public static final String EXCLUDE_NULL_KEYS = "EXCLUDE_NULL_KEYS";
	public static final String BUILD_MEM_BUDGET = "BUILD_MEM_BUDGET";
	public static final String BUILD_LIST_SIZE = "BUILD_LIST_SIZE";
	public static final String BUILD_PARALLELISM = "BUILD_PARALLELISM";
	public static final String MAX_NODE_DEGREE = "MAX_NODE_DEGREE";
	public static final String PCT_COMP_VECT_SIZE = "PCT_COMP_VECT_SIZE";
	public static final String PCT_NODES_TO_CACHE = "PCT_NODES_TO_CACHE";

	@Override
	public void addObjectDetail(final Index index, final Table table,
			final AbstractSqlBuilder<?> builder) {
		if (index.getIndexType() != IndexType.Vector) {
			super.addObjectDetail(index, table, builder);
			return;
		}
		validateVectorIndex(index, table);
		builder.space()._add("VECTOR").index().space().name(index, false).on();
		if (index.getSchemaName() != null && table.getSchemaName() != null
				&& !CommonUtils.eq(index.getSchemaName(), table.getSchemaName())) {
			builder.name(table, true);
		} else {
			builder.name(table, false);
		}
		builder.space()._add("(").name(index.getColumns().get(0)).space()._add(")");
		builder.space()._add("WITH DISTANCE").space()._add(index.getVectorDistanceType().getSqlValue());
		if (index.getTableSpaceName() != null) {
			builder.space()._add("IN").space().name(index.getTableSpaceName());
		}
		addCompressedVectorsTableSpace(index, builder);
		if (Boolean.TRUE.equals(index.getSpecifics().get(EXCLUDE_NULL_KEYS, Boolean.class))) {
			builder.space()._add("EXCLUDE NULL KEYS");
		}
		addIntegerOption(index, builder, BUILD_MEM_BUDGET, 1, 255);
		addIntegerOption(index, builder, BUILD_LIST_SIZE, 1, 200);
		addIntegerOption(index, builder, BUILD_PARALLELISM, 1, 64);
		addIntegerOption(index, builder, MAX_NODE_DEGREE, 32, 128);
		addIntegerOption(index, builder, PCT_COMP_VECT_SIZE, 1, 75);
		addIntegerOption(index, builder, PCT_NODES_TO_CACHE, 0, 100);
	}

	private void validateVectorIndex(final Index index, final Table table) {
		if (table == null) {
			throw new IllegalArgumentException("VECTOR index requires a parent table: " + index.getName());
		}
		if (index.getColumns().size() != 1) {
			throw new IllegalArgumentException("VECTOR index requires exactly one column: " + index.getName());
		}
		if (index.getVectorDistanceType() == null) {
			throw new IllegalArgumentException("VECTOR index requires a distance type: " + index.getName());
		}
		final ReferenceColumn reference = index.getColumns().get(0);
		final Column column = table.getColumns().get(reference.getName());
		if (column == null || column.getDataType() != DataType.VECTOR) {
			throw new IllegalArgumentException("VECTOR index column must have VECTOR data type: "
					+ reference.getName());
		}
		if (index.getVectorDistanceType() == VectorDistanceType.Cosine
				&& column.getVectorElementDataType() != DataType.REAL) {
			throw new IllegalArgumentException("COSINE distance requires a FLOAT32 VECTOR column: "
					+ reference.getName());
		}
	}

	private void addCompressedVectorsTableSpace(final Index index,
			final AbstractSqlBuilder<?> builder) {
		final String value = index.getSpecifics().get(COMPRESSED_VECTORS_TABLE_SPACE_NAME);
		if (value != null) {
			builder.space()._add("COMPRESSED VECTORS IN").space().name(value);
		}
	}

	private void addIntegerOption(final Index index, final AbstractSqlBuilder<?> builder,
			final String name, final int minimum, final int maximum) {
		final Integer value = index.getSpecifics().get(name, Integer.class);
		if (value == null) {
			return;
		}
		if (value < minimum || value > maximum) {
			throw new IllegalArgumentException(name + " must be between " + minimum
					+ " and " + maximum + ": " + value);
		}
		builder.space()._add(name).space()._add(value);
	}
}
