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
 * INTERVAL HOUR TO SECOND型
 * @author satoh
 *
 */
public class IntervalHourToSecond extends IntervalDayToSecond{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -7722837328367021543L;

    
	/**
	 * コンストラクタ 
	 * @param days
	 * @param hours 
	 * @param minutes
	 * @param seconds
	 * @param nanos
	 */
	@SuppressWarnings("unused")
	private IntervalHourToSecond(int days, int hours
    		, int minutes, int seconds
    		, long nanos){
    	super(days, hours
        		, minutes, seconds
        		, nanos);
    }

	/**
	 * コンストラクタ 
	 * @param hours 
	 * @param minutes
	 * @param seconds
	 * @param nanos
	 */
	public IntervalHourToSecond(int hours
    		, int minutes, int seconds
    		, long nanos){
    	super(0, hours
        		, minutes, seconds
        		, nanos);
    }

	/**
	 * コンストラクタ 
	 * @param hours 
	 * @param minutes
	 * @param seconds
	 */
    public IntervalHourToSecond(int hours
    		, int minutes, double seconds){
    	super(0, hours
        		, minutes, seconds);
    }

    /**
     * コンストラクタ
     * @param hours
     * @param minutes
     * @param seconds
     */
    public IntervalHourToSecond(int hours
    		, int minutes, int seconds){
        super(0, hours
        		, minutes, seconds
        		, 0);
    }

    /**
     * IntervalからIntervalHourToSecondへの変換
     * @param interval
     */
    public static IntervalHourToSecond toHourToSecondType(final Interval interval){
    	if (interval==null){
    		return null;
    	}
    	IntervalHourToSecond result=new IntervalHourToSecond(
    			  interval.getHoursFull()
    			, interval.getMinutes()
    			, interval.getSeconds(), interval.getNanos());
    	if (!interval.isPositive()){
    		result.scale(-1);
    	}
    	return result;
    }

    /**
     * 文字列からIntervalHourToSecondの構築
     * @param val
     */
    public static IntervalHourToSecond parse(final String val){
    	if (isEmpty(val)){
    		return null;
    	}
    	Interval interval=Interval.parseDetail(val);
    	if (interval==null){
    		interval=Interval.parse(val, HOUR, SECOND);
    	}
    	return toHourToSecondType(interval);
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public IntervalHourToSecond clone(){
		return (IntervalHourToSecond)super.clone();
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
    	builder.append(":");
    	if (this.getNanos()>0){
    		synchronized(secondsFormat){
    			double val=(double)this.getNanos()/NANO_LIMIT+this.getSeconds();
    			builder.append(secondsFormat.format(val));
    		}
    	} else{
        	builder.append(this.getSeconds());
    	}
    	builder.append("");
    	return builder.toString();
    }
}
