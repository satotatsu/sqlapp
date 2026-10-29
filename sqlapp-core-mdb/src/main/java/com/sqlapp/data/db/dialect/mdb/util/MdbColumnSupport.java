/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb.util;

import java.io.IOException;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.mdb.MdbFileLoader;
import com.sqlapp.data.schemas.Column;
import io.github.spannm.jackcess.PropertyMap;

/** Internal Access column mapping shared by file and JDBC metadata readers. */
public final class MdbColumnSupport {
	private MdbColumnSupport() {
	}

	public static Column read(final io.github.spannm.jackcess.Column source) throws IOException {
		final Column column = new Column(source.getName()).setDataType(dataType(source.getType()));
		column.setDataTypeName(source.getType().getTypeName());
		enrich(column, source);
		return column;
	}

	public static void enrich(final Column column, final io.github.spannm.jackcess.Column source) throws IOException {
		final var properties = source.getProperties();
		column.getSpecifics().put(MdbFileLoader.SOURCE_TYPE, source.getType().name());
		column.setIdentity(source.isAutoNumber());
		final Object required = properties.getValue(PropertyMap.REQUIRED_PROP);
		final boolean primaryKey = source.getTable().getIndexes().stream().filter(index -> index.isPrimaryKey())
				.anyMatch(index -> index.getColumns().stream()
						.anyMatch(key -> key.getName().equalsIgnoreCase(source.getName())));
		if (required instanceof Boolean || primaryKey || source.isAutoNumber()) {
			column.setNotNull(primaryKey || source.isAutoNumber() || Boolean.TRUE.equals(required));
		}
		column.setDefaultValue(string(properties.getValue(PropertyMap.DEFAULT_VALUE_PROP)));
		column.setRemarks(string(properties.getValue(PropertyMap.DESCRIPTION_PROP)));
		column.setCheck(string(properties.getValue(PropertyMap.VALIDATION_RULE_PROP)));
		final Object allowZero = properties.getValue(PropertyMap.ALLOW_ZERO_LEN_PROP);
		if (allowZero instanceof Boolean) {
			column.getSpecifics().put(MdbFileLoader.ALLOW_ZERO_LENGTH, allowZero.toString());
		}
		if (source.isCalculated()) {
			column.setFormula(string(properties.getValue(PropertyMap.EXPRESSION_PROP)));
		}
		if (source.getType().isTextual() && !source.getType().isLongValue()) {
			column.setLength((long) source.getLengthInUnits());
		} else if (source.getType() == io.github.spannm.jackcess.DataType.GUID) {
			column.setLength(38L);
		} else if (source.getType() == io.github.spannm.jackcess.DataType.BINARY) {
			column.setLength((long) source.getLength());
		} else if (source.getType() == io.github.spannm.jackcess.DataType.MONEY) {
			column.setLength(19L).setScale(4);
		}
		if (source.getType().getHasScalePrecision()) {
			column.setLength((long) source.getPrecision()).setScale((int) source.getScale());
		}
	}

	public static DataType dataType(final io.github.spannm.jackcess.DataType type) {
		return switch (type) {
		case BOOLEAN -> DataType.BOOLEAN;
		case BYTE -> DataType.TINYINT;
		case INT -> DataType.SMALLINT;
		case LONG -> DataType.INT;
		case BIG_INT -> DataType.BIGINT;
		case MONEY, NUMERIC -> DataType.DECIMAL;
		case FLOAT -> DataType.REAL;
		case DOUBLE -> DataType.DOUBLE;
		case SHORT_DATE_TIME, EXT_DATE_TIME -> DataType.DATETIME;
		case BINARY -> DataType.VARBINARY;
		case TEXT, GUID -> DataType.NVARCHAR;
		case MEMO -> DataType.LONGNVARCHAR;
		case OLE, COMPLEX_TYPE -> DataType.BLOB;
		default -> DataType.OTHER;
		};
	}

	private static String string(final Object value) {
		return value == null ? null : value.toString();
	}
}
