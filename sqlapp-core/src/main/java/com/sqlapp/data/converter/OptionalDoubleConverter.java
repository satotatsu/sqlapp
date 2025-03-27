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

import static com.sqlapp.util.CommonUtils.cast;
import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * OptionalInt Converter
 * 
 * @author SATOH
 *
 */
public class OptionalDoubleConverter extends AbstractNumberConverter<OptionalDouble> {

	/** serialVersionUID */
	private static final long serialVersionUID = -6588189179014473698L;
	private static DoubleConverter INTERNAL_CONVERTER = new DoubleConverter();

	@Override
	public OptionalDouble convertObject(final Object value) {
		if (isEmpty(value)) {
			return getDefaultValue();
		} else if (value instanceof OptionalDouble) {
			return (OptionalDouble) value;
		} else if (value instanceof OptionalInt) {
			final OptionalInt op = ((OptionalInt) value);
			if (!op.isPresent()) {
				return getDefaultValue();
			}
			int val = op.getAsInt();
			return OptionalDouble.of(val);
		} else if (value instanceof OptionalLong) {
			final OptionalLong op = (OptionalLong) value;
			if (!op.isPresent()) {
				return getDefaultValue();
			}
			long val = op.getAsLong();
			return OptionalDouble.of(val);
		} else if (value instanceof Optional<?>) {
			final Optional<?> op = (Optional<?>) value;
			if (!op.isPresent()) {
				return getDefaultValue();
			}
			double val = INTERNAL_CONVERTER.convertObject(op);
			return OptionalDouble.of(val);
		}
		double val = INTERNAL_CONVERTER.convertObject(value);
		return OptionalDouble.of(val);
	}

	@Override
	public OptionalDouble getDefaultValue() {
		return OptionalDouble.empty();
	}

	@Override
	public String convertString(final OptionalDouble value) {
		if (value == null) {
			return null;
		}
		if (value.isEmpty()) {
			return null;
		}
		if (getNumberFormat() == null) {
			return value.toString();
		}
		return format(value.getAsDouble());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj) {
		if (obj == this) {
			return true;
		}
		if (!super.equals(this)) {
			return false;
		}
		if (!(obj instanceof OptionalDoubleConverter)) {
			return false;
		}
		final OptionalDoubleConverter con = cast(obj);
		if (!eq(this.getDefaultValue(), con.getDefaultValue())) {
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return this.getClass().getName().hashCode();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	@Override
	public OptionalDouble copy(final Object obj) {
		if (obj == null) {
			return null;
		}
		return convertObject(obj);
	}

	@Override
	protected boolean getParseIntegerOnly() {
		return false;
	}
}