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

import java.time.YearMonth;

/**
 * INTERVAL YAER TO MONTH型
 * @author satoh
 *
 */
public class IntervalYearToMonth extends Interval{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 4409821325136133414L;

    /**
     * コンストラクタ
     */
	public IntervalYearToMonth(){
    }
	
	/**
	 * コンストラクタ
	 * @param years
	 * @param months
	 */
	public IntervalYearToMonth(final int years, final int months){
		super(years, months, 0,0,0,0);
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
    private IntervalYearToMonth(final int years, final int months
    		, final int days, final int hours
    		, final int minutes, final int seconds
    		, final long nanos){
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
    private IntervalYearToMonth(final int years, final int months
    		, final int days, final int hours
    		, final int minutes, final double seconds){
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
	private IntervalYearToMonth(final int years, final int months
    		, final int days, final int hours
    		, final int minutes, final int seconds){
        super(years, months
        		, days, hours
        		, minutes, seconds
        		, 0);
    }

    /**
     * IntervalからIntervalYearToMonthへの変換
     * @param interval
     */
    public static IntervalYearToMonth toYearToMonthType(final Interval interval){
    	if (interval==null){
    		return null;
    	}
    	final IntervalYearToMonth result=new IntervalYearToMonth(interval.getYears(), interval.getMonths());
    	if (!interval.isPositive()){
    		result.scale(-1);
    	}
    	return result;
    }
    
    /**
     * 文字列からIntervalYearToMonthの構築
     * @param val
     */
    public static IntervalYearToMonth parse(final String val){
    	if (isEmpty(val)){
    		return null;
    	}
    	Interval interval=Interval.parseDetail(val);
    	if (interval==null){
    		interval=Interval.parse(val, YEAR, MONTH);
    	}
    	return toYearToMonthType(interval);
    }
    
	/**
	 * @param days the days to set
	 */
    @Override
	public void setDays(final int days) {
	}

	/**
	 * @param days the days to set
	 */
    @Override
	public void setDays(final double days) {
	}
    
	/**
	 * @param hours the hours to set
	 */
    @Override
	public void setHours(final int hours) {
	}

	/**
	 * @param hours the hours to set
	 */
    @Override
	public void setHours(final double hours) {
	}

	/**
	 * @param minutes the minutes to set
	 */
    @Override
	public void setMinutes(final int minutes) {
	}

	/**
	 * @param minutes the minutes to set
	 */
    @Override
	public void setMinutes(final double minutes) {
	}

	/**
	 * @param seconds the seconds to set
	 */
    @Override
	public void setSeconds(final int seconds) {
	}

	/**
	 * @param seconds the seconds to set
	 */
    @Override
	public void setSeconds(final double seconds) {
	}

	/**
	 * @param nanos the nanos to set
	 */
    @Override
	public void setNanos(final int nanos) {
	}

	/**
	 * @param nanos the nanos to set
	 */
    @Override
	public void setNanos(final long nanos) {
	}

    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public IntervalYearToMonth clone(){
		return (IntervalYearToMonth)super.clone();
    }
    
    public YearMonth toYearMonth(){
    	return YearMonth.of(this.getYears(), getMonths());
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString(){
    	final StringBuilder builder=new StringBuilder("");
    	if (!this.isPositive()){
        	builder.append("-");
    	}
    	builder.append(this.getYears());
    	builder.append("-");
    	builder.append(this.getMonths());
    	return builder.toString();
    }
}
