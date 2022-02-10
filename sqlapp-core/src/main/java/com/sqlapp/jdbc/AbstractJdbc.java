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

package com.sqlapp.jdbc;

import java.sql.SQLException;
import java.sql.Wrapper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import com.sqlapp.util.CommonUtils;

public abstract class AbstractJdbc<T extends Wrapper> implements Wrapper {
	private Logger logger = LogManager.getLogger(this.getClass());

	protected T nativeObject = null;

	private boolean debug=false;
	
	public AbstractJdbc(T nativeObject) {
		this.nativeObject = nativeObject;
	}

	/**
	 * ラッピング元のオブジェクトの取得
	 * 
	 */
	public T getNativeObject() {
		return nativeObject;
	}

	/**
	 * ログ出力前の処理
	 */
	protected void logBefore() {
	}

	/**
	 * MDCへの設定
	 * 
	 * @param key
	 * @param val
	 */
	protected void setMdc(String key, String val) {
		ThreadContext.put(key, val);
	}

	/**
	 * infoレベルのログ出力
	 * 
	 * @param text
	 */
	protected void info(String text) {
		if (CommonUtils.isEmpty(text)){
			return;
		}
		logBefore();
		logger.info(text);
		if (this.isDebug()){
			System.out.println(text);
		}
	}
	
	/**
	 * infoレベルのログ出力
	 * 
	 * @param text
	 */
	protected void warn(String text) {
		if (CommonUtils.isEmpty(text)){
			return;
		}
		logBefore();
		logger.warn(text);
		if (this.isDebug()){
			System.out.println(text);
		}
	}

	/**
	 * traceレベルのログ出力
	 * 
	 * @param text
	 */
	protected void trace(String text) {
		if (CommonUtils.isEmpty(text)){
			return;
		}
		logBefore();
		logger.trace(text);
	}

	/**
	 * debugレベルのログ出力
	 * 
	 * @param text
	 */
	protected void debug(String text) {
		if (CommonUtils.isEmpty(text)){
			return;
		}
		logBefore();
		logger.debug(text);
	}

	/**
	 * errorレベルのログ出力
	 * 
	 * @param text
	 */
	protected void error(String text, Throwable t) {
		logBefore();
		logger.error(text, t);
		if (this.isDebug()){
			if (CommonUtils.isEmpty(text)){
				System.err.println(text);
			}
			t.printStackTrace(System.err);
		}
	}
	
	/**
	 * SQLログが有効
	 * 
	 */
	protected boolean isSqlLogEnabled() {
		return this.isDebug()||logger.isInfoEnabled();
	}
	
	protected boolean isErrorEnabled(){
		return logger.isErrorEnabled();
	}

	protected boolean isInfoEnabled(){
		return logger.isInfoEnabled();
	}

	protected boolean isTraceEnabled(){
		return logger.isTraceEnabled();
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Wrapper#isWrapperFor(java.lang.Class)
	 */
	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		if (iface.isAssignableFrom(this.getClass())) {
			return true;
		}
		return nativeObject.isWrapperFor(iface);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Wrapper#unwrap(java.lang.Class)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public <S> S unwrap(Class<S> iface) throws SQLException {
		if (iface.isAssignableFrom(this.getClass())) {
			return (S) this;
		}
		return nativeObject.unwrap(iface);
	}

	/**
	 * @return the debug
	 */
	protected boolean isDebug() {
		return debug;
	}

	/**
	 * @param debug the debug to set
	 */
	protected void setDebug(boolean debug) {
		this.debug = debug;
	}
	
	protected void initializeChild(AbstractJdbc<?> child){
		child.setDebug(this.isDebug());
	}

}
