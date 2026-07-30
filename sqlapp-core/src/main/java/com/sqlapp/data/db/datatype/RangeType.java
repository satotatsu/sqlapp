/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.db.datatype;

/**
 * Database range type.
 */
public class RangeType extends AbstractNoSizeType<RangeType> {
	private static final long serialVersionUID = 1L;

	public RangeType() {
		this(DataType.RANGE.getTypeName());
	}

	public RangeType(final String dataTypeName) {
		this.setDataType(DataType.RANGE);
		this.initialize(dataTypeName);
	}
}
