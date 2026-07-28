/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.properties;

import com.sqlapp.data.schemas.VectorDistanceType;

public interface VectorDistanceTypeProperty<T> {

	VectorDistanceType getVectorDistanceType();

	T setVectorDistanceType(VectorDistanceType value);
}
