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

package com.sqlapp.data.schemas.properties;

import java.math.BigInteger;

import com.sqlapp.data.converter.Converters;

/**
 * 
 * @author satoh
 * 
 */
/**
 * Next value to be issued (the value a recreated sequence starts or restarts
 * with). It can be ahead of the last value actually handed out when the
 * database reports a cache allocation boundary; null means unused or unknown,
 * so the start value applies.
 */
public interface LastValueProperty<T> {

	BigInteger getLastValue();

	T setLastValue(BigInteger value);

	@SuppressWarnings("unchecked")
	default T setLastValue(Number value){
		setLastValue(Converters.getDefault().convertObject(value, BigInteger.class));
		return (T)this;
	}

	@SuppressWarnings("unchecked")
	default T setLastValue(long value){
		setLastValue(Converters.getDefault().convertObject(value, BigInteger.class));
		return (T)this;
	}

}
