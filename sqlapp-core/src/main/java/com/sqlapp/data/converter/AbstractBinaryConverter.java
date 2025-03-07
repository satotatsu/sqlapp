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

import static com.sqlapp.util.BinaryUtils.toBinary;

import java.io.InputStream;
import java.lang.reflect.Array;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import com.sqlapp.util.BinaryUtils;
import com.sqlapp.util.FileUtils;
/**
 * @author SATOH
 *
 */
public abstract class AbstractBinaryConverter extends AbstractConverter<byte[]>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6419521281514049281L;

	private ByteConverter unitConverter;

	protected AbstractBinaryConverter() {
		this(new ByteConverter());
	}

	protected AbstractBinaryConverter(final ByteConverter unitConverter) {
		this.unitConverter=unitConverter;
	}
	
	@Override
	public byte[] convertObject(final Object value, final Connection conn) {
		return convertObject(value);
	}
	
	@Override
	public byte[] convertObject(final Object value) {
		if (value==null){
			return getDefaultValue();
		}
		if (value instanceof byte[]){
			return (byte[])value;
		} else if (value instanceof Blob){
			final Blob lob=(Blob)value;
			try {
				return lob.getBytes(1, (int)lob.length());
			} catch (final SQLException e) {
				throw new RuntimeException(e);
			}
		} else if (value instanceof String){
			return stringToBinary((String)value);
		} else if (value instanceof InputStream){
			final InputStream is=InputStream.class.cast(value);
			try{
				return BinaryUtils.toBinary(is);
			} finally{
				FileUtils.close(is);
			}
		} else if (value instanceof UUID){
			return BinaryUtils.toBinary((UUID)value);
		} else if (value instanceof Long){
			return BinaryUtils.toBinary(((Long)value).longValue());
		} else if (value.getClass().isArray()){
			final int size=Array.getLength(value);
			final byte[] bytes=new byte[size];
			for(int i=0;i<size;i++) {
				final byte b=objectToByte(Array.get(value, i));
				bytes[i]=b;
			}
			return bytes;
		}
		return toBinary(value);
	}

	private static byte zero=(byte)0;
	
	protected byte objectToByte(final Object value) {
		if (value==null) {
			return zero;
		}
		final Byte bt= unitConverter.convertObject(value);
		if (bt==null) {
			return zero;
		}
		return bt;
	}

	protected abstract byte[] stringToBinary(String value);
	protected abstract String binaryToString(byte[] value);

	@Override
	public String convertString(final byte[] value) {
		if (value==null){
			return null;
		}
		return binaryToString(value);
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof AbstractBinaryConverter)){
			return false;
		}
		return true;
	}

	public ByteConverter getUnitConverter() {
		return unitConverter;
	}

	public void setUnitConverter(final ByteConverter unitConverter) {
		this.unitConverter = unitConverter;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode(){
		return this.getClass().getName().hashCode();
	}
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	@Override
	public byte[] copy(final Object obj){
		if (obj==null){
			return null;
		}
		final byte[] cnv=convertObject(obj);
		final byte[] result=new byte[cnv.length];
		System.arraycopy(cnv, 0, result, 0, cnv.length);
		return result;
	}
}
