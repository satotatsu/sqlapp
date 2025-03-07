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
 * INTERVAL DAY TO HOUR型
 * @author satoh
 *
 */
public final class IntervalDayToHour extends IntervalDayToMinute{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 7033299601565739695L;

	/**
	 * コンストラクタ 
	 * @param days
	 * @param hours 
	 * @param minutes
	 */
	@SuppressWarnings("unused")
	private IntervalDayToHour(int days, int hours
    		, int minutes){
    	super(days, hours
        		, 0);
    }
	
	/**
     * コンストラクタ
     */
	public IntervalDayToHour(){
    }

	/**
	 * コンストラクタ 
	 * @param days
	 * @param hours 
	 */
	public IntervalDayToHour(int days, int hours){
    	super(days, hours
        		, 0);
    }

    /**
     * IntervalからIntervalDayToHourへの変換
     * @param interval
     */
    public static IntervalDayToHour toDayToHourType(final Interval interval){
    	if (interval==null){
    		return null;
    	}
    	IntervalDayToHour result=new IntervalDayToHour(interval.getDays()
    			, interval.getHours());
    	if (!interval.isPositive()){
    		result.scale(-1);
    	}
    	return result;
    }
    
    
    /**
     * 文字列からIntervalDayToHourの構築
     * @param val
     */
    public static IntervalDayToHour parse(final String val){
    	if (isEmpty(val)){
    		return null;
    	}
    	Interval interval=Interval.parseDetail(val);
    	if (interval==null){
    		interval=Interval.parse(val, DAY, HOUR);
    	}
    	return toDayToHourType(interval);
    }
    
	/**
	 * @param minutes the minutes to set
	 */
	public void setMinutes(int minutes) {
		super.setMinutes(truncate(minutes, HOUR_MINUTES));
	}

	/**
	 * @param minutes
	 */
	public void setMinutes(double minutes) {
		setMinutes((int)minutes);
	}
    
    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public IntervalDayToHour clone(){
		return (IntervalDayToHour)super.clone();
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
    	return builder.toString();
    }
}
