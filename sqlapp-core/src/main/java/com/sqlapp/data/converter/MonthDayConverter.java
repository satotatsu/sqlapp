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

import java.time.MonthDay;
import java.time.temporal.TemporalAccessor;

/** Converts ISO month-day text (--MM-dd) or date fields without inventing a year or zone. */
public class MonthDayConverter extends AbstractConverter<MonthDay> {

	private static final long serialVersionUID = 1L;

	@Override
	public MonthDay convertObject(final Object value) {
		if (isSupplier(value)) {
			return convertObject(getSupplierValue(value));
		} else if (isOptional(value)) {
			return convertObject(getOptionalValue(value));
		} else if (value == null) {
			return getDefaultValue();
		} else if (value instanceof MonthDay) {
			return (MonthDay) value;
		} else if (value instanceof java.sql.Date) {
			return MonthDay.from(((java.sql.Date) value).toLocalDate());
		} else if (value instanceof TemporalAccessor) {
			return MonthDay.from((TemporalAccessor) value);
		} else if (value instanceof CharSequence) {
			final String text = value.toString().trim();
			return text.isEmpty() ? getDefaultValue() : MonthDay.parse(text);
		}
		throw new ConverterException("Expected ISO month-day text or date fields: " + value);
	}

	@Override
	public String format(final MonthDay value) {
		return value == null ? null : value.toString();
	}

	@Override
	public MonthDay copy(final Object value) {
		return convertObject(value);
	}

	@Override
	public boolean equals(final Object value) {
		return value instanceof MonthDayConverter && super.equals(value);
	}
}
