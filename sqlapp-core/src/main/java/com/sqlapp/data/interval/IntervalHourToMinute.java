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

package com.sqlapp.data.interval;

import static com.sqlapp.util.CommonUtils.isEmpty;

/**
 * INTERVAL HOUR TO MINUTE型
 * @author satoh
 *
 */
public final class IntervalHourToMinute extends IntervalHourToSecond{
    
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -8236104654148730695L;
	
	/**
	 * コンストラクタ 
	 * @param hours 
	 * @param minutes
	 */
	public IntervalHourToMinute(int hours
    		, int minutes){
    	super(hours
        		, minutes, 0
        		, 0);
    }

    /**
     * IntervalからIntervalHourToMinuteへの変換
     * @param interval
     */
    public static IntervalHourToMinute toHourToMinuteType(final Interval interval){
    	if (interval==null){
    		return null;
    	}
    	IntervalHourToMinute result=new IntervalHourToMinute(
    			interval.getHoursFull()
    			, interval.getMinutes());
    	if (!interval.isPositive()){
    		result.scale(-1);
    	}
    	return result;
    }
    
    
    /**
     * 文字列からIntervalHourToMinuteの構築
     * @param val
     */
    public static IntervalHourToMinute parse(final String val){
    	if (isEmpty(val)){
    		return null;
    	}
    	Interval interval=Interval.parseDetail(val);
    	if (interval==null){
    		interval=Interval.parse(val, HOUR, MINUTE);
    	}
    	return toHourToMinuteType(interval);
    }
    
	/**
	 * @param seconds the seconds to set
	 */
    @Override
	public void setSeconds(int seconds) {
    	super.setSeconds(0);
    }

	/**
	 * @param seconds the seconds to set
	 */
    @Override
	public void setSeconds(double seconds) {
    	super.setSeconds(0);
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
    public IntervalHourToMinute clone(){
		return (IntervalHourToMinute)super.clone();
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
    	builder.append(this.getHoursFull());
    	builder.append(":");
    	builder.append(this.getMinutes());
    	builder.append("");
    	return builder.toString();
    }
}
