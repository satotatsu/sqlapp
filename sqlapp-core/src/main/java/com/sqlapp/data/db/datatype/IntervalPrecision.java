/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.datatype;

/** Semantic interval precisions, independent of a vendor's Column length/scale layout. */
public record IntervalPrecision(int leading, int fractional) {
	public IntervalPrecision {
		if (leading < -1 || leading > 18 || fractional < 0 || fractional > 9)
			throw new IllegalArgumentException("Invalid interval precision");
	}
}
