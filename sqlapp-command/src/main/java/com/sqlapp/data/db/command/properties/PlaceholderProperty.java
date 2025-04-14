/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.properties;

public interface PlaceholderProperty {

	/**
	 * @return the placeholderPrefix
	 */
	String getPlaceholderPrefix();

	/**
	 * @param placeholderPrefix the placeholderPrefix to set
	 */
	void setPlaceholderPrefix(final String placeholderPrefix);

	/**
	 * @return the placeholderSuffix
	 */
	String getPlaceholderSuffix();

	/**
	 * @param placeholderSuffix the placeholderSuffix to set
	 */
	void setPlaceholderSuffix(final String placeholderSuffix);

	/**
	 * @return the placeholders
	 */
	boolean isPlaceholders();

	/**
	 * @param placeholders the placeholders to set
	 */
	void setPlaceholders(final boolean placeholders);

}
