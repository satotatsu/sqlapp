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

import java.lang.invoke.MethodHandles;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * メッセージプロパティ読み込みクラス
 * @author SATOH
 *
 */
public class MessageReader {
	protected static Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

	protected MessageReader(){
	}

	private ResourceBundle bundle=null;

	private ResourceBundle getResourceBundle(){
		if (bundle!=null){
			return bundle;
		}
        try {
        	bundle= ResourceBundle.getBundle(getResourceName());
        } catch (MissingResourceException e1) {
            try {
            	bundle=ResourceBundle.getBundle(getResourceName(), Locale.getDefault(), Thread.currentThread().getContextClassLoader());
            } catch (MissingResourceException e2) {
            	log.error("MessagePropertyReader.getInstance()", e2);
            	throw e2;
            }
        }
        return bundle;
	}

	/**
	 * 遅延初期化用のクラス
	 * @author satoh
	 *
	 */
	private static class LazyHolder {
		public static MessageReader singleton = new MessageReader();
	}

	public static MessageReader getInstance(){
        return  LazyHolder.singleton;
	}

	protected String getResourceName(){
		return path;
	}

	private final String path=this.getClass().getPackage().getName()+".messages";

	/**
	 * メッセージの取得
	 * @param messageID メッセージID
	 * @return メッセージIDに対応したメッセージ
	 */
	public String getMessage(String messageID){
        String value = getResourceBundle().getString(messageID);
        return value;
	}

	/**
	 * メッセージの取得
	 * @param messageID メッセージID
	 * @param args arguments
	 * @return メッセージIDに対応したメッセージ
	 */
	public String getMessage(String messageID
			, Object... args){
		return StringUtils.printf(getMessage(messageID), args);
	}
}
