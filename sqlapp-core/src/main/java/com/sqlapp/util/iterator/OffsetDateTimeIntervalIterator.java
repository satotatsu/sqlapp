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

import java.time.OffsetDateTime;

import com.sqlapp.data.interval.Interval;

/**
 * For文の代わりに使用する<code>java.time.OffsetDateTime</code>のIterator
 * @author satoh
 *
 */
final class OffsetDateTimeIntervalIterator extends AbstractObjectIterator<OffsetDateTime, Interval>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * 開始、終了、ステップを指定するコンストラクタ
	 * @param start 開始
	 * @param end 終了
	 * @param step ステップ
	 */
	public OffsetDateTimeIntervalIterator(final OffsetDateTime start, final OffsetDateTime end, final Interval step){
		super(start, end, step);
	}

	/* (non-Javadoc)
	 * @see java.util.Iterator#hasNext()
	 */
	@Override
	public boolean hasNext() {
		if (this.step.isPositive()){
			return (next.compareTo(end)<0);
		} else{
			return (next.compareTo(end)>0);
		}
	}

	@Override
	protected OffsetDateTime getNext() {
		return step.add(start, (int)this.count);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	@Override
	public OffsetDateTimeIntervalIterator clone(){
		OffsetDateTimeIntervalIterator clone=new OffsetDateTimeIntervalIterator(this.start, this.end, this.step);
		return clone;
	}
}
