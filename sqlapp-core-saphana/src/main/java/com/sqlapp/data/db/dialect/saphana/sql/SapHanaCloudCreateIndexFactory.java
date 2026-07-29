/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-saphana.
 */
package com.sqlapp.data.db.dialect.saphana.sql;

import java.util.Set;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Table;

/**
 * SAP HANA Cloud CREATE INDEX.
 */
public class SapHanaCloudCreateIndexFactory
		extends SapHanaCreateIndexFactory {

	public static final String SEARCH_MODE = "SEARCH_MODE";
	public static final String TOKEN_SEPARATORS = "TOKEN_SEPARATORS";
	public static final String MIME_TYPE = "MIME_TYPE";
	public static final String ONLINE_PREFERRED = "ONLINE_PREFERRED";

	private static final Set<String> SEARCH_MODES = Set.of(
			"STRING", "TEXT", "ALPHANUM", "IDENTIFER",
			"ALPHANUM_IDENTIFIER", "POSTCODE");

	@Override
	public void addObjectDetail(final Index index, final Table table,
			final SapHanaSqlBuilder builder) {
		if (index.getIndexType() == IndexType.FullText) {
			addFuzzySearchIndex(index, table, builder);
			return;
		}
		super.addObjectDetail(index, table, builder);
	}

	private void addFuzzySearchIndex(final Index index, final Table table,
			final SapHanaSqlBuilder builder) {
		final Column column = validateFuzzySearchIndex(index, table);
		final String searchMode = searchMode(index);
		builder.space()._add("FUZZY SEARCH INDEX").space()
				.name(index, false).on().name(table, false).space()
				._add("(").name(index.getColumns().get(0))._add(")")
				.space()._add("SEARCH MODE").space()._add(searchMode);
		final String tokenSeparators = index.getSpecifics().get(
				TOKEN_SEPARATORS);
		if (tokenSeparators != null && !tokenSeparators.isBlank()) {
			if (!"TEXT".equals(searchMode)) {
				throw new IllegalArgumentException(
						"SAP HANA Cloud TOKEN SEPARATORS requires "
								+ "SEARCH MODE TEXT: " + index.getName());
			}
			builder.space()._add("TOKEN SEPARATORS").space()
					.sqlChar(tokenSeparators);
		}
		final String mimeType = index.getSpecifics().get(MIME_TYPE);
		if (mimeType != null && !mimeType.isBlank()) {
			builder.space()._add("MIME TYPE").space().sqlChar(mimeType);
		}
		if (column.getDataType() == DataType.BLOB
				&& (mimeType == null || mimeType.isBlank())) {
			throw new IllegalArgumentException(
					"SAP HANA Cloud BLOB fuzzy search index requires "
							+ "MIME_TYPE: " + index.getName());
		}
		if (Boolean.TRUE.equals(index.getSpecifics().get(ONLINE,
				Boolean.class))) {
			builder.space()._add("ONLINE");
			if (Boolean.TRUE.equals(index.getSpecifics().get(
					ONLINE_PREFERRED, Boolean.class))) {
				builder.space()._add("PREFERRED");
			}
		}
	}

	private Column validateFuzzySearchIndex(final Index index,
			final Table table) {
		if (table == null || index.getColumns().size() != 1) {
			throw new IllegalArgumentException(
					"SAP HANA Cloud fuzzy search index requires one "
							+ "table column: " + index.getName());
		}
		final Column column = table.getColumns().get(
				index.getColumns().get(0).getName());
		if (column == null || (column.getDataType() != DataType.NVARCHAR
				&& column.getDataType() != DataType.NCLOB
				&& column.getDataType() != DataType.BLOB)) {
			throw new IllegalArgumentException(
					"SAP HANA Cloud fuzzy search index requires "
							+ "NVARCHAR, NCLOB, or BLOB: "
							+ index.getName());
		}
		return column;
	}

	private String searchMode(final Index index) {
		String value = index.getSpecifics().get(SEARCH_MODE);
		if (value == null || value.isBlank()) {
			value = "TEXT";
		} else {
			value = value.trim().toUpperCase();
		}
		if (!SEARCH_MODES.contains(value)) {
			throw new IllegalArgumentException(
					"Unsupported SAP HANA Cloud fuzzy search mode: "
							+ value);
		}
		return value;
	}
}
