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

import java.sql.Connection;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Supplier;

import com.sqlapp.util.CommonUtils;

public abstract class AbstractConverter<T> implements Converter<T> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 3523823400881627578L;
	private Supplier<T> defaultValue = null;

	@Override
	public T convertObject(Object value, Connection conn) {
		return convertObject(value);
	}

	@Override
	public T getDefaultValue() {
		if (defaultValue != null) {
			return defaultValue.get();
		}
		return null;
	}

	@Override
	public Converter<T> setDefaultValue(Supplier<T> value) {
		this.defaultValue = value;
		return this;
	}

	@Override
	public String format(T value) {
		return null;
	}

	protected boolean isSupplier(Object value) {
		if (value instanceof Supplier) {
			return true;
		}
		return false;
	}

	protected Object getSupplierValue(Object value) {
		Supplier<?> op = Supplier.class.cast(value);
		return op.get();
	}

	protected boolean isOptional(Object value) {
		if (value instanceof Optional) {
			return true;
		} else if (value instanceof OptionalInt) {
			return true;
		} else if (value instanceof OptionalLong) {
			return true;
		} else if (value instanceof OptionalDouble) {
			return true;
		}
		return false;
	}

	protected Object getOptionalValue(Object value) {
		if (value instanceof Optional) {
			Optional<?> op = Optional.class.cast(value);
			if (op.isEmpty()) {
				return null;
			}
			return op.get();
		} else if (value instanceof OptionalInt) {
			OptionalInt op = OptionalInt.class.cast(value);
			if (op.isEmpty()) {
				return null;
			}
			return op.getAsInt();
		} else if (value instanceof OptionalLong) {
			OptionalLong op = OptionalLong.class.cast(value);
			if (op.isEmpty()) {
				return null;
			}
			return op.getAsLong();
		} else if (value instanceof OptionalDouble) {
			OptionalDouble op = OptionalDouble.class.cast(value);
			if (op.isEmpty()) {
				return null;
			}
			return op.getAsDouble();
		}
		return null;
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
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof AbstractConverter<?>)) {
			return false;
		}
		AbstractConverter<?> cst = AbstractConverter.class.cast(obj);
		if (!CommonUtils.eq(this.getDefaultValue(), cst.getDefaultValue())) {
			return false;
		}
		return true;
	}
}
