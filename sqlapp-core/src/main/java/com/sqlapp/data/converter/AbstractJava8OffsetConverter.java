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

import static com.sqlapp.util.CommonUtils.cast;
import static com.sqlapp.util.CommonUtils.eq;

import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;

/**
 * java.time.LocalDateTime converter
 */
public abstract class AbstractJava8OffsetConverter<T extends Temporal, S> extends AbstractJava8DateConverter<T, S>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1212274814940098554L;
	/**
	 */
	private boolean utc = false;

	@Override
	public String convertString(T value) {
		if (value ==null){
			return null;
		}
		DateTimeFormatter format=this.getFormat();
		if (format == null) {
			return toUtc(value).toString();
		}
		String result=convertUtcFormat(format(toUtc(value), format));
		return result;
	}
	
	protected abstract T toUtc(T dateTime);

	/**
	 * @return the utc
	 */
	public boolean isUtc() {
		return utc;
	}

	/**
	 * @param utc
	 *            the utc to set
	 * @return this(Fluent Interface)
	 */
	public S setUtc(boolean utc) {
		this.utc = utc;
		return this.instance();
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof AbstractJava8OffsetConverter)){
			return false;
		}
		AbstractJava8OffsetConverter<?,?> con=cast(obj);
		if (!eq(this.isUtc(), con.isUtc())){
			return false;
		}
		return true;
	}

	@Override
	public AbstractJava8OffsetConverter<T,S> clone(){
		return (AbstractJava8OffsetConverter<T,S>)super.clone();
	}
}
