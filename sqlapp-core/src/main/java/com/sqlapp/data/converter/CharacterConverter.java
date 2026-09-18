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

/**
 * Converts a single UTF-16 code unit. Empty input uses the configured default;
 * longer text is rejected rather than truncated. Whitespace is preserved.
 */
public class CharacterConverter extends AbstractConverter<Character> {

	private static final long serialVersionUID = 1L;

	@Override
	public Character convertObject(final Object value) {
		if (isSupplier(value)) {
			return convertObject(getSupplierValue(value));
		} else if (isOptional(value)) {
			return convertObject(getOptionalValue(value));
		} else if (value == null) {
			return getDefaultValue();
		} else if (value instanceof Character) {
			return (Character) value;
		} else if (value instanceof CharSequence) {
			final CharSequence text = (CharSequence) value;
			if (text.length() == 0) {
				return getDefaultValue();
			} else if (text.length() == 1) {
				return text.charAt(0);
			}
		}
		throw new ConverterException("Expected a Character or a single UTF-16 code unit: " + value);
	}

	@Override
	public String format(final Character value) {
		return value == null ? null : value.toString();
	}

	@Override
	public Character copy(final Object value) {
		return convertObject(value);
	}
}
