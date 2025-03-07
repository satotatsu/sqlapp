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
 * INTERVAL MINUTE TO SECOND型
 * @author satoh
 *
 */
public class IntervalMinuteToSecond extends IntervalHourToSecond{
    
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -3792165414712653431L;
	/**
	 * コンストラクタ 
	 * @param hours 
	 * @param minutes
	 * @param seconds
	 * @param nanos
	 */
	private IntervalMinuteToSecond(int hours
    		, int minutes, int seconds
    		, long nanos){
    	super(hours
        		, minutes, seconds
        		, nanos);
    }

	/**
	 * コンストラクタ 
	 * @param hours 
	 * @param minutes
	 * @param seconds
	 */
	private IntervalMinuteToSecond(int hours
    		, int minutes, double seconds){
    	super(hours
        		, minutes, seconds);
    }

    /**
     * コンストラクタ
     * @param hours
     * @param minutes
     * @param seconds
     */
    private IntervalMinuteToSecond(int hours
    		, int minutes, int seconds){
        super(hours
        		, minutes, seconds
        		, 0);
    }

	/**
	 * コンストラクタ
	 * @param minutes
	 * @param seconds
	 * @param nanos
	 */
	public IntervalMinuteToSecond(
    		int minutes, int seconds
    		, long nanos){
    	super(0	, minutes, seconds
        		, nanos);
    }

	/**
	 * コンストラクタ 
	 * @param minutes
	 * @param seconds
	 */
    public IntervalMinuteToSecond(int minutes, double seconds){
    	super(0, minutes, seconds);
    }

    /**
     * コンストラクタ
     * @param minutes
     * @param seconds
     */
    public IntervalMinuteToSecond(int minutes, int seconds){
        super(0, minutes, seconds
        		, 0);
    }

    /**
     * IntervalからIntervalMinuteToSecondへの変換
     * @param interval
     */
    public static IntervalMinuteToSecond toMinuteToSecondType(final Interval interval){
    	if (interval==null){
    		return null;
    	}
    	IntervalMinuteToSecond result=new IntervalMinuteToSecond(
    			interval.getMinutes()*MINUTE_SECONDS
    			, interval.getSeconds(), interval.getNanos());
    	if (!interval.isPositive()){
    		result.scale(-1);
    	}
    	return result;
    }
    
    
    /**
     * 文字列からIntervalMinuteToSecondの構築
     * @param val
     */
    public static IntervalMinuteToSecond parse(final String val){
    	if (isEmpty(val)){
    		return null;
    	}
    	Interval interval=Interval.parseDetail(val);
    	if (interval==null){
    		interval=Interval.parse(val, MINUTE, SECOND);
    	}
    	return toMinuteToSecondType(interval);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public IntervalMinuteToSecond clone(){
		return (IntervalMinuteToSecond)super.clone();
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
