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
public class OptionalIntConverter extends AbstractNumberConverter<OptionalInt> {

	/** serialVersionUID */
	private static final long serialVersionUID = -6588189179014473698L;
	private static IntegerConverter INTERNAL_CONVERTER = new IntegerConverter();

	@Override
	public OptionalInt convertObject(final Object value) {
		if (isSupplier(value)) {
			return convertObject(getSupplierValue(value));
		} else if (isEmpty(value)) {
			return getDefaultValue();
		} else if (value instanceof OptionalInt) {
			return (OptionalInt) value;
		} else if (value instanceof OptionalDouble) {
			final OptionalDouble op = ((OptionalDouble) value);
			if (!op.isPresent()) {
				return getDefaultValue();
			}
			int val = (int) op.getAsDouble();
			return OptionalInt.of(val);
		} else if (value instanceof OptionalLong) {
			final OptionalLong op = (OptionalLong) value;
			if (!op.isPresent()) {
				return getDefaultValue();
			}
			int val = (int) op.getAsLong();
			return OptionalInt.of(val);
		} else if (value instanceof Optional<?>) {
			final Optional<?> op = (Optional<?>) value;
			if (!op.isPresent()) {
				return getDefaultValue();
			}
			Integer val = INTERNAL_CONVERTER.convertObject(op);
			return OptionalInt.of(val);
		}
		Integer val = INTERNAL_CONVERTER.convertObject(value);
		return OptionalInt.of(val);
	}

	@Override
	public OptionalInt getDefaultValue() {
		return OptionalInt.empty();
	}

	@Override
	public String format(final OptionalInt value) {
		if (value == null) {
			return null;
		}
		if (value.isEmpty()) {
			return null;
		}
		if (getNumberFormat() == null) {
			return value.toString();
		}
		return format(value.getAsInt());
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
		if (!(obj instanceof OptionalIntConverter)) {
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	@Override
	public OptionalInt copy(final Object obj) {
		if (obj == null) {
			return null;
		}
		return convertObject(obj);
	}

	@Override
	protected boolean getParseIntegerOnly() {
		return true;
	}
}