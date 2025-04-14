/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.gradle.plugins.properties;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.converter.Converters;

/**
 * GenerateSql用のExtension
 */
public interface GenerateSqlTaskProperties {
	/**
	 * 複数ファイル出力
	 */
	@Input
	@Optional
	abstract Property<Boolean> getOutputAsMultiFiles();

	@Input
	@Optional
	abstract Property<String> getOutputFileExtension();

	@Input
	@Optional
	abstract Property<Object> getLastChangeNumber();

	@Input
	@Optional
	abstract Property<Object> getChangeNumberStep();

	@Input
	@Optional
	abstract Property<Object> getNumberOfDigits();

	@Internal
	public default Long getOrElseLastChangeNumber() {
		if (getLastChangeNumber().isPresent()) {
			return Converters.getDefault().convertObject(getLastChangeNumber().get(), long.class);
		}
		return null;
	}

	@Internal
	public default long getOrElseChangeNumberStep() {
		if (getChangeNumberStep().isPresent()) {
			return Converters.getDefault().convertObject(getChangeNumberStep().get(), long.class);
		}
		return 10;
	}

	@Internal
	public default int getOrElseNumberOfDigits() {
		if (getNumberOfDigits().isPresent()) {
			return Converters.getDefault().convertObject(getNumberOfDigits().get(), int.class);
		}
		return 19;
	}
}
