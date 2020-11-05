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
import java.util.Iterator;

import static com.sqlapp.util.CommonUtils.*;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.ToStringBuilder;

public abstract class AbstractObjectIterator<T, S> implements Iterator<T>, Serializable, Cloneable{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;
	protected final T start;
	protected final T end;
	protected final S step;
	protected T current;
	protected T next;
	protected boolean first=true;
	protected long count=1;
	/**
	 * 開始、終了、ステップを指定するコンストラクタ
	 * @param start 開始
	 * @param end 終了
	 * @param step ステップ(秒単位)
	 */
	public AbstractObjectIterator(final T start, final T end, final S step){
		assert (start!=null);
		assert (end!=null);
		assert (step!=null);
		this.start=start;
		this.end=end;
		this.step=step;
		this.current=start;
		this.next=getNext();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString(){
		ToStringBuilder builder=new ToStringBuilder(this.getClass());
		builder.add("start", start);
		builder.add("end", end);
		builder.add("step", step);
		builder.add("current", current);
		return builder.toString();
	}

	@Override
	public void remove() {
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode(){
		return CommonUtils.hashCode(this.start, this.end, this.step, this.current);
	}

	/* (non-Javadoc)
	 * @see java.util.Iterator#next()
	 */
	@Override
	public T next() {
		if (first){
			first=false;
		} else{
			this.current=this.next;
			this.next=getNext();
		}
		count++;
		return this.current;
	}

	protected abstract T getNext();
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (obj==null){
			return false;
		}
		if (obj==this){
			return true;
		}
		if (!(obj instanceof AbstractObjectIterator)){
			return true;
		}
		AbstractObjectIterator<?,?> cst=(AbstractObjectIterator<?,?>)obj;
		if (eq(cst.start, this.start)){
			return false;
		}
		if (eq(cst.end, this.end)){
			return false;
		}
		if (eq(cst.step, this.step)){
			return false;
		}
		if (eq(cst.current, this.current)){
			return false;
		}
		return true;
	}
}
