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
package com.sqlapp.util.iterator;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Iterator;

import com.sqlapp.util.ToStringBuilder;
/**
 * For文の代わりに使用するIterator
 * @author satoh
 *
 */
final class StringArrayIterator implements Iterator<String>, Serializable, Cloneable{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;
	private final String[] args;
	private int pos=0;

	/**
	 * 文字列の配列を指定するコンストラクタ
	 * @param args 文字列の配列
	 */
	public StringArrayIterator(final String... args){
		assert (args!=null);
		this.args=args;
	}

	/* (non-Javadoc)
	 * @see java.util.Iterator#hasNext()
	 */
	@Override
	public boolean hasNext() {
		return pos<this.args.length;
	}

	/* (non-Javadoc)
	 * @see java.util.Iterator#next()
	 */
	@Override
	public String next() {
		if (this.hasNext()){
			String val=args[pos];
			pos++;
			return val;
		}
		return null;
	}

	@Override
	public void remove() {
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString(){
		ToStringBuilder builder=new ToStringBuilder(StringArrayIterator.class);
		builder.add(Arrays.toString(args));
		return builder.toString();
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode(){
		return (int)(Arrays.hashCode(args));
	}
	
	
	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	@Override
	public StringArrayIterator clone(){
		StringArrayIterator clone=new StringArrayIterator(args);
		return clone;
	}
}
