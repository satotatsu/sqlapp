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

import java.util.Calendar;

/**
 * For文の代わりに使用する<code>java.util.Calendar</code>のIterator
 * @author satoh
 *
 */
final class CalendarIterator extends AbstractObjectIterator<Calendar, Integer>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;
	/**
	 * 開始、終了、ステップを指定するコンストラクタ
	 * @param start
	 * @param end
	 */
	public CalendarIterator(final Calendar start, final Calendar end){
		this(start, end, 3600*24);
	}

	/**
	 * 開始、終了、ステップを指定するコンストラクタ
	 * @param start 開始
	 * @param end 終了
	 * @param step ステップ(秒単位)
	 */
	public CalendarIterator(final Calendar start, final Calendar end, final Integer step){
		super(start, end, step);
	}

	/* (non-Javadoc)
	 * @see java.util.Iterator#hasNext()
	 */
	@Override
	public boolean hasNext() {
		if (this.step>0){
			return (next.compareTo(end)<0);
		} else{
			return (next.compareTo(end)>0);
		}
	}

	@Override
	protected Calendar getNext() {
		Calendar cal=(Calendar)this.current.clone();
		cal.add(Calendar.SECOND, step.intValue());
		return cal;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	@Override
	public CalendarIterator clone(){
		CalendarIterator clone=new CalendarIterator(this.start, this.end, this.step);
		return clone;
	}
}
