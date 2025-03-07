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

import static com.sqlapp.util.DateUtils.addSeconds;

import java.sql.Time;

/**
 * For文の代わりに使用する<code>java.sql.Time</code>のIterator
 * @author satoh
 *
 */
final class TimeIterator extends AbstractObjectIterator<Time, Integer>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;
	/**
	 * 開始、終了、ステップを指定するコンストラクタ
	 * @param start
	 * @param end
	 */
	public TimeIterator(final Time start, final Time end){
		this(start, end, 3600);
	}

	/**
	 * 開始、終了、ステップを指定するコンストラクタ
	 * @param start 開始
	 * @param end 終了
	 * @param step ステップ(秒単位)
	 */
	public TimeIterator(final Time start, final Time end, final Integer step){
		super(start, end, step);
	}

	/* (non-Javadoc)
	 * @see java.util.Iterator#hasNext()
	 */
	@Override
	public boolean hasNext() {
		if (this.step>0){
			if (this.start.compareTo(this.next)>=0){
				return false;
			}else{
				if (next.compareTo(end)<0){
					return true;
				}
			}
		} else{
			if (this.start.compareTo(this.next)<=0){
				return false;
			}else{
				if (next.compareTo(end)>0){
					return true;
				}
			}
		}
		return false;
	}

	@Override
	protected Time getNext() {
		return addSeconds(this.current, step.intValue());
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	@Override
	public TimeIterator clone(){
		TimeIterator clone=new TimeIterator(this.start, this.end, this.step);
		return clone;
	}
}
