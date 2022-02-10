/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.util;

import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.converter.NewValue;

public class ValueUtils {
	
	private Converters converters=new Converters();

	public ValueUtils(){
		setValue(String.class, "a");
		setValue(Long.class, 1L);
		setValue(Integer.class, 1);
		setValue(Short.class, (short)1);
		setValue(Byte.class, (byte)1);
		setValue(Double.class, 1.0);
		setValue(Float.class, 1.0f);
		setValue(Boolean.class, true);
	}

	@SuppressWarnings("unchecked")
	public <T> T getDefaultValue(Class<T> clazz){
		Converter<T> converter=converters.getConverter(clazz);
		if (converter instanceof NewValue){
			return ((NewValue<T>)converter).newValue();
		}
		return converter.getDefaultValue();
	}

	public <T> void setValue(Class<T> clazz, T value){
		converters.getConverter(clazz).setDefaultValue(value);
	}
	
}
