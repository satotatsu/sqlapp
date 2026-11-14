/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.JsonUtils;

/**
 * JDBC keyset source whose rows are projected into a logical table with changed
 * column types or converted values.
 *
 * <p>Rows are read with the source table's types. Keys are neither converted nor
 * retyped, so resume tokens and ordering are those of the plain source. Copy,
 * verification and repair all read through this source and therefore compare the
 * same projected values. {@link #getTable()} returns the logical table, and the
 * conversion fingerprint is part of {@link #getConfigurationFingerprint()}.</p>
 */
public class ConvertingJdbcBulkMigrationKeysetSource extends JdbcBulkMigrationKeysetSource {
	private final BulkMigrationColumnConversions conversions;

	public ConvertingJdbcBulkMigrationKeysetSource(final Connection connection, final List<String> keyColumnNames,
			final BulkMigrationColumnConversions conversions) {
		super(connection, Objects.requireNonNull(conversions, "conversions").getSourceTable(), keyColumnNames);
		this.conversions = conversions;
		conversions.requireUnconverted(getKeyColumnNames(), "keyset");
	}

	/** Converted columns, changed types and their expressions. */
	public BulkMigrationColumnConversions getConversions() {
		return conversions;
	}

	@Override
	public Table getTable() {
		return conversions.getTable();
	}

	@Override
	public String getConfigurationFingerprint() {
		if (conversions.isEmpty()) {
			return super.getConfigurationFingerprint();
		}
		return JsonUtils.toJsonString(List.of(super.getConfigurationFingerprint(), conversions.getFingerprint()));
	}

	@Override
	public Iterator<Row> iterator(final String resumeToken) throws SQLException {
		return conversions.iterator(super.iterator(resumeToken));
	}
}
