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

package com.sqlapp.util.iterator;

import java.io.Serializable;
import java.util.Iterator;

import com.sqlapp.util.ToStringBuilder;
/**
 * For文の代わりに使用するIterator
 * @author satoh
 *
 */
final class IntegerIterator implements Iterator<Integer>, Serializable, Cloneable{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;
	private final int start;
	private final int end;
	private final int step;
	private int current=0;
	private boolean first=true;
	
	/**
	 * 終了を指定するコンストラクタ
	 * @param end
	 */
	public IntegerIterator(int end){
		this(0, end,1);
	}

	/**
	 * 開始、終了を指定するコンストラクタ
	 * @param start
	 * @param end
	 */
	public IntegerIterator(int start, int end){
		this(start, end,1);
	}

	/**
	 * 開始、終了、ステップを指定するコンストラクタ
	 * @param start 開始
	 * @param end 終了
	 * @param step ステップ
	 */
	public IntegerIterator(int start, int end, int step){
		assert (step!=0);
		assert ((start<end&&step>0)||(start>end&&step<0));
		this.start=start;
		this.end=end;
		this.step=step;
		this.current=start;
	}

	@Override
	public boolean hasNext() {
		if (this.step>0){
			return ((current+step)<end);
		} else{
			return ((current+step)>end);
		}
	}

	@Override
	public Integer next() {
		if (first){
			first=false;
			return current;
		}
		current=current+step;
		return current;
	}

	@Override
	public void remove() {
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString(){
		ToStringBuilder builder=new ToStringBuilder(IntegerIterator.class);
		builder.add("start", start);
		builder.add("end", end);
		builder.add("step", step);
		builder.add("current", current);
		return builder.toString();
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode(){
		return this.start^this.end^this.step^this.current;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	@Override
	public IntegerIterator clone(){
		IntegerIterator clone=new IntegerIterator(this.start, this.end, this.step);
		return clone;
	}
}
