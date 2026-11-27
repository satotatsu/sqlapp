/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.Set;
import com.sqlapp.data.schemas.*;
import com.sqlapp.util.AbstractSqlBuilder;

/** Logical XML indexes; physical XML support indexes belong to Db2. */
final class Db2XmlIndexSql {
	private Db2XmlIndexSql() {
	}

	static void validate(Index index, Table table) {
		if (table == null || index.getColumns().size() != 1 || index.getIndexType() == IndexType.Clustered)
			throw unsupported(index);
		var column = index.getColumns().getFirst();
		if (column.isIncludedColumn() || !column.getStatement().isEmpty()
				|| column.getOrder() != null && column.getOrder() != Order.Asc
				|| column.getSpecifics().get("DB2_COLORDER") != null
						&& !"A".equals(column.getSpecifics().get("DB2_COLORDER")))
			throw unsupported(index);
		Column source = table.getColumns().get(column.getName());
		if (source != null && source.getDataType() != null
				&& source.getDataType() != com.sqlapp.data.db.datatype.DataType.SQLXML)
			throw unsupported(index);
		if ("N".equals(index.getSpecifics().get("NULLKEYS")))
			throw unsupported(index);
	}

	static void append(Index index, AbstractSqlBuilder<?> builder) {
		var fields = index.getSpecifics();
		String pattern = fields.get("XML_PATTERN"), mode = fields.get("XML_TYPEMODEL");
		String datatype = fields.get("XML_DATATYPE"), hashed = fields.get("XML_HASHED");
		if (pattern == null || pattern.isBlank() || pattern.indexOf('\0') >= 0
				|| !Set.of("Q", "R").contains(mode == null ? "" : mode)
				|| !Set.of("Y", "N").contains(hashed == null ? "" : hashed))
			throw unsupported(index);
		String type;
		if ("VARCHAR".equals(datatype)) {
			type = "Y".equals(hashed) ? "VARCHAR HASHED"
					: "VARCHAR(" + number(index, "XML_LENGTH", 1, Short.MAX_VALUE) + ")";
		} else {
			if (!"N".equals(hashed))
				throw unsupported(index);
			if ("DECIMAL".equals(datatype)) {
				int precision = number(index, "XML_LENGTH", 1, 31), scale = number(index, "XML_SCALE", 0, precision);
				type = "DECIMAL(" + precision + "," + scale + ")";
			} else if (Set.of("DOUBLE", "INTEGER", "DATE", "TIMESTAMP").contains(datatype == null ? "" : datatype))
				type = datatype;
			else
				throw unsupported(index);
		}
		builder._add(" GENERATE KEY USING XMLPATTERN ").sqlChar(pattern)._add(" AS SQL " + type)
				._add("R".equals(mode) ? " REJECT INVALID VALUES" : " IGNORE INVALID VALUES");
	}

	private static int number(Index index, String key, int minimum, int maximum) {
		try {
			int value = Integer.parseInt(index.getSpecifics().get(key));
			if (value < minimum || value > maximum)
				throw unsupported(index);
			return value;
		} catch (NumberFormatException exception) {
			throw unsupported(index);
		}
	}

	private static IllegalArgumentException unsupported(Index index) {
		return new IllegalArgumentException(
				"Db2 XML index requires one XML column and supported complete XML pattern/type metadata or a native CREATE definition: "
						+ index.getName());
	}
}
