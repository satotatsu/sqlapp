/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-saphana.
 */
package com.sqlapp.data.db.dialect.saphana.sql;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.AbstractCreateIndexFactory;
import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.VectorDistanceType;

/**
 * SAP HANA CREATE INDEX including HNSW vector indexes.
 */
public class SapHanaCreateIndexFactory
		extends AbstractCreateIndexFactory<SapHanaSqlBuilder> {

	public static final String BUILD_CONFIGURATION = "BUILD_CONFIGURATION";
	public static final String SEARCH_CONFIGURATION = "SEARCH_CONFIGURATION";
	public static final String ONLINE = "ONLINE";

	@Override
	public void addObjectDetail(final Index index, final Table table,
			final SapHanaSqlBuilder builder) {
		if (index.getIndexType() != IndexType.Vector) {
			super.addObjectDetail(index, table, builder);
			return;
		}
		validate(index, table);
		builder.space()._add("HNSW VECTOR INDEX").space()
				.name(index, false).on().name(table, false).space()
				._add("(").name(index.getColumns().get(0)).space()._add(")")
				.space()._add("SIMILARITY FUNCTION").space()
				._add(similarityFunction(index.getVectorDistanceType()));
		addConfiguration(index, BUILD_CONFIGURATION, builder);
		addConfiguration(index, SEARCH_CONFIGURATION, builder);
		if (Boolean.TRUE.equals(index.getSpecifics().get(ONLINE,
				Boolean.class))) {
			builder.space()._add("ONLINE");
		}
	}

	private void validate(final Index index, final Table table) {
		if (table == null || index.getColumns().size() != 1) {
			throw new IllegalArgumentException(
					"SAP HANA VECTOR index requires one table column: "
					+ index.getName());
		}
		final Column column = table.getColumns().get(
				index.getColumns().get(0).getName());
		if (column == null || column.getDataType() != DataType.VECTOR) {
			throw new IllegalArgumentException(
					"SAP HANA VECTOR index requires a VECTOR column: "
					+ index.getName());
		}
		if (index.getVectorDistanceType() != VectorDistanceType.Cosine
				&& index.getVectorDistanceType()
						!= VectorDistanceType.Euclidean) {
			throw new IllegalArgumentException(
					"SAP HANA VECTOR index similarity must be Cosine "
					+ "or Euclidean: " + index.getName());
		}
	}

	private String similarityFunction(final VectorDistanceType type) {
		return type == VectorDistanceType.Cosine
				? "COSINE_SIMILARITY" : "L2DISTANCE";
	}

	private void addConfiguration(final Index index, final String key,
			final SapHanaSqlBuilder builder) {
		final String value = index.getSpecifics().get(key);
		if (value != null && !value.isBlank()) {
			builder.space()._add(key.replace('_', ' ')).space()
					.sqlChar(value);
		}
	}
}
