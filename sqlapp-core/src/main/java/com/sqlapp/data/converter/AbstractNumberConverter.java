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
package com.sqlapp.data.converter;

import java.text.NumberFormat;
import java.text.ParseException;

import static com.sqlapp.util.CommonUtils.*;

/**
 * @author SATOH
 *
 */
public abstract class AbstractNumberConverter<T extends Number> extends AbstractConverter<T>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -4360446065570122729L;
	/**
	 * number format
	 */
	private NumberFormat numberFormat=null;
	
	protected synchronized Number parse(String value){
		try {
			return numberFormat.parse(value);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
	}

	public synchronized String format(Number value){
		return numberFormat.format(value);
	}

	public NumberFormat getNumberFormat() {
		return numberFormat;
	}

	public void setNumberFormat(NumberFormat numberFormat) {
		this.numberFormat = numberFormat;
		if (numberFormat!=null){
			this.numberFormat.setParseIntegerOnly(this.getParseIntegerOnly());
		}
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (obj==this){
			return true;
		}
		if (!(obj instanceof AbstractNumberConverter)){
			return false;
		}
		AbstractNumberConverter<?> con=cast(obj);
		if (!eq(this.getNumberFormat(), con.getNumberFormat())){
			return false;
		}
		return true;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode(){
		return this.getClass().getName().hashCode();
	}
	
	protected abstract boolean getParseIntegerOnly();
	
	protected String trim(String text){
		String val=text.trim();
		if (val.startsWith("+")){
			return val.substring(1);
		}
		return val;
	}
}
