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

import static com.sqlapp.util.CommonUtils.*;

/**
 * INTERVAL DAY TO MINUTE型
 * @author satoh
 *
 */
public class IntervalDayToMinute extends IntervalDayToSecond{

    /**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -607286796441334304L;

	/**
     * コンストラクタ
     */
	public IntervalDayToMinute(){
    }

	/**
	 * コンストラクタ 
	 * @param days
	 * @param hours 
	 * @param minutes
	 * @param seconds
	 */
	@SuppressWarnings("unused")
	private IntervalDayToMinute(int days, int hours
    		, int minutes, double seconds){
    	super(days, hours
        		, minutes, seconds);
    }

    /**
     * コンストラクタ
     * @param days
     * @param hours
     * @param minutes
     * @param seconds
     */
	@SuppressWarnings("unused")
	private IntervalDayToMinute(int days, int hours
    		, int minutes, int seconds){
        super(days, hours
        		, minutes, seconds
        		, 0);
    }

    /**
     * コンストラクタ
     * @param days
     * @param hours
     * @param minutes
     */
	public IntervalDayToMinute(int days, int hours
    		, int minutes){
        super(days, hours
        		, minutes, 0
        		, 0);
    }

    /**
     * IntervalからIntervalDayToMinuteへの変換
     * @param interval
     */
    public static IntervalDayToMinute toDayToMinuteType(final Interval interval){
    	if (interval==null){
    		return null;
    	}
    	IntervalDayToMinute result=new IntervalDayToMinute(interval.getDays()
    			, interval.getHours(), interval.getMinutes());
    	if (!interval.isPositive()){
    		result.scale(-1);
    	}
    	return result;
    }
    
    
    /**
     * 文字列からIntervalDayToMinuteの構築
     * @param val
     */
    public static IntervalDayToMinute parse(final String val){
    	if (isEmpty(val)){
    		return null;
    	}
    	Interval interval=Interval.parseDetail(val);
    	if (interval==null){
    		interval=Interval.parse(val, DAY, MINUTE);
    	}
    	return toDayToMinuteType(interval);
    }
    
	/**
	 * @param seconds the seconds to set
	 */
    @Override
	public void setSeconds(int seconds) {
        super.setSeconds(truncate(seconds, MINUTE_SECONDS));
    }

	/**
	 * @param seconds the seconds to set
	 */
    @Override
	public void setSeconds(double seconds) {
    	setSeconds((int)seconds);
	}

	/**
	 * @param nanos the nanos to set
	 */
    @Override
	public void setNanos(int nanos) {
        super.setNanos(0);
    }

	/**
	 * @param nanos the nanos to set
	 */
    @Override
	public void setNanos(long nanos) {
        super.setNanos(0);
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public IntervalDayToMinute clone(){
		return (IntervalDayToMinute)super.clone();
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
    	builder.append(this.getDays());
    	builder.append(" ");
    	builder.append(this.getHours());
    	builder.append(":");
    	builder.append(this.getMinutes());
    	return builder.toString();
    }
}
