/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.migration;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.db.datatype.DataType;

/**
 * Declared output column of a model: the type the target uses, independent of
 * the type the execution engine derives from the query.
 *
 * @param name     column name, as it is created in the engine and the target
 * @param dataType dialect-independent type
 * @param length   length or precision; required for CHAR, VARCHAR, NCHAR and
 *                 NVARCHAR
 * @param scale    scale, for DECIMAL and NUMERIC
 * @param notNull  whether NULL is rejected
 */
public record MigrationModelColumn(String name, DataType dataType, Long length, Integer scale, boolean notNull) {

	/** Character types whose length the target needs and the engine does not keep. */
	public static final Set<DataType> LENGTH_REQUIRED = EnumSet.of(DataType.CHAR, DataType.VARCHAR, DataType.NCHAR,
			DataType.NVARCHAR, DataType.VARCHAR_IGNORECASE);

	public MigrationModelColumn {
		MigrationModel.requireName("Model column", name);
		Objects.requireNonNull(dataType, () -> "Model column type is required: " + name);
		if (dataType == DataType.OTHER) {
			throw new IllegalArgumentException("Model column type OTHER has no portable definition: " + name);
		}
		if (length != null && length <= 0) {
			throw new IllegalArgumentException("Model column length must be positive: " + name);
		}
		if (scale != null && (scale < 0 || length == null || scale > length)) {
			throw new IllegalArgumentException(
					"Model column scale needs a length and must be between 0 and the length: " + name);
		}
		if (length == null && LENGTH_REQUIRED.contains(dataType)) {
			throw new IllegalArgumentException("Model column " + name + " needs a length, such as " + dataType.name()
					+ "(50). The execution engine does not keep string lengths, so the target cannot derive it.");
		}
	}

	/** The column as a NOT NULL column. */
	public MigrationModelColumn withNotNull() {
		return notNull ? this : new MigrationModelColumn(name, dataType, length, scale, true);
	}

	/** Canonical type text, such as {@code VARCHAR(50)} or {@code DECIMAL(12,2)}. */
	public String typeText() {
		if (length == null) {
			return dataType.name();
		}
		return dataType.name() + "(" + length + (scale == null ? "" : "," + scale) + ")";
	}
}
