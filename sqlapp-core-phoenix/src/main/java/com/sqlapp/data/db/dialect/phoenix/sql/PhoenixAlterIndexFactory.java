/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import java.util.List;
import java.util.Objects;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.phoenix.util.PhoenixSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Order;

/**
 * Conservative state-only ALTER; rebuild remains an explicitly selected
 * operation.
 */
public class PhoenixAlterIndexFactory extends SimpleSqlFactory<Index, PhoenixSqlBuilder> {
	public static final String STATE = "phoenix.indexState";

	static boolean stateOnly(Index original, Index target, Dialect dialect) {
		if (original == null || target == null)
			return false;
		final var left = original.clone();
		final var right = target.clone();
		left.getSpecifics().remove(STATE);
		right.getSpecifics().remove(STATE);
		normalizeOptions(left);
		normalizeOptions(right);
		PhoenixAlterTableFactory.normalizeIndexFamilies(left, original.getTable());
		PhoenixAlterTableFactory.normalizeIndexFamilies(right, target.getTable());
		return left.diff(right).toDifference().getChangedProperties(dialect).isEmpty();
	}

	private static void normalizeOptions(Index index) {

		// INCLUDE columns carry values only; CREATE INDEX never emits their sort order.
		index.getIncludes().forEach(column -> column.setOrder(Order.Asc));
		if (index.getIndexType() == IndexType.BTree)
			index.setIndexType((IndexType) null);
		if (index.getWhere() != null && index.getWhere().isBlank())
			index.setWhere(null);
		final Integer buckets = PhoenixDdlOptions.saltBuckets(index.getSpecifics(), true);
		if (buckets != null)
			index.getSpecifics().put(PhoenixDdlOptions.SALT_BUCKETS, buckets);
		final String type = index.getSpecifics().get("phoenix.indexType");
		if ("GLOBAL".equalsIgnoreCase(type))
			index.getSpecifics().remove("phoenix.indexType");
		else if ("LOCAL".equalsIgnoreCase(type))
			index.getSpecifics().put("phoenix.indexType", "LOCAL");
	}

	private static String state(String value) {
		if (value == null || value.isBlank())
			throw new IllegalArgumentException("Phoenix index ALTER requires an explicit " + STATE);
		return switch (value.strip().toUpperCase(java.util.Locale.ROOT)) {
		case "A", "ACTIVE", "USABLE" -> "ACTIVE";
		case "X", "DISABLE" -> "DISABLE";
		case "D", "UNUSABLE" -> "UNUSABLE";
		default -> throw new UnsupportedOperationException("Phoenix index state requires a separate operation: " + value
				+ "; select SqlType.REBUILD explicitly when a rebuild is intended");
		};
	}

	@Override
	public List<SqlOperation> createSql(Index index) {
		final String target = state(index.getSpecifics().get(STATE));
		if (target.equals("ACTIVE"))
			throw new UnsupportedOperationException("Phoenix USABLE requires a known maintained UNUSABLE source state; "
					+ "supply an index diff or select SqlType.REBUILD explicitly");
		return operation(index, null, target);
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		final var original = difference.getOriginal(Index.class);
		final var target = difference.getTarget(Index.class);
		if (!stateOnly(original, target, getDialect()))
			throw new UnsupportedOperationException("Phoenix standalone index ALTER supports only state changes; "
					+ "use the table/view diff for structural replacements");
		validateIdentity(original);
		validateIdentity(target);
		if (!Objects.equals(original.getTable().getCatalogName(), target.getTable().getCatalogName())
				|| !Objects.equals(original.getTable().getSchemaName(), target.getTable().getSchemaName())
				|| !Objects.equals(original.getTable().getName(), target.getTable().getName()))
			throw new UnsupportedOperationException("Phoenix index ALTER cannot change its owning table/view");
		final String originalState = original.getSpecifics().get(STATE);
		final String targetState = target.getSpecifics().get(STATE);
		if (Objects.equals(originalState, targetState))
			return List.of();
		final String before = state(originalState);
		final String after = state(targetState);
		if (before.equals(after))
			return List.of();
		if (before.equals("DISABLE"))
			throw new UnsupportedOperationException(
					"A disabled Phoenix index requires explicit SqlType.REBUILD before re-enabling");
		return operation(target, original, after.equals("ACTIVE") ? "USABLE" : after);
	}

	private void validateIdentity(Index index) {
		if (index.getTable() == null || index.getName() == null || index.getName().isBlank()
				|| index.getTable().getName() == null || index.getTable().getName().isBlank())
			throw new IllegalArgumentException("Phoenix ALTER INDEX requires an index name and parent table/view");
	}

	private List<SqlOperation> operation(Index index, Index original, String action) {
		validateIdentity(index);
		final var builder = createSqlBuilder();
		builder.alter().index().name(index, false).on().name(index.getTable(), getOptions().isDecorateSchemaName())
				.space()._add(action);
		return List.of(createOperation(builder.toString(), SqlType.ALTER, original, index));
	}
}
