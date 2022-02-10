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

import java.sql.Timestamp;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.converter.Converters;

public abstract class StringToTimestampSetValue<T> extends AbstractSetValue<T, String>{

	private Converter<Timestamp> converter=Converters.getDefault().getConverter(Timestamp.class);
	
	@Override
	public void setValue(T target, String name, String setValue) throws XMLStreamException {
		Timestamp val=converter.convertObject(setValue);
		if (val==null){
			raiseError(target, name, setValue);
		}
		if (val!=null){
			setValue(target, val);
		}
	}

	protected abstract void setValue(T target, Timestamp ts)throws XMLStreamException ;

	@Override
	public String toString(){
		return StringToTimestampSetValue.class.getName();
	}
}
