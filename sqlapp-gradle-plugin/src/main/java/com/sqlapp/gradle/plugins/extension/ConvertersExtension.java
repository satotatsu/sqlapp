/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-gradle-plugin.
 *
 * sqlapp-gradle-plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-gradle-plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-gradle-plugin.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.gradle.plugins.extension;

import org.gradle.api.tasks.Internal;

import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.converter.Converters;

public abstract class ConvertersExtension {

	@Internal
	private Converters converters = new Converters();

	public Converters getConverters() {
		return this.converters;
	}

	/**
	 * 
	 * @param clazz
	 * @param converter
	 */
	public void put(final Class<?> clazz, final Converter<?> converter) {
		this.converters.put(clazz, converter);
	}

	/**
	 * 
	 * @param name
	 * @param converter
	 */
	public void put(final String name, final Converter<?> converter) {
		this.converters.put(name, converter);
	}

	/**
	 * @param enumEmptyToNull the enumEmptyToNull to set
	 */
	public void setEnumEmptyToNull(final boolean enumEmptyToNull) {
		this.converters.setEnumEmptyToNull(enumEmptyToNull);
	}
}
