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

package com.sqlapp.data.interval;

import static com.sqlapp.util.CommonUtils.isEmpty;
/**
 * INTERVAL MINUTE型
 * @author satoh
 *
 */
public class IntervalMinute extends Interval{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -5794364986820692508L;

	/**
     * コンストラクタ
     */
	public IntervalMinute(){
    }

	/**
	 * コンストラクタ 
	 * @param minutes
	 */
    public IntervalMinute(final int minutes){
        super(0, 0
        		, 0, 0
        		, minutes, 0
        		, 0);
    }

	/**
	 * @return the minutes
	 */
    @Override
	public int getMinutes() {
		return super.getMinutesFull();
	}

    /**
     * IntervalからIntervalMinuteへの変換
     * @param interval
     */
    public static IntervalMinute toMinuteType(final Interval interval){
    	if (interval==null){
    		return null;
    	}
    	IntervalMinute result=new IntervalMinute(interval.getMinutesFull());
    	if (!interval.isPositive()){
    		result.scale(-1);
    	}
    	return result;
    }

    /**
     * 文字列からIntervalMinuteの構築
     * @param val
     */
    public static IntervalMinute parse(final String val){
    	if (isEmpty(val)){
    		return null;
    	}
    	Interval interval=Interval.parseDetail(val);
    	if (interval==null){
    		interval=Interval.parse(val, MINUTE, MINUTE);
    	}
    	return toMinuteType(interval);
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public IntervalMinute clone(){
		return (IntervalMinute)super.clone();
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString(){
    	StringBuilder builder=new StringBuilder("");
    	if (!this.isPositive()){
        	builder.append("-");
    	}
    	builder.append(this.getMinutesFull());
    	builder.append("");
    	return builder.toString();
    }
}
