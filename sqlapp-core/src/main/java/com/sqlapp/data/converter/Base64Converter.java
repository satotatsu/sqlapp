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

package com.sqlapp.data.converter;

import static com.sqlapp.util.BinaryUtils.decodeBase64;
import static com.sqlapp.util.BinaryUtils.encodeBase64;

/**
 * @author SATOH
 *
 */
public class Base64Converter extends AbstractBinaryConverter{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -2529076858508701049L;

	public Base64Converter() {
	}

	public Base64Converter(final ByteConverter unitConverter) {
		super(unitConverter);
	}
	
	@Override
	protected byte[] stringToBinary(final String value){
		return decodeBase64(value);
	}

	@Override
	protected String binaryToString(final byte[] value) {
		return encodeBase64(value);
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj){
		if (!super.equals(this)){
			return false;
		}
		if (!(obj instanceof Base64Converter)){
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

}
