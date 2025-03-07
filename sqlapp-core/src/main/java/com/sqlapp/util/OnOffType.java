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

package com.sqlapp.util;

import com.sqlapp.data.converter.BooleanConverter;
import com.sqlapp.data.converter.EnumConvertable;

public enum OnOffType implements EnumConvertable<String>{
	ON(){
		@Override
		public boolean isOn() {
			return true;
		}
	},
	OFF(){
		@Override
		public boolean isOff() {
			return true;
		}
	},;
	
	private static BooleanConverter converter = new BooleanConverter();
	
	static {
		converter.setTrueString("ON");
		converter.setFalseString("OFF");
	}
	
	public boolean isOn() {
		return false;
	}

	public boolean isOff() {
		return false;
	}

	public static OnOffType parse(final Object obj) {
		return parse(obj, OFF);
	}

	public static OnOffType parse(final Object obj, final OnOffType defaultValue) {
		final Boolean bool=converter.convertObject(obj);
		if (bool!=null&&bool.booleanValue()) {
			return ON;
		}
		return defaultValue;
	}

	@Override
	public String getValue() {
		return toString();
	}
}
