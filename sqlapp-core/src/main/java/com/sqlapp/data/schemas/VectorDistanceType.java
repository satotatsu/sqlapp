/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas;

import java.util.Locale;

public enum VectorDistanceType implements EnumProperties {
	Cosine("COSINE"),
	Euclidean("EUCLIDEAN"),
	EuclideanSquared("EUCLIDEAN_SQUARED"),
	InnerProduct("INNER_PRODUCT"),
	DotProduct("DOT_PRODUCT"),
	Manhattan("MANHATTAN"),
	Hamming("HAMMING"),
	Jaccard("JACCARD");

	private final String sqlValue;

	VectorDistanceType(final String sqlValue) {
		this.sqlValue = sqlValue;
	}

	@Override
	public String getDisplayName() {
		return sqlValue;
	}

	@Override
	public String getDisplayName(final Locale locale) {
		return sqlValue;
	}

	@Override
	public String getSqlValue() {
		return sqlValue;
	}
}
