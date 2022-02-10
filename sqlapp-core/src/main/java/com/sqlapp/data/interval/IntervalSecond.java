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
 * INTERVAL SECOND型
 * @author satoh
 *
 */
public class IntervalSecond extends Interval{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -5794364986820692508L;

	/**
     * コンストラクタ
     */
	public IntervalSecond(){
    }

	/**
	 * コンストラクタ 
	 * @param seconds
	 */
    public IntervalSecond(int seconds){
        super(0, 0
        		, 0, 0
        		, 0, seconds
        		, 0);
    }

	/**
	 * コンストラクタ 
	 * @param seconds
	 */
    public IntervalSecond(int seconds, int nanos){
        super(0, 0
        		, 0, 0
        		, 0, seconds
        		, nanos);
    }

	/**
	 * コンストラクタ 
	 * @param seconds
	 */
    public IntervalSecond(double seconds){
        super(0, 0
        		, 0, 0
        		, 0, seconds);
    }

    /**
     * IntervalからIntervalSecondへの変換
     * @param interval
     */
    public static IntervalSecond toSecondType(final Interval interval){
    	if (interval==null){
    		return null;
    	}
    	IntervalSecond result=new IntervalSecond(
    					interval.getSecondsFull()
    	    			, interval.getNanos());
    	if (!interval.isPositive()){
    		result.scale(-1);
    	}
    	return result;
    }

    /**
     * 文字列からIntervalSecondの構築
     * @param val
     */
    public static IntervalSecond parse(final String val){
    	if (isEmpty(val)){
    		return null;
    	}
    	Interval interval=Interval.parseDetail(val);
    	if (interval==null){
    		interval=Interval.parse(val, SECOND, SECOND);
    	}
    	return toSecondType(interval);
    }
    
	/**
	 * @return the seconds
	 */
    @Override
	public int getSeconds() {
		return super.getSecondsFull();
	}
    
    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public IntervalSecond clone(){
		return (IntervalSecond)super.clone();
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
    	if (this.getNanos()>0){
    		synchronized(secondsFormat){
    			double val=(double)this.getNanos()/NANO_LIMIT+this.getSecondsFull();
    			builder.append(secondsFormat.format(val));
    		}
    	} else{
        	builder.append(this.getSecondsFull());
    	}
    	builder.append("");
    	return builder.toString();
    }
}
