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

import static com.sqlapp.util.CommonUtils.*;

/**
 * INTERVAL DAY TO SECOND型
 * @author satoh
 *
 */
public class IntervalDayToSecond extends Interval{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -6661727596902448239L;

    /**
     * コンストラクタ
     */
	public IntervalDayToSecond(){
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
	private IntervalDayToSecond(int years, int months
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
    private IntervalDayToSecond(int years, int months
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
    private IntervalDayToSecond(int years, int months
    		, int days, int hours
    		, int minutes, int seconds){
        super(years, months
        		, days, hours
        		, minutes, seconds
        		, 0);
    }
    
	/**
	 * コンストラクタ 
	 * @param days
	 * @param hours 
	 * @param minutes
	 * @param seconds
	 * @param nanos
	 */
	public IntervalDayToSecond(int days, int hours
    		, int minutes, int seconds
    		, long nanos){
    	super(0, 0
        		, days, hours
        		, minutes, seconds
        		, nanos);
    }

	/**
	 * コンストラクタ 
	 * @param days
	 * @param hours 
	 * @param minutes
	 * @param seconds
	 */
    public IntervalDayToSecond(int days, int hours
    		, int minutes, double seconds){
    	super(0, 0
        		, days, hours
        		, minutes, seconds);
    }

    /**
     * コンストラクタ
     * @param days
     * @param hours
     * @param minutes
     * @param seconds
     */
    public IntervalDayToSecond(int days, int hours
    		, int minutes, int seconds){
        super(0, 0
        		, days, hours
        		, minutes, seconds
        		, 0);
    }

    /**
     * IntervalからIntervalDayToSecondへの変換
     * @param interval
     */
    public static IntervalDayToSecond toDayToSecondType(final Interval interval){
    	if (interval==null){
    		return null;
    	}
    	IntervalDayToSecond result=new IntervalDayToSecond(interval.getDays()
    			, interval.getHours(), interval.getMinutes()
    			, interval.getSeconds(), interval.getNanos());
    	if (!interval.isPositive()){
    		result.scale(-1);
    	}
    	return result;
    }
    
    
    /**
     * 文字列からIntervalDayToSecondの構築
     * @param val
     */
    public static IntervalDayToSecond parse(final String val){
    	if (isEmpty(val)){
    		return null;
    	}
    	Interval interval=Interval.parseDetail(val);
    	if (interval==null){
    		interval=Interval.parse(val, DAY, SECOND);
    	}
    	return toDayToSecondType(interval);
    }

	/**
	 * @param years the years to set
	 */
    @Override
	public void setYears(int years) {
	}

	/**
	 * @param years the years to set
	 */
    @Override
	public void setYears(double years) {
	}
    
	/**
	 * @param months the months to set
	 */
    @Override
	public void setMonths(int months) {
	}
    
    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public IntervalDayToSecond clone(){
		return (IntervalDayToSecond)super.clone();
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
    	builder.append(":");
    	builder.append(this.getSeconds());
    	if (this.getNanos()>0){
        	builder.append(".");
	    	builder.append(this.getNanos()/NANO_LIMIT);
    	}
    	return builder.toString();
    }
}
