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
 * INTERVAL DAY型
 * @author satoh
 *
 */
public class IntervalHour extends Interval{

    /**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 2517932092799906545L;

	/**
     * コンストラクタ
     */
	public IntervalHour(){
    }

	/**
	 * コンストラクタ 
	 * @param hours
	 */
    public IntervalHour(int hours){
        super(0, 0
        		, 0, hours
        		, 0, 0
        		, 0);
    }

    /**
     * IntervalからIntervalHourへの変換
     * @param interval
     */
    public static IntervalHour toHourType(final Interval interval){
    	if (interval==null){
    		return null;
    	}
    	IntervalHour result=new IntervalHour((interval.getDays()*HOUR_LIMIT
    			+interval.getHours()));
    	if (!interval.isPositive()){
    		result.scale(-1);
    	}
    	return result;
    }

    /**
     * 文字列からIntervalDayの構築
     * @param val
     */
    public static IntervalHour parse(final String val){
    	if (isEmpty(val)){
    		return null;
    	}
    	Interval interval=Interval.parseDetail(val);
    	if (interval==null){
    		interval=Interval.parse(val, HOUR, HOUR);
    	}
    	return toHourType(interval);
    }
    

	/**
	 * @return the hours
	 */
    @Override
	public int getHours() {
		return super.getHoursFull();
	}
    
    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public IntervalHour clone(){
		return (IntervalHour)super.clone();
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
    	builder.append((this.getDays()*HOUR_LIMIT+this.getHours()));
    	builder.append("");
    	return builder.toString();
    }
}
