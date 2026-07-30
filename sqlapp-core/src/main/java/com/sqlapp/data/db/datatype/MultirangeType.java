/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.db.datatype;

/**
 * Database multirange type.
 */
public class MultirangeType extends AbstractNoSizeType<MultirangeType> {
	private static final long serialVersionUID = 1L;

	public MultirangeType() {
		this(DataType.MULTIRANGE.getTypeName());
	}

	public MultirangeType(final String dataTypeName) {
		this.setDataType(DataType.MULTIRANGE);
		this.initialize(dataTypeName);
	}
}
