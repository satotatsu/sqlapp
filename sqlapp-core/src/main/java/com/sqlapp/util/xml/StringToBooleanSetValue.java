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

package com.sqlapp.util.xml;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.converter.BooleanConverter;
import com.sqlapp.data.converter.Converter;

public abstract class StringToBooleanSetValue<T> extends AbstractSetValue<T, String>{

	private Converter<Boolean> converter=new BooleanConverter();
	
	@Override
	public void setValue(T target, String name, String setValue) throws XMLStreamException {
		Boolean val=converter.convertObject(setValue);
		if (val==null){
			raiseError(target, name, setValue);
		}
		if (val!=null){
			setValue(target, val);
		}
	}

	protected abstract void setValue(T target, boolean bool)throws XMLStreamException ;
	
	@Override
	public String toString(){
		return StringToBooleanSetValue.class.getName();
	}
}
