/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.datatype;

import java.util.List;
import java.util.Objects;
import com.sqlapp.data.interval.IntervalValue;

/** Destination projection and stable warning codes for review, copying and verification. */
public record IntervalConversion(Object value, List<String> warnings) {
	public IntervalConversion {
		Objects.requireNonNull(value, "value");
		warnings = List.copyOf(warnings);
	}
	public static IntervalConversion unchanged(IntervalValue value) {
		return new IntervalConversion(value, List.of());
	}
}
