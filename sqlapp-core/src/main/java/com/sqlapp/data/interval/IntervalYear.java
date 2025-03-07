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
 * INTERVAL YEAR型
 * @author satoh
 *
 */
public final class IntervalYear extends Interval{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -7959888976357432942L;
    /**
     * コンストラクタ
     */
	public IntervalYear(){
    }

	/**
	 * コンストラクタ 
	 * @param years
	 */
    public IntervalYear(final int years){
        super(years, 0
        		, 0, 0
        		, 0, 0
        		, 0);
    }

    /**
     * IntervalからIntervalYearへの変換
     * @param interval
     */
    public static IntervalYear toYearType(final Interval interval){
    	if (interval==null){
    		return null;
    	}
    	final IntervalYear result=new IntervalYear(interval.getYears());
    	if (!interval.isPositive()){
    		result.scale(-1);
    	}
    	return result;
    }

    /**
     * 文字列からIntervalYearの構築
     * @param val
     */
    public static IntervalYear parse(final String val){
    	if (isEmpty(val)){
    		return null;
    	}
    	Interval interval=Interval.parseDetail(val);
    	if (interval==null){
    		interval=Interval.parse(val, YEAR, YEAR);
    	}
    	return toYearType(interval);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public IntervalYear clone(){
		return (IntervalYear)super.clone();
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
    	builder.append("");
    	return builder.toString();
    }
}
