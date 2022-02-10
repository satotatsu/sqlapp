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

import java.math.BigInteger;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.converter.BigIntegerConverter;
import com.sqlapp.data.converter.Converter;

public abstract class StringToBigIntegerSetValue<T> extends AbstractSetValue<T, String>{

	private Converter<BigInteger> converter=new BigIntegerConverter();
	
	@Override
	public void setValue(T target, String name, String setValue) throws XMLStreamException {
		BigInteger val=converter.convertObject(setValue);
		if (val==null){
			raiseError(target, name, setValue);
		}
		if (val!=null){
			setValue(target, val);
		}
	}

	protected abstract void setValue(T target, BigInteger val)throws XMLStreamException ;
	
	@Override
	public String toString(){
		return StringToBigIntegerSetValue.class.getName();
	}
}
