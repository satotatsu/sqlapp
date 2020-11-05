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
public final class IntervalDay extends Interval{

    /**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 2517932092799906545L;

	/**
     * コンストラクタ
     */
	public IntervalDay(){
    }

	/**
	 * コンストラクタ 
	 * @param days
	 */
    public IntervalDay(int days){
        super(0, 0
        		, days, 0
        		, 0, 0
        		, 0);
    }

    /**
     * IntervalからIntervalDayへの変換
     * @param interval
     */
    public static IntervalDay toDayType(final Interval interval){
    	if (interval==null){
    		return null;
    	}
    	IntervalDay result=new IntervalDay(interval.getDays());
    	if (!interval.isPositive()){
    		result.scale(-1);
    	}
    	return result;
    }

    /**
     * 文字列からIntervalDayの構築
     * @param val
     */
    public static IntervalDay parse(final String val){
    	if (isEmpty(val)){
    		return null;
    	}
    	Interval interval=Interval.parseDetail(val);
    	if (interval==null){
    		interval=Interval.parse(val, YEAR, DAY);
    	}
    	return toDayType(interval);
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public IntervalDay clone(){
		return (IntervalDay)super.clone();
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
    	builder.append((this.getDays()));
    	builder.append("");
    	return builder.toString();
    }
}
