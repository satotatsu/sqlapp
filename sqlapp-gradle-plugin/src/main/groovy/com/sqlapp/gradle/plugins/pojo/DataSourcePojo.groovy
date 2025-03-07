/*
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-gradle-plugin.
 *
 * sqlapp-gradle-plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-gradle-plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-gradle-plugin.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.gradle.plugins.pojo

import com.sqlapp.util.CommonUtils

import java.io.File
import java.util.List;

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

public class DataSourcePojo implements Cloneable{

	final Project project;

	public DataSourcePojo(Project project) {
		this.project=project;
	}

	/**
	 * JDBC Driver Class Name
	 */
	@Input
	@Optional
	String driverClassName;
	/**
	 * JDBC URL
	 */
	@Input
	@Optional
	String jdbcUrl;
	/**
	 * JDBC User Name
	 */
	@Input
	@Optional
	String username;
	/**
	 * JDBC Password
	 */
	@Input
	@Optional
	String password;
	/**
	 * defaultCatalog
	 */
	@Input
	@Optional
	String catalog;
	/**
	 * オートコミット
	 */
	@Input
	@Optional
	Boolean autoCommit = null;
	/**
	 * allowPoolSuspension
	 */
	@Input
	@Optional
	Boolean allowPoolSuspension = null;
	/**
	 * 最大接続数
	 */
	@Input
	@Optional
	Integer maximumPoolSize = null;
	/**
	 * 最大寿命
	 */
	@Input
	@Optional
	Long idleTimeout = null;
	/**
	 * プール内のコネクションが不足したときの最大待ち時間
	 */
	@Input
	@Optional
	Long keepaliveTime = null;
	/**
	 * プールに保持しておく最大のコネクション数
	 */
	@Input
	@Optional
	Integer maxIdle = null;
	/**
	 * プールに保持する最小のコネクション数
	 */
	@Input
	@Optional
	Integer minimumIdle = null;
	@Input
	@Optional
	Long validationTimeout;
	@Input
	@Optional
	Long leakDetectionThreshold;
	@Input
	@Optional
	Long maxLifetime;
	@Input
	@Optional
	String poolName;
	/**
	 * コネクション初期化時のSQL
	 */
	@Input
	@Optional
	String connectionInitSql;
	/**
	 * コネクションテスト時のSQL
	 */
	@Input
	@Optional
	String connectionTestQuery;
	/**
	 * コネクションタイムアウト
	 */
	@Input
	@Optional
	Long connectionTimeout;
	/**
	 * クローズ漏れとなったコネクションを回収するかどうか
	 */
	@Input
	@Optional
	Boolean removeAbandoned = false;
	/**
	 * コネクションが最後に使用されてから回収対象となるまでの時間（秒）
	 */
	@Input
	@Optional
	int removeAbandonedTimeout = 60 * 10;
	/**
	 * デフォルトトランザクション分離レベル
	 */
	@Input
	@Optional
	Integer transactionIsolation = null;
	/**
	 * プール内のアイドル接続を一定時間毎に監視するスレッドを開始させます。間隔をミリ秒単位で指定します。
	 */
	@Input
	@Optional
	Integer timeBetweenEvictionRunsMillis = null;
	/**
	 * 監視処理時、アイドル接続の生存期間をチェックします。
	 */
	@Input
	@Optional
	Integer minEvictableIdleTimeMillis = null;
	/**
	 * 1回の監視処理でチェックするアイドル接続数の最大値を指定します。
	 */
	@Input
	@Optional
	Integer numTestsPerEvictionRun = null;
	/**
	 * プロパティファイル
	 */
	@Internal
	List<Object> properties=new ArrayList<>();
	
	void driverClassName(String driverClassName){
		this.driverClassName=driverClassName;
	}

	void jdbcUrl(String jdbcUrl){
		this.jdbcUrl=jdbcUrl;
	}
	
	void url(String url){
		this.setJdbcUrl(url);
	}
	
	@Input
	@Optional
	void setUrl(String url){
		this.setJdbcUrl(url);
	}

	void username(String username){
		this.username=username;
	}

	void password(String password){
		this.password=password;
	}
	
	void transactionIsolation(String value){
		if ("NONE".equalsIgnoreCase(value)||"TRANSACTION_NONE".equalsIgnoreCase(value)){
			this.transactionIsolation=java.sql.Connection.TRANSACTION_NONE;
		}else if ("READ_COMMITTED".equalsIgnoreCase(value)||"TRANSACTION_READ_COMMITTED".equalsIgnoreCase(value)){
			this.transactionIsolation=java.sql.Connection.TRANSACTION_READ_COMMITTED;
		}else if ("READ_UNCOMMITTED".equalsIgnoreCase(value)||"TRANSACTION_READ_UNCOMMITTED".equalsIgnoreCase(value)){
			this.transactionIsolation=java.sql.Connection.TRANSACTION_READ_UNCOMMITTED;
		}else if ("REPEATABLE_READ".equalsIgnoreCase(value)||"TRANSACTION_REPEATABLE_READ".equalsIgnoreCase(value)){
			this.transactionIsolation=java.sql.Connection.TRANSACTION_REPEATABLE_READ;
		}else if ("SERIALIZABLE".equalsIgnoreCase(value)||"TRANSACTION_SERIALIZABLE".equalsIgnoreCase(value)){
			this.transactionIsolation=java.sql.Connection.TRANSACTION_SERIALIZABLE;
		}
	}
		
	void setTransactionIsolation(String value){
		this.transactionIsolation(value);
	}

	void maximumPoolSize(int value){
		this.setMaximumPoolSize(value);
	}

	void keepaliveTime(int value){
		this.setKeepaliveTime(value);
	}

	void maxIdle(int value){
		this.setMaxIdle(value);
	}

	void minimumIdle(int value){
		this.setMinimumIdle(value);
	}

	void poolName(String value){
		this.setPoolName(value);
	}

	void validationTimeout(long value){
		this.setValidationTimeout(value);
	}
	
	void leakDetectionThreshold(long value){
		this.setLeakDetectionThreshold(value);
	}
	
	void maxLifetime(long value){
		this.setMaxLifetime(value);
	}
	void connectionInitSql(String value){
		this.setConnectionInitSql(value);
	}
	
	void connectionTestQuery(boolean value){
		this.setConnectionTestQuery(value);
	}

	void connectionTimeout(long value){
		this.setConnectionTimeout(value);
	}

	void removeAbandoned(boolean value){
		this.setRemoveAbandoned(value);
	}

	void removeAbandonedTimeout(int value){
		this.setRemoveAbandonedTimeout(value);
	}

	void autoCommit(boolean value){
		this.setAutoCommit(value);
	}
	
	void catalog(String value){
		this.setCatalog(value);
	}

	void timeBetweenEvictionRunsMillis(int value){
		this.setTimeBetweenEvictionRunsMillis(value);
	}

	void minEvictableIdleTimeMillis(int value){
		this.setMinEvictableIdleTimeMillis(value);
	}

	void numTestsPerEvictionRun(int value){
		this.setNumTestsPerEvictionRun(value);
	}
	
	void allowPoolSuspension(boolean value){
		this.setAllowPoolSuspension(value);
	}

	@Input
	@Optional
	void properties(Object obj) {
		if (obj!=null) {
			this.properties.add(obj);
		}
	}

	@Override
	DataSourcePojo clone(){
		return super.clone();
	}
}
