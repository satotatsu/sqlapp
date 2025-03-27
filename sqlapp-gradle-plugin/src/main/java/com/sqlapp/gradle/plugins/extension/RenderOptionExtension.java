/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

import org.gradle.api.Action;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.mvel2.ParserContext;

import com.sqlapp.data.db.command.html.HighlightMethod;
import com.sqlapp.data.db.command.html.RenderOptions;

/**
 * HTMLのRenderOption用のExtension
 */

public interface RenderOptionExtension {

	@Internal
	public default void call(Action<RenderOptionExtension> cons) {
		cons.execute(this);
	}

	/** CDN Scheme */
	@Input
	@Optional
	Property<String> getCdnScheme();

	/** CSS Table class */
	@Input
	@Optional
	Property<String> getTableClass();

	/** ParserContext */
	@Input
	@Optional
	Property<ParserContext> getParserContext();

	/** HighlightMethod */
	@Input
	@Optional
	Property<HighlightMethod> getHighlightMethod();

	/** DateTimeForma */
	@Input
	@Optional
	Property<String> getDateTimeFormat();

	/** CheckIconValue */
	@Input
	@Optional
	Property<String> getCheckIconValue();

	/** CssFrameworkPath */
	@Input
	@Optional
	Property<String> getCssFrameworkPath();

	/** WithJquery */
	@Input
	@Optional
	Property<Boolean> getWithJquery();

	/** WithRows */
	@Input
	@Optional
	Property<Boolean> getWithRows();

	/** HideColumns */
	@Input
	@Optional
	ListProperty<String> getHideColumns();

	@Internal
	public default void setRenderOption(RenderOptions obj) {
		if (getCdnScheme().isPresent()) {
			obj.setCdnScheme(getCdnScheme().get());
		}
		if (getTableClass().isPresent()) {
			obj.setTableClass(getTableClass().get());
		}
		if (getParserContext().isPresent()) {
			obj.setParserContext(getParserContext().get());
		}
		if (getHighlightMethod().isPresent()) {
			obj.setHighlightMethod(getHighlightMethod().get());
		}
		if (getDateTimeFormat().isPresent()) {
			obj.setDateTimeFormat(getDateTimeFormat().get());
		}
		if (getCheckIconValue().isPresent()) {
			obj.setCheckIconValue(getCheckIconValue().get());
		}
		if (getCssFrameworkPath().isPresent()) {
			obj.setCssFrameworkPath(getCssFrameworkPath().get());
		}
		if (getWithJquery().isPresent()) {
			obj.setWithJquery(getWithJquery().get());
		}
		if (getWithRows().isPresent()) {
			obj.setWithRows(getWithRows().get());
		}
		if (getHideColumns().isPresent()) {
			obj.setHideColumns(getHideColumns().get().toArray(new String[0]));
		}
	}
}
