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

import java.io.Serializable;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.util.HashCodeBuilder;
import com.sqlapp.util.Java8DateUtils;

import static com.sqlapp.util.CommonUtils.*;
/**
 * SQLのInterval型に相当するクラス
 * @author satoh
 *
 */
public class Interval implements Serializable, Cloneable, Comparable<Interval>{

    /**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -5789019381885490073L;
    protected static final DecimalFormat secondsFormat;

    static 
    {
        secondsFormat = new DecimalFormat("0.######");
        DecimalFormatSymbols dfs = secondsFormat.getDecimalFormatSymbols();
        dfs.setDecimalSeparator('.');
        secondsFormat.setDecimalFormatSymbols(dfs);
    }
	private boolean positive=true;
	/**
	 * 月
	 */
	private int months;
    /**
     * 日
     */
	private int seconds;
	private int nanos;
    protected static final int NANO_LIMIT=1000000000;
    protected static final int SECOND_LIMIT=60;
    protected static final int MINUTE_LIMIT=60;
    protected static final int HOUR_LIMIT=24;
    protected static final int MONTH_LIMIT=12;

    protected static final int MINUTE_SECONDS=SECOND_LIMIT;
    protected static final int HOUR_SECONDS=MINUTE_LIMIT*SECOND_LIMIT;
    protected static final int HOUR_MINUTES=MINUTE_LIMIT;
    protected static final int DAY_SECONDS=HOUR_LIMIT*HOUR_SECONDS;

    
    protected static final String TO="TO";

    public static final String YEAR="YEAR";
    public static final String MONTH="MONTH";
    public static final String DAY="DAY";
    public static final String HOUR="HOUR";
    public static final String MINUTE="MINUTE";
    public static final String SECOND="SECOND";
    
    public static final String[] UNITS=new String[]{YEAR, MONTH, DAY, HOUR, MINUTE, SECOND};
    /**
     * YEAR TO MONTH
     */
    public static final String YEAR_TO_MONTH=getTypeName(YEAR, MONTH);
    public static final String MONTH_TO_DAY=getTypeName(MONTH, DAY);
    public static final String YEAR_TO_DAY=getTypeName(YEAR, DAY);
    public static final String DAY_TO_SECOND=getTypeName(DAY, SECOND);
    public static final String HOUR_TO_MINUTE=getTypeName(HOUR, MINUTE);
    public static final String HOUR_TO_SECOND=getTypeName(HOUR, SECOND);
    public static final String MINUTE_TO_SECOND=getTypeName(MINUTE, SECOND);

    protected static String getTypeName(String type1, String type2){
    	return type1+" "+TO+" "+type2;
    }

    /**
     * コンストラクタ
     */
	public Interval(){
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
    public Interval(int years, int months
    		, int days, int hours
    		, int minutes, int seconds
    		, long nanos){
        setValue(years*MONTH_LIMIT + months
        		, days*DAY_SECONDS + hours*HOUR_SECONDS
        		+ minutes*MINUTE_SECONDS + seconds
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
    public Interval(int years, int months
    		, int days, int hours
    		, int minutes, double seconds){
        setValue(years*MONTH_LIMIT + months
        		, (double)days*DAY_SECONDS + (double)hours*HOUR_SECONDS
        		+ (double)minutes*MINUTE_SECONDS + seconds);
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
    public Interval(int years, int months
    		, int days, int hours
    		, int minutes, int seconds){
        setValue(years*MONTH_LIMIT + months
        		, days*DAY_SECONDS + hours*HOUR_SECONDS
        		+ minutes*MINUTE_SECONDS + seconds
        		, 0);
    }
    
    /**
     * IntervalにScaleをかける(-1を設定すると負の値になる)
     * @param scale
     */
    public Interval scale(int scale){
    	if (scale<0){
    		this.positive=false;
    	} else{
    		this.positive=true;
    	}
    	int absScale=abs(scale);
        setValue(months*absScale
        		, (long)seconds*absScale
        		, (long)nanos*absScale);
    	return this;
    }
    
    protected void setValue(final int months
    		, final double seconds){
    	int secondsInt=(int)seconds;
    	int nanoInt=(int)((seconds-secondsInt)*NANO_LIMIT);
        setValue(months
        		, secondsInt
        		, nanoInt);
    }

    protected void setValue(int months
    		, long seconds
    		, long nanos){
    	months=abs(months);
    	seconds=abs(seconds);
    	nanos=abs(nanos);
        long val=(nanos/NANO_LIMIT);
        if (val>0){
        	this.nanos=(int)(nanos%NANO_LIMIT);
        	seconds=seconds+val;
        } else{
        	this.nanos=(int)nanos;
        }
    	this.seconds=(int)(seconds);
    	this.months=(int)(months);
    }
    
    /**
     * 指定した日付に期間を加算した結果を返します
     * @param ts
     */
    public Timestamp add(Timestamp ts){
        return add(ts, 1);
    }

    /**
     * 指定した日付に期間を加算した結果を返します
     * @param ts
     */
    public Timestamp add(Timestamp ts, int multiplicity){
    	int nanos=ts.getNanos();
        Calendar cal = Calendar.getInstance();
        cal.setTime(ts);
        cal.set(Calendar.MILLISECOND, 0);
        cal=add(cal, multiplicity);
        Timestamp ret=new Timestamp(cal.getTimeInMillis());
        ret.setTime(cal.getTime().getTime());
        if (isPositive()){
	        ts.setNanos(nanos + getNanos()*multiplicity);
        } else{
        	ret.setNanos(nanos - getNanos()*multiplicity);
        }
        return ts;
    }

    /**
     * 指定した日付に期間を加算した結果を返します
     * @param cal
     */
    public Calendar add(final Calendar cal){
    	return add(cal, 1);
    }

    /**
     * 指定した日付に期間を指定した回数だけ加算した結果を返します
     * @param cal
     * @param multiplicity Intervalの値を何倍にして加算するかを指定します。
     */
    public Calendar add(final Calendar cal, int multiplicity){
    	Calendar ret=(Calendar)cal.clone();
    	if (isPositive()){
    		ret.add(Calendar.MILLISECOND, getMilliseconds()*multiplicity);
    		ret.add(Calendar.SECOND, getSeconds()*multiplicity);
    		ret.add(Calendar.MINUTE, getMinutes()*multiplicity);
	        ret.add(Calendar.HOUR_OF_DAY, getHours()*multiplicity);
	        ret.add(Calendar.DATE, getDays()*multiplicity);
	        ret.add(Calendar.MONTH, getMonths()*multiplicity);
	        ret.add(Calendar.YEAR, getYears()*multiplicity);
    	} else{
    		ret.add(Calendar.MILLISECOND, -getMilliseconds()*multiplicity);
    		ret.add(Calendar.SECOND, -getSeconds()*multiplicity);
    		ret.add(Calendar.MINUTE, -getMinutes()*multiplicity);
    		ret.add(Calendar.HOUR_OF_DAY, -getHours()*multiplicity);
    		ret.add(Calendar.DATE, -getDays()*multiplicity);
    		ret.add(Calendar.MONTH, -getMonths()*multiplicity);
    		ret.add(Calendar.YEAR, -getYears()*multiplicity);
    	}
    	return ret;
    }

    /**
     * 指定した日付に期間を加算した結果を返します
     * @param date
     */
    public Date add(Date date) {
    	return add(date, 1);
    }

    /**
     * 指定した日付に期間を指定した回数だけ加算した結果を返します
     * @param date
     * @param multiplicity Intervalの値を何倍にして加算するかを指定します。
     */
    public Date add(Date date, int multiplicity) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal=add(cal, multiplicity);
        return cal.getTime();
    }

    /**
     * 指定した日付に期間を加算した結果を返します
     * @param date
     */
    public java.sql.Date add(java.sql.Date date) {
        return add(date, 1);
    }

    /**
     * 指定した日付に期間を指定した回数だけ加算した結果を返します
     * @param date
     * @param multiplicity Intervalの値を何倍にして加算するかを指定します。
     */
    public java.sql.Date add(java.sql.Date date, int multiplicity) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal=add(cal, multiplicity);
        return new java.sql.Date(cal.getTimeInMillis());
    }

    /**
     * 指定した時間に期間を指定した回数だけ加算した結果を返します
     * @param time
     * @param multiplicity Intervalの値を何倍にして加算するかを指定します。
     */
    public java.sql.Time add(java.sql.Time time, int multiplicity) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(time);
    	if (isPositive()){
    		cal.add(Calendar.MILLISECOND, getMilliseconds()*multiplicity);
    		cal.add(Calendar.SECOND, getSeconds()*multiplicity);
    		cal.add(Calendar.MINUTE, getMinutes()*multiplicity);
    		cal.add(Calendar.HOUR_OF_DAY, getHours()*multiplicity);
    	} else{
    		cal.add(Calendar.MILLISECOND, -getMilliseconds()*multiplicity);
    		cal.add(Calendar.SECOND, -getSeconds()*multiplicity);
    		cal.add(Calendar.MINUTE, -getMinutes()*multiplicity);
    		cal.add(Calendar.HOUR_OF_DAY, -getHours()*multiplicity);
    	}
        return new java.sql.Time(cal.getTimeInMillis()*multiplicity);
    }

    /**
     * 指定した時間に期間を加算した結果を返します
     * @param time
     */
    public java.sql.Time add(java.sql.Time time) {
    	return add(time, 1);
    }

    /**
     * <code>java.time.Temporal</code>にIntervalを指定した回数だけ加算した結果を返します。
     * @param temporal
     * @param multiplicity Intervalの値を何倍にして加算するかを指定します。
     */
    public <T extends java.time.temporal.Temporal> T add(T temporal, int multiplicity) {
    	T ret=temporal;
    	if (months!=0){
        	ret=Java8DateUtils.addMonths(ret, this.months*multiplicity);
    	}
    	if (this.seconds!=0){
        	ret=Java8DateUtils.addSeconds(ret, this.seconds*multiplicity);
    	}
    	return ret;
    }
    
    /**
     * <code>java.time.Month</code>にIntervalを指定した回数だけ加算した結果を返します。
     * @param temporal
     * @param multiplicity Intervalの値を何倍にして加算するかを指定します。
     */
    public java.time.Month add(java.time.Month temporal, int multiplicity) {
    	java.time.Month ret=temporal;
    	if (months!=0){
        	ret=Java8DateUtils.addMonths(ret, this.months*multiplicity);
    	}
    	return ret;
    }

    /**
     * <code>java.time.YearMonth</code>にIntervalを指定した回数だけ加算した結果を返します。
     * @param temporal
     * @param multiplicity Intervalの値を何倍にして加算するかを指定します。
     */
    public java.time.MonthDay add(java.time.MonthDay temporal, int multiplicity) {
    	java.time.MonthDay ret=temporal;
    	if (months!=0){
        	ret=Java8DateUtils.addMonths(ret, this.months*multiplicity);
    	}
    	if (months!=0){
        	ret=Java8DateUtils.addDays(ret, this.getDays()*multiplicity);
    	}
    	return ret;
    }
    
    /**
     * <code>java.time.YearMonth</code>にIntervalを指定した回数だけ加算した結果を返します。
     * @param temporal
     * @param multiplicity Intervalの値を何倍にして加算するかを指定します。
     */
    public java.time.YearMonth add(java.time.YearMonth temporal, int multiplicity) {
    	java.time.YearMonth ret=temporal;
    	if (months!=0){
        	ret=Java8DateUtils.addMonths(ret, this.months*multiplicity);
    	}
    	return ret;
    }
    
    /**
     * <code>java.time.Instant</code>にIntervalを加算した結果を返します。
     * @param temporal
     */
    public <T extends java.time.temporal.Temporal> T add(T temporal) {
    	return add(temporal, 1);
    }

    /**
     * 指定した時間に期間を加算した結果を返します
     * @param temporal
     */
    public java.time.Month add(java.time.Month temporal) {
    	return add(temporal, 1);
    }

    /**
     * 指定した時間に期間を加算した結果を返します
     * @param temporal
     */
    public java.time.YearMonth add(java.time.YearMonth temporal) {
    	return add(temporal, 1);
    }

    /**
     * Intervalを加算します。
     * @param interval
     */
    public void add(Interval interval){
    	if (isPositive()){
            interval.setYears(getYears() + interval.getYears());
            interval.setMonths(interval.getMonths() + getMonths());
            interval.setDays(interval.getDays() + getDays());
            interval.setHours(interval.getHours() + getHours());
            interval.setMinutes(interval.getMinutes() + getMinutes());
            interval.setSeconds(interval.getSeconds() + getSeconds());
            interval.setNanos(interval.getNanos() + getNanos());
    	} else{
            interval.setYears(getYears() - interval.getYears());
            interval.setMonths(interval.getMonths() - getMonths());
            interval.setDays(interval.getDays() - getDays());
            interval.setHours(interval.getHours() - getHours());
            interval.setMinutes(interval.getMinutes() - getMinutes());
            interval.setSeconds(interval.getSeconds() - getSeconds());
            interval.setNanos(interval.getNanos() - getNanos());
    	}
    }

    private static final String MONTH_PIPE_SECOND=MONTH+"|"+DAY+"|"+HOUR+"|"+MINUTE+"|"+SECOND;

    private static final String YEAR_PIPE_SECOND=YEAR+"|"+MONTH_PIPE_SECOND;

    /**
     * 文字列を解析する時に使用する正規表現
     */
    private final static Pattern INTERVAL_PATTERN
		=Pattern.compile("\\s*('?[-+]?[0-9]+[ -:0-9]*'?)\\s*("+YEAR_PIPE_SECOND+"){0,1}\\s*(\\([0-9]+\\)){0,1}\\s*(TO){0,1}\\s*("+MONTH_PIPE_SECOND+"){0,1}.*");
    
    /**
     * 文字列からIntervalの構築
     * @param val
     */
    public static Interval parse(final String val){
    	if (isEmpty(val)){
    		return null;
    	}
		return parseDetail(val);
    }

    public static boolean isParsable(final String val){
    	Matcher mathcer=null;
    	String upperVal=val.toUpperCase();
    	if (upperVal.startsWith("INTERVAL")){
    		mathcer=INTERVAL_PATTERN.matcher(upperVal.substring("INTERVAL".length()));
    	} else{
    		mathcer=INTERVAL_PATTERN.matcher(upperVal);
    	}
    	return mathcer.matches();
    }
    
    protected static Interval parseDetail(final String val){
    	String upperVal=val.toUpperCase();
    	Matcher mathcer=null;
    	if (upperVal.startsWith("INTERVAL")){
    		mathcer=INTERVAL_PATTERN.matcher(upperVal.substring("INTERVAL".length()));
    	} else{
    		mathcer=INTERVAL_PATTERN.matcher(upperVal);
    	}
    	if (!mathcer.matches()){
    		throw toRe(new ParseException("Interval#parse("+val+") fail.", 0));
    	}
    	int count=1;
		String matchValue=mathcer.group(count++);
		String firstUnit=mathcer.group(count++);
		mathcer.group(count++);//precision
		mathcer.group(count++);//toPart
		String secondUnit=mathcer.group(count++);
		if (isEmpty(firstUnit)){
			return null;
		}
		return parse(matchValue, firstUnit, secondUnit);
    }

    protected static Interval parse(final String value, final String firstUnit, final String secondUnit){
		Interval result=new Interval();
		String matchValue=trim(unwrap(trim(value), '\''));
		if (matchValue.startsWith("-")){
			result.scale(-1);
		}
		if (!isEmpty(secondUnit)){
			String[] tmp=matchValue.split("[- ]");
			String[] splits=null;
			if (isEmpty(first(tmp))){
				splits=new String[tmp.length-1];
				System.arraycopy(tmp, 1, splits, 0, tmp.length-1);
			} else{
				splits=tmp;
			}
			int dayPosition=unitPosition("DAY");
			int hourPosition=dayPosition+1;
			int firstUnitPosition=unitPosition(firstUnit);
			int secondUnitPosition=unitPosition(secondUnit);
			if(dayPosition<firstUnitPosition){
				//時刻のみ
				String[] timeSplits=last(splits).split(":");
				for(int i=0;i<timeSplits.length;i++){
					if (timeSplits[i].contains(".")){
						setValue(result, UNITS[firstUnitPosition], timeSplits[i]);
					} else{
						setValue(result, UNITS[firstUnitPosition], Integer.valueOf(timeSplits[i]).intValue());
					}
					firstUnitPosition++;
				}
			} else{
				//日付を含む
				if (dayPosition<secondUnitPosition){
					//時刻を含む
					for(int i=splits.length-2;i>=0;i--){
						setValue(result, UNITS[dayPosition], Integer.valueOf(splits[i]).intValue());
						dayPosition--;
					}
					String[] timeSplits=last(splits).split(":");
					for(int i=0;i<timeSplits.length;i++){
						if (timeSplits[i].contains(".")){
							setValue(result, UNITS[hourPosition], timeSplits[i]);
						} else{
							setValue(result, UNITS[hourPosition], Integer.valueOf(timeSplits[i]).intValue());
						}
						hourPosition++;
					}
				} else{
					for(int i=splits.length-1;i>=0;i--){
						setValue(result, UNITS[secondUnitPosition], Integer.valueOf(splits[i]).intValue());
						secondUnitPosition--;
					}
				}
			}
		} else{
			if (matchValue.contains(".")){
				setValue(result, firstUnit, matchValue);
			} else{
				setValue(result, firstUnit, Integer.valueOf(matchValue).intValue());
			}
		}
		return result;
    }

    /**
     * 単位を指定して値を設定
     * @param unit YEAR,MONTH,DAY,HOUR,MINUTE,SECONDのいずれか
     * @param value
     */
    protected static void setValue(Interval interval, String unit, String value){
    	if (YEAR.equalsIgnoreCase(unit)){
    		interval.setYears(Double.valueOf(value).doubleValue());
    	} else if (MONTH.equalsIgnoreCase(unit)){
    		interval.setMonths(Double.valueOf(value).intValue());
    	} else if (DAY.equalsIgnoreCase(unit)){
    		interval.setDays(Double.valueOf(value).intValue());
    	} else if (HOUR.equalsIgnoreCase(unit)){
    		interval.setHours(Double.valueOf(value).intValue());
    	} else if (MINUTE.equalsIgnoreCase(unit)){
    		interval.setMinutes(Double.valueOf(value).intValue());
    	} else if (SECOND.equalsIgnoreCase(unit)){
        	String[] sp=value.split("\\.");
        	int sec=Integer.valueOf(sp[0]).intValue();
        	int nano=Integer.valueOf((sp[1]+"000000000").substring(0, 9)).intValue();
    		interval.setSeconds(sec);
    		interval.setNanos(nano);
    	}
    }
    
    private static int unitPosition(String unit){
    	for(int i=0;i<UNITS.length;i++){
    		if (UNITS[i].equalsIgnoreCase(unit)){
    			return i;
    		}
    	}
    	return -1;
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj){
        if(obj == null){
            return false;
        }
        if(obj == this){
            return true;
        }
        if(!(obj instanceof Interval)) {
            return false;
        }
    	Interval val = (Interval)obj;
    	if (val.positive!=this.positive){
            return false;
    	}
    	if (val.months!=this.months){
            return false;
    	}
    	if (val.seconds!=this.seconds){
            return false;
    	}
    	if (abs(val.nanos-this.nanos)>1){
            return false;
    	}
        return true;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode(){
    	HashCodeBuilder builder=new HashCodeBuilder();
    	builder.append(this.positive);
    	builder.append(this.months);
    	builder.append(this.seconds);
    	builder.append(this.nanos);
        return builder.hashCode();
    }

    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public Interval clone(){
    	try {
			return (Interval)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
    }

    /**
     * 単位を指定して値を設定
     * @param unit YEAR,MONTH,DAY,HOUR,MINUTE,SECONDのいずれか
     * @param value
     */
    protected static void setValue(Interval interval, String unit, int value){
    	if (YEAR.equalsIgnoreCase(unit)){
    		interval.setYears(value);
    	} else if (MONTH.equalsIgnoreCase(unit)){
    		interval.setMonths(value);
    	} else if (DAY.equalsIgnoreCase(unit)){
    		interval.setDays(value);
    	} else if (HOUR.equalsIgnoreCase(unit)){
    		interval.setHours(value);
    	} else if (MINUTE.equalsIgnoreCase(unit)){
    		interval.setMinutes(value);
    	} else if (SECOND.equalsIgnoreCase(unit)){
    		interval.setSeconds(value);
    	} else{
    		interval.setNanos(value);
    	}
    }

	/**
	 * @return the years
	 */
	public int getYears() {
		return months/MONTH_LIMIT;
	}

	/**
	 * @param years the years to set
	 */
	public void setYears(int years) {
        setValue(years*MONTH_LIMIT+(getMonths()%MONTH_LIMIT)
        		, seconds
        		, nanos);
	}

	/**
	 * @param years the years to set
	 */
	public void setYears(double years) {
		int calc=(int)(years*MONTH_LIMIT);
        setValue(calc
        		, seconds
        		, nanos);
	}

	/**
	 * @return the months
	 */
	public int getMonths() {
		return months%MONTH_LIMIT;
	}

	/**
	 * @return the months
	 */
	public int getMonthsFull() {
		return months;
	}
	
	/**
	 * @param months the months to set
	 */
	public void setMonths(int months) {
		int calc=0;
		if (months>MONTH_LIMIT){
			calc=months;
		}else{
			calc=getYears()*MONTH_LIMIT
			+months;
		}
		setValue(calc
        		, seconds
        		, nanos);
	}

	/**
	 * @return the days
	 */
	public int getDays() {
		return seconds/DAY_SECONDS;
	}

	/**
	 * @param days the days to set
	 */
	public void setDays(int days) {
		int calc=days*DAY_SECONDS
		+getHours()*HOUR_SECONDS
		+getMinutes()*MINUTE_SECONDS
		+getSeconds();
        setValue(months
        		, calc
        		, nanos);
	}

	/**
	 * @param days
	 */
	public void setDays(double days) {
		double calc=(days-(int)days)*HOUR_LIMIT;
		if (calc!=0.0d){
			setHours(calc);
		}
		setDays((int)days);
	}

	/**
	 * @return the hours
	 */
	public int getHours() {
		return (seconds-getDays()*DAY_SECONDS)/HOUR_SECONDS;
	}

	/**
	 * @return the hours
	 */
	protected int getHoursFull() {
		return (seconds)/HOUR_SECONDS;
	}

	/**
	 * @param hours the hours to set
	 */
	public void setHours(int hours) {
		int calc=0;
		if (hours>HOUR_LIMIT){
			setDays(hours/HOUR_LIMIT);
		}
		calc=getDays()*DAY_SECONDS
		+(hours%HOUR_LIMIT)*HOUR_SECONDS
		+getMinutes()*MINUTE_SECONDS
		+getSeconds();
        setValue(months
        		, calc
        		, nanos);
	}

	/**
	 * @param hours
	 */
	public void setHours(double hours) {
		double calc=(hours-(int)hours)*HOUR_MINUTES;
		if (calc!=0.0d){
			setMinutes(calc);
		}
		setHours((int)hours);
	}
	
	/**
	 * @return the minutes
	 */
	public int getMinutes() {
		return (seconds-getDays()*DAY_SECONDS-getHours()*HOUR_SECONDS)
			/MINUTE_SECONDS;
	}

	/**
	 * @return the minutes
	 */
	protected int getMinutesFull() {
		return (seconds)
			/MINUTE_SECONDS;
	}
	
	/**
	 * @param minutes the minutes to set
	 */
	public void setMinutes(int minutes) {
		int calc=0;
		if (minutes>MINUTE_LIMIT){
			setHours(minutes/MINUTE_LIMIT);
		}
		calc=getDays()*DAY_SECONDS
		+getHours()*HOUR_SECONDS
		+(minutes%MINUTE_SECONDS)*MINUTE_SECONDS
		+getSeconds();;
        setValue(months
        		, calc
        		, nanos);
	}

	/**
	 * @param minutes
	 */
	public void setMinutes(double minutes) {
		double calc=(minutes-(int)minutes)*MINUTE_SECONDS;
		if (calc!=0.0d){
			setSeconds(calc);
		}
		setMinutes((int)minutes);
	}

	/**
	 * @return the seconds
	 */
	public int getSeconds() {
		return seconds%SECOND_LIMIT;
	}

	public int getSecondsFull(){
		return seconds;
	}

	/**
	 * doubleで秒を取得
	 * @return the seconds
	 */
	public double getSecondsAsDouble() {
		double ret=getSeconds();
		ret=ret+(double)getNanos()/NANO_LIMIT;
		return ret;
	}

	/**
	 * @param seconds the seconds to set
	 */
	public void setSeconds(int seconds) {
		int calc=0;
		if (seconds>SECOND_LIMIT){
			setMinutes(seconds/SECOND_LIMIT);
		}
		calc=getDays()*DAY_SECONDS
			+getHours()*HOUR_SECONDS
			+getMinutes()*MINUTE_SECONDS
			+(seconds%SECOND_LIMIT);
        setValue(months
        		, calc
        		, nanos);
	}

	/**
	 * @param seconds the seconds to set
	 */
	public void setSeconds(double seconds) {
		double nanos=seconds-(int)seconds;
		setNanos((int)(nanos*NANO_LIMIT));
		int sec=(int)seconds;
		setSeconds(sec);
	}

	public int getMilliseconds(){
		return (int)(getNanos() / 1000000);
	}

	/**
	 * @return the nanos
	 */
	public int getNanos() {
		return nanos;
	}

	/**
	 * @param nanos the nanos to set
	 */
	public void setNanos(int nanos) {
        setValue(months
        		, seconds
        		, nanos);
	}

	/**
	 * @param nanos the nanos to set
	 */
	public void setNanos(long nanos) {
        setValue(months
        		, seconds
        		, nanos);
	}

	/**
	 * Intervalが正の値かを返す
	 */
	public boolean isPositive(){
		return positive;
	}

	/**
	 * Intervalを負の値に設定
	 */
	public void setNegative(){
		this.positive=false;
	}

	/**
	 * Intervalを正の値に設定
	 */
	public void setPositive(){
		this.positive=true;
	}

	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(Interval o) {
		if (o==null){
			return 1;
		}
		int val=compare(this.positive, o.positive);
		if (val!=0){
			return val;
		}
		val=compare(this.months, o.months);
		if (val!=0){
			return val;
		}
		val=compare(this.seconds, o.seconds);
		if (val!=0){
			return val;
		}
		val=compare(this.nanos, o.nanos);
		if (val!=0){
			return val;
		}
		return 0;
	}

    /**
     * Interval型への変換
     */
    public Interval toInterval(){
    	Interval interval=new Interval();
    	interval.setMonths(this.months);
    	interval.setSeconds(this.seconds);
    	interval.setNanos(this.nanos);
    	if (!isPositive()){
    		interval.scale(-1);
    	}
    	return interval;
    }

    /**
     * プロパティをコピーします
     * @param clone
     */
    protected void cloneProperties(Interval clone){
    	clone.months=this.months;
    	clone.nanos=this.nanos;
    	clone.seconds=this.seconds;
    	clone.positive=this.positive;
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
    	builder.append(" ");
    	builder.append(this.getHours());
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

    /**
     * 第2引数で指定した桁以下の数をゼロにする
     * @param val
     * @param sub
     */
    protected int truncate(int val, int sub){
    	return (val/sub)*sub;
    }

    protected long truncate(long val, long sub){
    	return (val/sub)*sub;
    }    
}
