/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.*;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.util.*;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/**
 * Native ordinary-column changes; unsupported conversions require an explicit
 * plan.
 */
final class Db2ColumnAlterSql {
	private Db2ColumnAlterSql() {
	}

	private static final Set<String> SUPPORTED = Set.of("name", "dataType", "length", "scale", "defaultValue",
			"notNull", "remarks");

	static List<SqlOperation> create(Dialect dialect, Table table, Column before, Column after,
			DbObjectDifference difference) {
		var changed = difference.getChangedProperties().keySet();
		if (!SUPPORTED.containsAll(changed))
			throw unsupported(before, "attributes " + changed);
		boolean substantive = changed.stream().anyMatch(key -> !key.equals("remarks"));
		if (substantive && (before.isIdentity() || after.isIdentity() || before.getFormula() != null
				|| after.getFormula() != null))
			throw unsupported(before, "identity/generated columns");
		String tableName = Db2CreationContext.qualified(table.getSchemaName(), table.getName());
		String columnName = Db2CreationContext.identifier(before.getName());
		String prefix = "ALTER TABLE " + tableName + " ALTER COLUMN " + columnName;
		List<SqlOperation> result = new ArrayList<>();
		boolean maintenance = false;
		if (changed.stream().anyMatch(Set.of("dataType", "length", "scale")::contains)) {
			validateWidening(before, after);
			String type = new TypeRenderer(dialect).render(after);
			result.add(new SqlOperation(prefix + " SET DATA TYPE " + type, SqlType.ALTER, before, after));
			maintenance = before.getDataType() != after.getDataType()
					|| !Set.of(DataType.VARCHAR, DataType.NVARCHAR, DataType.VARBINARY).contains(after.getDataType());
		}
		if (changed.contains("defaultValue")) {
			String expression = after.getDefaultValue();
			if (expression != null && (expression.isBlank() || expression.indexOf('\0') >= 0))
				throw unsupported(before, "invalid default expression");
			result.add(new SqlOperation(prefix + (expression == null ? " DROP DEFAULT" : " SET DEFAULT " + expression),
					SqlType.ALTER, before, after));
		}
		if (changed.contains("notNull")) {
			result.add(new SqlOperation(prefix + (after.isNotNull() ? " SET NOT NULL" : " DROP NOT NULL"),
					SqlType.ALTER, before, after));
			maintenance = true;
		}
		if (changed.contains("remarks"))
			result.add(new SqlOperation(
					Db2AlterCommentSql.comment(dialect, "COLUMN " + tableName + "." + columnName, after.getRemarks()),
					SqlType.SET_COMMENT, before, after));
		if (changed.contains("name"))
			result.add(new SqlOperation("ALTER TABLE " + tableName + " RENAME COLUMN " + columnName + " TO "
					+ Db2CreationContext.identifier(after.getName()), SqlType.ALTER, before, after));
		if (maintenance)
			result.add(new SqlOperation("-- Db2 column changes on " + tableName
					+ " may require an explicit table REORG before subsequent access; no REORG or data backfill is performed by this plan.",
					SqlType.COMMENT, before, after));
		return result;
	}

	private static void validateWidening(Column before, Column after) {
		var integers = List.of(DataType.SMALLINT, DataType.INT, DataType.BIGINT);
		if (before.getDataType() != after.getDataType()) {
			if (integers.contains(before.getDataType())
					&& integers.indexOf(after.getDataType()) > integers.indexOf(before.getDataType()))
				return;
			throw unsupported(before, "type conversion/narrowing");
		}
		if (Set.of(DataType.VARCHAR, DataType.NVARCHAR, DataType.VARBINARY, DataType.DECIMAL, DataType.NUMERIC)
				.contains(after.getDataType()) && before.getLength() != null && after.getLength() != null
				&& after.getLength() > before.getLength() && Objects.equals(before.getScale(), after.getScale())) {
			if (Set.of(DataType.DECIMAL, DataType.NUMERIC).contains(after.getDataType()) && (after.getLength() > 31
					|| after.getScale() == null || after.getScale() < 0 || after.getScale() > after.getLength()))
				throw unsupported(before, "invalid decimal precision/scale");
			return;
		}
		throw unsupported(before, "length/scale change outside supported widening");
	}

	private static final class TypeRenderer extends Db2SqlBuilder {
		TypeRenderer(Dialect dialect) {
			super(dialect);
		}

		String render(Column column) {
			return typeDefinition(column).toString();
		}
	}

	private static IllegalArgumentException unsupported(Column column, String reason) {
		return new IllegalArgumentException(
				"Db2 column ALTER requires an explicit native migration plan for " + reason + ": " + column.getName());
	}
}
