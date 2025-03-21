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
public interface MaxValueProperty<T> {

	BigInteger getMaxValue();

	T setMaxValue(BigInteger value);

	@SuppressWarnings("unchecked")
	default T setMaxValue(Number value){
		setMaxValue(Converters.getDefault().convertObject(value, BigInteger.class));
		return (T)this;
	}

	@SuppressWarnings("unchecked")
	default T setMaxValue(long value){
		setMaxValue(Converters.getDefault().convertObject(value, BigInteger.class));
		return (T)this;
	}

}
