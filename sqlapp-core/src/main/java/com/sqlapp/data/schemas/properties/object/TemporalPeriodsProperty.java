/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.properties.object;

import com.sqlapp.data.schemas.TemporalPeriodCollection;

public interface TemporalPeriodsProperty<T> extends TemporalPeriodsGetter {

	T setTemporalPeriods(TemporalPeriodCollection value);
}
