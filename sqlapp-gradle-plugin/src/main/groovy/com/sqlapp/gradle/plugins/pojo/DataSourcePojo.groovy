/*
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-gradle-plugin.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.gradle.plugins.pojo

import com.sqlapp.util.CommonUtils

import java.io.File
import java.util.List;

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

class DataSourcePojo implements Cloneable{
	Project project;

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
	String url;
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
	String defaultCatalog;
	/**
	 * デフォルトオートコミット
	 */
	@Input
	@Optional
	Boolean defaultAutoCommit = null;
	/**
	 * fairQueue
	 */
	@Input
	@Optional
	Boolean fairQueue=null;
	/**
	 * プールの起動時に作成されるコネクションの初期サイズ
	 */
	@Input
	@Optional
	Integer initialSize = null;
	/**
	 *　コネクション作成時に実行されるSQL
	 */
	@Input
	@Optional
	String initSQL = null;
	/**
	 * 最大接続数
	 */
	@Input
	@Optional
	Integer maxActive = null;
	/**
	 * 最大寿命
	 */
	@Input
	@Optional
	Long maxAge = null;
	/**
	 * プール内のコネクションが不足したときの最大待ち時間
	 */
	@Input
	@Optional
	Integer maxWait = null;
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
	Integer minIdle = null;

	@Input
	@Optional
	String name;
	/**
	 * コネクションが有効かどうかを検証するためのSQL
	 */
	@Input
	@Optional
	String validationQuery;
	/**
	 *  コネクションが有効かどうかを検証するためのSQLの実行間隔
	 */
	@Input
	@Optional
	Long validationInterval=null;
	/**
	 *  コネクションが有効かどうかを検証するためのSQLのタイムアウト時間
	 */
	@Input
	@Optional
	Integer validationQueryTimeout=null;
	/**
	 * コネクションをプールから取り出すときに検証するかどうか
	 */
	@Input
	@Optional
	Boolean testOnBorrow;
	/**
	 * コネクション作成時に検証するかどうか
	 */
	@Input
	@Optional
	Boolean testOnConnect;
	/**
	 * コネクションをプールに返すときに検証するかどうか
	 */
	@Input
	@Optional
	Boolean testOnReturn;
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
	Integer defaultTransactionIsolation = null;
	/**
	 * プール内のアイドル接続を一定時間毎に監視するスレッドを開始させます。間隔をミリ秒単位で指定します。
	 */
	@Input
	@Optional
	Integer timeBetweenEvictionRunsMillis = null;
	/**
	 * 監視処理時、アイドル接続の有効性を確認します。
	 */
	@Input
	@Optional
	Boolean testWhileIdle = null;
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
	 * JDBCインターセプタ
	 */
	@Input
	@Optional
	String jdbcInterceptors = null;
	/**
	 * JMX enabled
	 */
	@Input
	@Optional
	Boolean jmxEnabled=null;
	/**
	 * プロパティファイル
	 */
	List<Object> properties=new ArrayList<>();
	
	void driverClassName(String driverClassName){
		this.driverClassName=driverClassName;
	}

	void url(String url){
		this.url=url;
	}

	void username(String username){
		this.username=username;
	}

	void password(String password){
		this.password=password;
	}
	
	void defaultTransactionIsolation(String value){
		if ("NONE".equalsIgnoreCase(value)||"TRANSACTION_NONE".equalsIgnoreCase(value)){
			this.defaultTransactionIsolation=java.sql.Connection.TRANSACTION_NONE;
		}else if ("READ_COMMITTED".equalsIgnoreCase(value)||"TRANSACTION_READ_COMMITTED".equalsIgnoreCase(value)){
			this.defaultTransactionIsolation=java.sql.Connection.TRANSACTION_READ_COMMITTED;
		}else if ("READ_UNCOMMITTED".equalsIgnoreCase(value)||"TRANSACTION_READ_UNCOMMITTED".equalsIgnoreCase(value)){
			this.defaultTransactionIsolation=java.sql.Connection.TRANSACTION_READ_UNCOMMITTED;
		}else if ("REPEATABLE_READ".equalsIgnoreCase(value)||"TRANSACTION_REPEATABLE_READ".equalsIgnoreCase(value)){
			this.defaultTransactionIsolation=java.sql.Connection.TRANSACTION_REPEATABLE_READ;
		}else if ("SERIALIZABLE".equalsIgnoreCase(value)||"TRANSACTION_SERIALIZABLE".equalsIgnoreCase(value)){
			this.defaultTransactionIsolation=java.sql.Connection.TRANSACTION_SERIALIZABLE;
		}
	}
		
	void setDefaultTransactionIsolation(String value){
		this.defaultTransactionIsolation(value);
	}

	void fairQueue(boolean value){
		this.setFairQueue(value);
	}

	void initialSize(int value){
		this.setInitialSize(value);
	}

	void maxActive(int value){
		this.setMaxActive(value);
	}

	void maxWait(int value){
		this.setMaxWait(value);
	}

	void maxIdle(int value){
		this.setMaxIdle(value);
	}

	void minIdle(int value){
		this.setMinIdle(value);
	}

	void name(String value){
		this.setName(value);
	}

	void validationQuery(String value){
		this.setValidationQuery(value);
	}

	void testOnBorrow(boolean value){
		this.setTestOnBorrow(value);
	}
	
	void testOnConnect(boolean value){
		this.setTestOnConnect(value);
	}

	void testOnReturn(boolean value){
		this.setTestOnReturn(value);
	}

	void removeAbandoned(boolean value){
		this.setRemoveAbandoned(value);
	}

	void removeAbandonedTimeout(int value){
		this.setRemoveAbandonedTimeout(value);
	}

	void defaultAutoCommit(boolean value){
		this.setDefaultAutoCommit(value);
	}
	
	void defaultCatalog(String value){
		this.setDefaultCatalog(value);
	}

	void timeBetweenEvictionRunsMillis(int value){
		this.setTimeBetweenEvictionRunsMillis(value);
	}

	void testWhileIdle(boolean value){
		this.setTestWhileIdle(value);
	}

	void minEvictableIdleTimeMillis(int value){
		this.setMinEvictableIdleTimeMillis(value);
	}

	void numTestsPerEvictionRun(int value){
		this.setNumTestsPerEvictionRun(value);
	}

	void jdbcInterceptors(String value){
		this.setJdbcInterceptors(value);
	}

	void initialSQL(String value){
		this.setInitSQL(value);
	}

	void validationInterval(long value){
		this.setValidationInterval(value);
	}

	void validationQueryTimeout(int value){
		this.setValidationQueryTimeout(value);
	}
	
	void maxAge(long value){
		this.setMaxAge(value);
	}

	void properties(Object obj) {
		if (obj!=null) {
			this.properties.add(obj);
		}
	}

	void jmxEnabled(boolean value){
		this.setJmxEnabled(value);
	}
	
	@Override
	DataSourcePojo clone(){
		return super.clone();
	}
}
