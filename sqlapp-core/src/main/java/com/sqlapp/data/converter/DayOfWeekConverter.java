/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.converter;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAccessor;

/** Adds ISO numeric values and date fields to the existing enum-name conversion. */
public class DayOfWeekConverter extends EnumConverter<DayOfWeek> {

	private static final long serialVersionUID = 1L;

	public DayOfWeekConverter() {
		super(DayOfWeek.class);
	}

	@Override
	public DayOfWeek convertObject(final Object value) {
		if (isSupplier(value)) {
			return convertObject(getSupplierValue(value));
		} else if (isOptional(value)) {
			return convertObject(getOptionalValue(value));
		} else if (value instanceof DayOfWeek) {
			return (DayOfWeek) value;
		} else if (value instanceof Number) {
			return DayOfWeek.of(new BigDecimal(value.toString()).intValueExact());
		} else if (value instanceof java.sql.Date) {
			return DayOfWeek.from(((java.sql.Date) value).toLocalDate());
		} else if (value instanceof TemporalAccessor) {
			return DayOfWeek.from((TemporalAccessor) value);
		} else if (value instanceof CharSequence && value.toString().matches("[+-]?[0-9]+")) {
			return DayOfWeek.of(Integer.parseInt(value.toString()));
		} else if (value instanceof CharSequence) {
			final DayOfWeek byName = byName(value.toString().trim());
			if (byName != null) {
				return byName;
			}
		}
		return super.convertObject(value);
	}

	/**
	 * English and Japanese full or short names, case-insensitive ("MONDAY",
	 * "mon", "月曜日" / "NOVEMBER", "Nov", "11月").
	 */
	private static DayOfWeek byName(final String text) {
		for (final DayOfWeek candidate : DayOfWeek.values()) {
			for (final java.util.Locale locale : new java.util.Locale[] { java.util.Locale.ENGLISH,
					java.util.Locale.JAPANESE }) {
				for (final java.time.format.TextStyle style : new java.time.format.TextStyle[] {
						java.time.format.TextStyle.FULL, java.time.format.TextStyle.SHORT }) {
					if (candidate.getDisplayName(style, locale).equalsIgnoreCase(text)) {
						return candidate;
					}
				}
			}
		}
		return null;
	}
}
