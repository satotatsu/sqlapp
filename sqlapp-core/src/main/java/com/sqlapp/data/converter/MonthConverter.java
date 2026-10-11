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
import java.time.Month;
import java.time.temporal.TemporalAccessor;

/** Adds ISO numeric values and date fields to the existing enum-name conversion. */
public class MonthConverter extends EnumConverter<Month> {

	private static final long serialVersionUID = 1L;

	public MonthConverter() {
		super(Month.class);
	}

	@Override
	public Month convertObject(final Object value) {
		if (isSupplier(value)) {
			return convertObject(getSupplierValue(value));
		} else if (isOptional(value)) {
			return convertObject(getOptionalValue(value));
		} else if (value instanceof Month) {
			return (Month) value;
		} else if (value instanceof Number) {
			return Month.of(new BigDecimal(value.toString()).intValueExact());
		} else if (value instanceof java.sql.Date) {
			return Month.from(((java.sql.Date) value).toLocalDate());
		} else if (value instanceof TemporalAccessor) {
			return Month.from((TemporalAccessor) value);
		} else if (value instanceof CharSequence && value.toString().matches("[+-]?[0-9]+")) {
			return Month.of(Integer.parseInt(value.toString()));
		}
		return super.convertObject(value);
	}
}
