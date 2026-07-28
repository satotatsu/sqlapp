/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.properties;

public interface VectorDimensionProperty<T> {

	Integer getVectorDimension();

	T setVectorDimension(Integer value);
}
