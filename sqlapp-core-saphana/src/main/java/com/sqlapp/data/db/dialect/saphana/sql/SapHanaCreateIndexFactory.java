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
	public static final String LANGUAGE_COLUMN = "LANGUAGE_COLUMN";
	public static final String MIME_TYPE_COLUMN = "MIME_TYPE_COLUMN";
	public static final String FAST_PREPROCESS = "FAST_PREPROCESS";
	public static final String FUZZY_SEARCH_INDEX = "FUZZY_SEARCH_INDEX";
	public static final String SEARCH_ONLY = "SEARCH_ONLY";
	public static final String FLUSH_AFTER_DOCUMENTS = "FLUSH_AFTER_DOCUMENTS";
	public static final String FLUSH_EVERY_MINUTES = "FLUSH_EVERY_MINUTES";
	public static final String CONFIGURATION = "CONFIGURATION";
	public static final String PHRASE_INDEX_RATIO = "PHRASE_INDEX_RATIO";

	@Override
	public void addObjectDetail(final Index index, final Table table,
			final SapHanaSqlBuilder builder) {
		if (index.getIndexType() == IndexType.FullText) {
			addFullTextIndex(index, table, builder);
			return;
		}
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

	private void addFullTextIndex(final Index index, final Table table,
			final SapHanaSqlBuilder builder) {
		if (table == null || index.getColumns().size() != 1) {
			throw new IllegalArgumentException(
					"SAP HANA FULLTEXT index requires one table column: "
							+ index.getName());
		}
		builder.space()._add("FULLTEXT INDEX").space()
				.name(index, false).on().name(table, false).space()
				._add("(").name(index.getColumns().get(0))._add(")");
		addIdentifierOption(index, LANGUAGE_COLUMN, builder);
		addIdentifierOption(index, MIME_TYPE_COLUMN, builder);
		addOnOffOption(index, FAST_PREPROCESS, builder);
		addOnOffOption(index, FUZZY_SEARCH_INDEX, builder);
		addOnOffOption(index, SEARCH_ONLY, builder);
		addFlushOptions(index, builder);
		addLiteralOption(index, CONFIGURATION, builder);
		addNumberOption(index, PHRASE_INDEX_RATIO, builder);
	}

	private void addIdentifierOption(final Index index, final String key,
			final SapHanaSqlBuilder builder) {
		final String value = index.getSpecifics().get(key);
		if (value != null && !value.isBlank()) {
			builder.space()._add(key.replace('_', ' ')).space()
					.name(value);
		}
	}

	private void addOnOffOption(final Index index, final String key,
			final SapHanaSqlBuilder builder) {
		final Boolean value = index.getSpecifics().get(key, Boolean.class);
		if (value != null) {
			builder.space()._add(key.replace('_', ' ')).space()
					._add(value.booleanValue() ? "ON" : "OFF");
		}
	}

	private void addFlushOptions(final Index index,
			final SapHanaSqlBuilder builder) {
		final Integer minutes = index.getSpecifics().get(
				FLUSH_EVERY_MINUTES, Integer.class);
		final Integer documents = index.getSpecifics().get(
				FLUSH_AFTER_DOCUMENTS, Integer.class);
		if (minutes == null && documents == null) {
			return;
		}
		builder.space()._add("ASYNC FLUSH");
		if (minutes != null) {
			builder.space()._add("EVERY").space()._add(minutes)
					.space()._add("MINUTES");
		}
		if (documents != null) {
			if (minutes != null) {
				builder.space()._add("OR");
			}
			builder.space()._add("AFTER").space()._add(documents)
					.space()._add("DOCUMENTS");
		}
	}

	private void addLiteralOption(final Index index, final String key,
			final SapHanaSqlBuilder builder) {
		final String value = index.getSpecifics().get(key);
		if (value != null && !value.isBlank()) {
			builder.space()._add(key.replace('_', ' ')).space()
					.sqlChar(value);
		}
	}

	private void addNumberOption(final Index index, final String key,
			final SapHanaSqlBuilder builder) {
		final Number value = index.getSpecifics().get(key, Number.class);
		if (value != null) {
			builder.space()._add(key.replace('_', ' ')).space()
					._add(value);
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
