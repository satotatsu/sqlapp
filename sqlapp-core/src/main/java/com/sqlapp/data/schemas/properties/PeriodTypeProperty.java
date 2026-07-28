package com.sqlapp.data.schemas.properties;

import com.sqlapp.data.schemas.TemporalPeriodType;

public interface PeriodTypeProperty<T> {
	TemporalPeriodType getPeriodType();
	T setPeriodType(TemporalPeriodType value);
}
