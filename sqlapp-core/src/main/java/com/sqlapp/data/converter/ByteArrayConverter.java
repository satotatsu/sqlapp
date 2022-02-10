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

import java.io.UnsupportedEncodingException;

/**
 * byte配列のコンバータクラス
 * @author SATOH
 *
 */
public class ByteArrayConverter extends AbstractBinaryConverter{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 491425127888279448L;


	public ByteArrayConverter() {
	}

	public ByteArrayConverter(final ByteConverter unitConverter) {
		super(unitConverter);
	}

	/**
	 * バイナリの文字エンコーディング
	 */
	private String charset="UTF8";

	@Override
	protected byte[] stringToBinary(final String value){
		if (value==null){
			return null;
		}
		try {
			return value.getBytes(charset);
		} catch (final UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected String binaryToString(final byte[] value) {
		try {
			return new String(value, charset);
		} catch (final UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	public String getCharset() {
		return charset;
	}

	public void setCharset(final String charset) {
		this.charset = charset;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj){
		if (!super.equals(this)){
			return false;
		}
		if (!(obj instanceof ByteArrayConverter)){
			return false;
		}
		final ByteArrayConverter con=cast(obj);
		if (!eq(this.getCharset(), con.getCharset())){
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
