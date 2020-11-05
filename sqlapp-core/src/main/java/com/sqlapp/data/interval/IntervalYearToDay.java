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
 * INTERVAL YAER TO DAY型
 * @author satoh
 *
 */
public final class IntervalYearToDay extends Interval{

    /**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 5576144904244107604L;

	/**
     * コンストラクタ
     */
	public IntervalYearToDay(){
    }
	
	/**
	 * コンストラクタ
	 * @param years
	 * @param months
	 * @param days
	 */
	public IntervalYearToDay(int years, int months, int days){
		super(years, months, days,0,0,0);
	}

	/**
	 * コンストラクタ 
	 * @param years
	 * @param months
	 * @param days
	 * @param hours 
	 * @param minutes
	 * @param seconds
	 * @param nanos
	 */
    @SuppressWarnings("unused")
    private IntervalYearToDay(int years, int months
    		, int days, int hours
    		, int minutes, int seconds
    		, long nanos){
    	super(years, months
        		, days, hours
        		, minutes, seconds
        		, nanos);
    }

	/**
	 * コンストラクタ 
	 * @param years
	 * @param months
	 * @param days
	 * @param hours 
	 * @param minutes
	 * @param seconds
	 */
    @SuppressWarnings("unused")
    private IntervalYearToDay(int years, int months
    		, int days, int hours
    		, int minutes, double seconds){
    	super(years, months
        		, days, hours
        		, minutes, seconds);
    }

    /**
     * コンストラクタ
     * @param years
     * @param months
     * @param days
     * @param hours
     * @param minutes
     * @param seconds
     */
    @SuppressWarnings("unused")
	private IntervalYearToDay(int years, int months
    		, int days, int hours
    		, int minutes, int seconds){
        super(years, months
        		, days, hours
        		, minutes, seconds
        		, 0);
    }

    /**
     * IntervalからIntervalYearToDayへの変換
     * @param interval
     */
    public static IntervalYearToDay toYearToDayType(final Interval interval){
    	if (interval==null){
    		return null;
    	}
    	IntervalYearToDay result=new IntervalYearToDay(interval.getYears(), interval.getMonths(), interval.getDays());
    	if (!interval.isPositive()){
    		result.scale(-1);
    	}
    	return result;
    }

    /**
     * 文字列からIntervalYearToDayの構築
     * @param val
     */
    public static IntervalYearToDay parse(final String val){
    	if (isEmpty(val)){
    		return null;
    	}
    	Interval interval=Interval.parseDetail(val);
    	if (interval==null){
    		interval=Interval.parse(val, YEAR, DAY);
    	}
    	return toYearToDayType(interval);
    }
    
	/**
	 * @param hours the hours to set
	 */
    @Override
	public void setHours(int hours) {
        super.setHours(truncate(hours, HOUR_LIMIT));
	}

	/**
	 * @param hours the hours to set
	 */
    @Override
	public void setHours(double hours) {
        setHours((int)hours);
	}

	/**
	 * @param minutes the minutes to set
	 */
    @Override
	public void setMinutes(int minutes) {
        super.setMinutes(truncate(minutes, HOUR_LIMIT*MINUTE_LIMIT));
    }

	/**
	 * @param minutes the minutes to set
	 */
    @Override
	public void setMinutes(double minutes) {
        super.setMinutes(minutes);
	}

	/**
	 * @param seconds the seconds to set
	 */
    @Override
	public void setSeconds(int seconds) {
        super.setSeconds(truncate(seconds, DAY_SECONDS));
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
	public void setNanos(long nanos) {
        super.setNanos(truncate(nanos, NANO_LIMIT*DAY_SECONDS));
    }

    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public IntervalYearToDay clone(){
		return (IntervalYearToDay)super.clone();
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
    	builder.append(this.getYears());
    	builder.append("-");
    	builder.append(this.getMonths());
    	builder.append("-");
    	builder.append(this.getDays());
    	builder.append("");
    	return builder.toString();
    }
}
