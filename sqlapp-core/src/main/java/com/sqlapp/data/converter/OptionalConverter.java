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
 * PathType Converter
 * 
 * @author SATOH
 *
 */
public class OptionalConverter extends AbstractConverter<Optional<?>> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -7480426186864635353L;

	@Override
	public Optional<?> convertObject(Object value) {
		if (isEmpty(value)) {
			return getDefaultValue();
		} else if (value instanceof Optional) {
			return (Optional<?>) value;
		} else if (value instanceof OptionalInt) {
			final OptionalInt op = ((OptionalInt) value);
			if (!op.isPresent()) {
				return getDefaultValue();
			}
			return Optional.of(op.getAsInt());
		} else if (value instanceof OptionalDouble) {
			final OptionalDouble op = ((OptionalDouble) value);
			if (!op.isPresent()) {
				return getDefaultValue();
			}
			return Optional.of(op.getAsDouble());
		} else if (value instanceof OptionalLong) {
			final OptionalLong op = (OptionalLong) value;
			if (!op.isPresent()) {
				return getDefaultValue();
			}
			return Optional.of(op.getAsLong());
		}
		return (Optional<?>) Optional.of(value);
	}

	@Override
	public Optional<?> getDefaultValue() {
		return Optional.empty();
	}

	@Override
	public String format(Optional<?> value) {
		if (value == null) {
			return null;
		}
		return value.toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof OptionalConverter)) {
			return false;
		}
		OptionalConverter con = cast(obj);
		if (!eq(this.getDefaultValue(), con.getDefaultValue())) {
			return false;
		}
		return true;
	}

	@Override
	public Optional<?> copy(Object value) {
		if (isEmpty(value)) {
			return getDefaultValue();
		} else if (value instanceof Optional) {
			Optional<?> op = Optional.class.cast(value);
			if (op.isEmpty()) {
				return getDefaultValue();
			}
			return Optional.of(op.get());
		}
		return Optional.of(value);
	}

}
