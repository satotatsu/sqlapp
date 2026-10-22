/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.h2.util.H2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractMergeTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.data.schemas.Table;

/**
 * Typed MERGE sources reuse shared key selection, matching and column policies.
 */
public class H2MergeFactory extends AbstractMergeTableFactory<H2SqlBuilder> {
	@Override
	protected SqlType getSqlType() {
		return SqlType.MERGE;
	}

	@Override
	public List<SqlOperation> createSql(Table table) {
		var signature = createSqlSignature(table);
		try {
			signature.setColumnSelectionStrategy(getTableOptions().getMergeKeyColumnsMatchingStrategy().apply(table));
		} catch (com.sqlapp.exceptions.MissingWhereClauseKeyException ex) {
			throw new IllegalArgumentException("H2 MERGE requires a primary or unique key", ex);
		}
		var keys = signature.getSelectedColumnsHolder().getKeyColumns();
		if (keys.isEmpty())
			throw new IllegalArgumentException("H2 MERGE requires an unambiguous primary or unique key");
		if (keys.stream().anyMatch(column -> isFormulaColumn(column)
				|| column.isIdentity() && column.getIdentityGenerationType() == IdentityGenerationType.Always))
			throw new IllegalArgumentException(
					"H2 MERGE keys cannot be generated columns or GENERATED ALWAYS identities; select a natural unique key");
		if (getTableOptions().getMergeRowsWithDelete().test(table)
				|| getTableOptions().getMergeTableWithDelete().test(table)
				|| getTableOptions().getMergeTableWithReturning().test(table))
			throw new IllegalArgumentException(
					"H2 MERGE does not support the requested delete-by-source or RETURNING policy");
		if (getSqlType() != SqlType.MERGE_TABLE && table.getColumns().stream()
				.anyMatch(column -> column.getDomain() != null
						|| column.getDataType() == com.sqlapp.data.db.datatype.DataType.DOMAIN
						|| column.getArrayDimension() > 0
						|| column.getDataTypeName() != null && (column.getDataTypeName()
								.toUpperCase(java.util.Locale.ROOT).contains(" ARRAY")
								|| column.getDataTypeName().toUpperCase(java.util.Locale.ROOT).startsWith("ROW("))))
			throw new IllegalArgumentException(
					"H2 MERGE of domain/ARRAY/ROW values requires MERGE_TABLE with a typed staging table to avoid truncating source values during casts");
		return super.createSql(table);
	}

	@Override
	protected void addUsing(Table table, SqlSignature signature, String alias, H2SqlBuilder builder) {
		if (getSqlType() == SqlType.MERGE_TABLE) {
			super.addUsing(table, signature, alias, builder);
			return;
		}
		var columns = table.getColumns().stream().filter(column -> !isFormulaColumn(column)).toList();
		builder.using().space()._add("(");
		if (getSqlType() == SqlType.MERGE_ROWS)
			builder._add("/*VALUES*/");
		builder.values().space()._add("(");
		for (int i = 0; i < columns.size(); i++) {
			builder.lineBreak();
			if (i > 0)
				builder._add(",");
			var column = columns.get(i);
			String value = getValueDefinitionForMerge(column);
			builder._add("CAST(")._add(value).as().space();
			// CAST to a bounded string/binary type silently truncates in H2. Keep
			// input wide so the target assignment can report a length violation.
			if (column.getDataType() == com.sqlapp.data.db.datatype.DataType.JSON)
				builder.addTypeDefinition(column);
			else
				switch (column.getDataType().getJdbcType()) {
				case CHAR, VARCHAR, LONGVARCHAR, NCHAR, NVARCHAR, LONGNVARCHAR ->
					builder._add(column.getDataType() == com.sqlapp.data.db.datatype.DataType.VARCHAR_IGNORECASE
							? "VARCHAR_IGNORECASE"
							: "VARCHAR");
				case BINARY, VARBINARY, LONGVARBINARY -> builder._add("VARBINARY");
				default -> builder.addTypeDefinition(column);
				}
			builder._add(")");
		}
		builder.lineBreak()._add(")");
		if (getSqlType() == SqlType.MERGE_ROWS)
			builder._add("/*END*/");
		builder._add(")").as().space()._add(alias).space()._add("(");
		for (int i = 0; i < columns.size(); i++) {
			if (i > 0)
				builder._add(",");
			builder.name(columns.get(i));
		}
		builder._add(")");
	}

	@Override
	protected void addMergeTableWhenMatched(Table table, SqlSignature signature, String target, String source,
			H2SqlBuilder builder) {
		var holder = signature.getSelectedColumnsHolder();
		if (table.getColumns().stream()
				.anyMatch(column -> isUpdateable(column) && !column.isIdentity()
						&& !holder.getKeyColumns().contains(column)
						&& (!holder.isUniqueKey() || !signature.getPrimaryKey().getKeyColumns().contains(column))))
			super.addMergeTableWhenMatched(table, signature, target, source, builder);
	}

	@Override
	protected void addOn(Table table, SqlSignature signature, String target, String source, H2SqlBuilder builder) {
		var keys = signature.getSelectedColumnsHolder().getKeyColumns();
		var names = keys.stream().map(Column::getName).collect(java.util.stream.Collectors.toSet());
		var policies = table.getConstraints().getUniqueConstraints().stream()
				.filter(constraint -> constraint.getColumns().size() == keys.size()
						&& constraint.getColumns().stream().allMatch(column -> names.contains(column.getName())))
				.map(constraint -> constraint.getSpecifics().get("h2.nullsDistinct")).toList();
		boolean nullSafe = policies.contains("NO") || policies.contains("ALL");
		builder.on().space()._add("(");
		int position = 0;
		for (var key : keys) {
			builder.lineBreak().and(position++ > 0);
			if (nullSafe) {
				builder._add("((").name(target + ".", key)._add(" IS NULL AND ").name(source + ".", key)
						._add(" IS NULL) OR (");
			}
			boolean fixedBinary = key.getDataType().getJdbcType() == java.sql.JDBCType.BINARY;
			if (fixedBinary) {
				long length = key.getLength() == null ? 1 : key.getLength();
				builder._add("(OCTET_LENGTH(").name(source + ".", key)._add(") <= ")._add(length)._add(" AND ")
						.name(target + ".", key).eq();
				builder._add("CAST(").name(source + ".", key).as().space().addTypeDefinition(key)._add("))");
			} else
				builder.name(target + ".", key).eq().name(source + ".", key);
			if (nullSafe) {
				builder._add("))");
			}
		}
		if (nullSafe && !policies.contains("NO")) {
			builder._add(" AND (");
			int index = 0;
			for (var key : keys) {
				if (index++ > 0) {
					builder._add(" OR ");
				}
				builder.name(source + ".", key)._add(" IS NOT NULL");
			}
			builder._add(")");
		}
		builder.lineBreak()._add(")");
	}

	@Override
	protected boolean isUpdateable(Column column) {
		return !column.isIdentity() && super.isUpdateable(column);
	}

	@Override
	protected void addMergeTableWhenNotMatched(Table table, SqlSignature signature, String target, String source,
			H2SqlBuilder builder) {
		var columns = table.getColumns().stream().filter(this::isInsertable).filter(
				column -> !column.isIdentity() || column.getIdentityGenerationType() != IdentityGenerationType.Always)
				.toList();
		builder.lineBreak().when().not().matched().then().insert().space()._add("(");
		for (int i = 0; i < columns.size(); i++) {
			if (i > 0)
				builder._add(",");
			builder.name(columns.get(i));
		}
		builder._add(")").values().space()._add("(");
		for (int i = 0; i < columns.size(); i++) {
			if (i > 0)
				builder._add(",");
			var column = columns.get(i);
			String value = getTableOptions().getInsertTableColumnValue().apply(column);
			if (column.getDefaultValue() != null)
				builder._add("COALESCE(");
			if (value != null && !value.equals(column.getName()))
				builder._add(value);
			else
				builder.name(source + ".", column);
			if (column.getDefaultValue() != null)
				builder._add(",")._add(column.getDefaultValue())._add(")");
		}
		builder._add(")");
	}

	public static class Rows extends H2MergeFactory {
		@Override
		protected SqlType getSqlType() {
			return SqlType.MERGE_ROWS;
		}
	}

	public static class Staged extends H2MergeFactory {
		@Override
		protected SqlType getSqlType() {
			return SqlType.MERGE_TABLE;
		}
	}
}
