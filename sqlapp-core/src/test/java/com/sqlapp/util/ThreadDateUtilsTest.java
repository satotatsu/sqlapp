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

package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.*;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.sqlapp.util.CommonUtils.*;

import org.junit.jupiter.api.Test;

public class ThreadDateUtilsTest {

	static final int THREAD_SIZE=4;

	static final int TEST_COUNT=50000;
	static final int PATTERN=1;

	static final String FORMAT="yyyy-MM-dd HH:mm:ss";

	@Test
	public void testToStringDate() throws InterruptedException {
		pre(1, PATTERN);
		System.out.println("************************");
		System.out.println("************************");
		pre(THREAD_SIZE, PATTERN);
	}

	public void pre(int size, int patten) throws InterruptedException {
		test("getNormalFormat", getNormalFormat(size, patten));
		System.out.println("************************");
		test("getSyncFormat", getSyncFormat(size, patten));
		System.out.println("************************");
		test("getThreadFormat", getThreadFormat(size, patten));
	}

	static abstract class AbstractThreadFormat implements Runnable, Exec<Long>{
		int pattern=0;
		long result=0;
		@Override
		public void run() {
			Date date=new Date();
			result=0;
			for(int i=0;i<TEST_COUNT;i++){
				long start=System.currentTimeMillis();
				Object val=exec(date);
				long end=System.currentTimeMillis();
				result=result+(end-start);
			}
		}
		protected abstract Object exec(Date date); 
		@Override
		public Long getResult() {
			return result;
		}
	}
	
	private static final ThreadLocal<Calendar> threadLocalCalendar=new ThreadLocal<Calendar>(){
	    protected Calendar initialValue() {
	        return Calendar.getInstance();
	    }
	};
	
	static class ThreadFormat extends AbstractThreadFormat{
		@Override
		protected Object exec(Date date){
			switch (pattern){
			case 0:
				String val=DateUtils.format(date, FORMAT);
				return val;
			case 1:
				Calendar cal=threadLocalCalendar.get();
				cal.setTime(date);
				clearTime(cal);
				return cal.getTime();
			}
			return null;
		}
	}
	
	/**
	 * 時刻情報のクリア
	 * @param cal カレンダー
	 * @return 時刻情報をクリアした日付
	 */
	public static Calendar clearTime(Calendar cal){
		cal.set(Calendar.HOUR, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal;
	}
	
	private static Calendar cacheCalendar=Calendar.getInstance();
	
	private static ConcurrentHashMap<String, DateFormat> dateFormatMap=new ConcurrentHashMap<String, DateFormat>();

	private static DateFormat getDateFormat(String format){
		DateFormat dateFormat=dateFormatMap.get(format);
		if (dateFormat==null){
			dateFormat=new SimpleDateFormat(format);
			DateFormat oldValue=dateFormatMap.putIfAbsent(format, dateFormat);
			if (oldValue!=null){
				return oldValue;
			} else{
				return dateFormat;
			}
		}
		return dateFormat;
	}
	
	static String toStringSync(Date date, String format){
		DateFormat dateFormat=getDateFormat(format);
		synchronized(dateFormat){
			return dateFormat.format(date);
		}
	}
	
	static class SyncFormat extends AbstractThreadFormat{
		@Override
		protected Object exec(Date date){
			switch (pattern){
			case 0:
				String val=toStringSync(date, FORMAT);
				return val;
			case 1:
				Calendar cal=cacheCalendar;
				synchronized(cal){
					cal.setTime(date);
					clearTime(cal);
					return cal.getTime();
				}
			}
			return null;
		}
	}

	static class NormalFormat extends AbstractThreadFormat{
		@Override
		protected Object exec(Date date){

			switch (pattern){
			case 0:
				DateFormat dateFormat=new SimpleDateFormat(FORMAT);
				String val=dateFormat.format(date);
				return val;
			case 1:
				Calendar cal=Calendar.getInstance();
				cal.setTime(date);
				clearTime(cal);
				return cal.getTime();
			}
			return null;
		}
	}
	
	private void test(String title, List<AbstractThreadFormat> list) throws InterruptedException{
		System.out.println("******"+title+"("+list.size()+")******");
		List<Thread> threadList=list();
		for(int i=0;i<list.size();i++){
			threadList.add(new Thread(list.get(i)));
		}
		for(int i=0;i<list.size();i++){
			Thread thread=threadList.get(i);
			thread.start();
		}
		for(int i=0;i<list.size();i++){
			Thread thread=threadList.get(i);
			thread.join();
		}
		for(int i=0;i<list.size();i++){
			AbstractThreadFormat fmt=list.get(i);
			System.out.println(fmt.getResult());
		}
	}
	
	private List<AbstractThreadFormat> getNormalFormat(int size, int pettern){
		List<AbstractThreadFormat> list=list();
		for(int i=0;i<size;i++){
			AbstractThreadFormat format=new NormalFormat();
			format.pattern=pettern;
			list.add(format);
		}
		return list;
	}
	
	private List<AbstractThreadFormat> getThreadFormat(int size, int pettern){
		List<AbstractThreadFormat> list=list();
		for(int i=0;i<size;i++){
			AbstractThreadFormat format=new ThreadFormat();
			format.pattern=pettern;
			list.add(format);
		}
		return list;
	}

	private List<AbstractThreadFormat> getSyncFormat(int size, int pettern){
		List<AbstractThreadFormat> list=list();
		for(int i=0;i<size;i++){
			AbstractThreadFormat format=new SyncFormat();
			format.pattern=pettern;
			list.add(format);
		}
		return list;
	}

}
