/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.db.datatype;

/**
 * Database vector type. Vendor-specific builders render its dimension and
 * element type.
 */
public class VectorType extends AbstractNoSizeType<VectorType> {

	private static final long serialVersionUID = 1L;

	public VectorType() {
		this(DataType.VECTOR.getTypeName());
	}

	protected VectorType(final String dataTypeName) {
		this.setDataType(DataType.VECTOR);
		this.initialize(dataTypeName);
		this.addPetternColumnTypeMatcher("VECTOR\\s*\\(\\s*(?:FLOAT32|REAL|INT8)\\s*\\)");
	}
}
