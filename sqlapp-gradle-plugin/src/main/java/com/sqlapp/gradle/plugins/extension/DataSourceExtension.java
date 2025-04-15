/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.gradle.plugins.extension;

import java.util.Map;

import javax.sql.DataSource;

import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.gradle.plugins.ConfigUtils;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public abstract class DataSourceExtension {

	public DataSourceExtension() {
		this.getProperties().from();
	}

	public void call(Action<DataSourceExtension> cons) {
		cons.execute(this);
	}

	@Internal
	public DataSource createDataSource() {
		final DataSource ds = new HikariDataSource(toConfig());
		return ds;
	}

	/**
	 * JDBC Driver Class Name
	 */
	@Input
	@Optional
	public abstract Property<String> getDriverClassName();

	/**
	 * JDBC URL
	 */
	@Input
	@Optional
	public abstract Property<String> getJdbcUrl();

	/**
	 * JDBC User Name
	 */
	@Input
	@Optional
	public abstract Property<String> getUsername();

	/**
	 * JDBC Password
	 */
	@Input
	@Optional
	public abstract Property<String> getPassword();

	/**
	 * Default Catalog
	 */
	@Input
	@Optional
	public abstract Property<String> getCatalog();

	/**
	 * Default Schema
	 */
	@Input
	@Optional
	public abstract Property<String> getSchema();

	/**
	 * オートコミット
	 */
	@Input
	@Optional
	public abstract Property<Boolean> getAutoCommit();

	/**
	 * allowPoolSuspension
	 */
	@Input
	@Optional
	public abstract Property<Boolean> getAllowPoolSuspension();

	/**
	 * 最大接続数
	 */
	@Input
	@Optional
	public abstract Property<Integer> getMaximumPoolSize();

	/**
	 * 最大寿命
	 */
	@Input
	@Optional
	public abstract Property<Long> getIdleTimeout();

	/**
	 * InitializationFailTimeout
	 */
	@Input
	@Optional
	public abstract Property<Long> getInitializationFailTimeout();

	/**
	 * IsolateInternalQueries
	 */
	@Input
	@Optional
	public abstract Property<Boolean> getIsolateInternalQueries();

	/**
	 * プール内のコネクションが不足したときの最大待ち時間
	 */
	@Input
	@Optional
	public abstract Property<Long> getKeepaliveTime();

	/**
	 * プールに保持する最小のコネクション数
	 */
	@Input
	@Optional
	public abstract Property<Integer> getMinimumIdle();

	@Input
	@Optional
	public abstract Property<Long> getValidationTimeout();

	@Input
	@Optional
	public abstract Property<Long> getLeakDetectionThreshold();

	@Input
	@Optional
	public abstract Property<Long> getMaxLifetime();

	@Input
	@Optional
	public abstract Property<String> getPoolName();

	/**
	 * コネクション初期化時のSQL
	 */
	@Input
	@Optional
	public abstract Property<String> getConnectionInitSql();

	/**
	 * コネクションテスト時のSQL
	 */
	@Input
	@Optional
	public abstract Property<String> getConnectionTestQuery();

	/**
	 * コネクションタイムアウト
	 */
	@Input
	@Optional
	public abstract Property<Long> getConnectionTimeout();

	/**
	 * デフォルトトランザクション分離レベル
	 */
	@Input
	@Optional
	public abstract Property<String> getTransactionIsolation();

	/**
	 * RegisterMbeans
	 */
	@Input
	@Optional
	public abstract Property<Boolean> getRegisterMbeans();

	/**
	 * プロパティファイル
	 */
	@InputFile
	@Optional
	public abstract ConfigurableFileCollection getProperties();

	public void properties(Object... paths) {
		getProperties().from(paths);
	}

	@Internal
	public void setConfig(final HikariConfig config) {
		if (!getProperties().isEmpty()) {
			Map<String, Object> map = CommonUtils.map();
			ConfigUtils.readConfig(null, map, this.getProperties().getFiles());
			for (Map.Entry<String, Object> entry : map.entrySet()) {
				SimpleBeanUtils.setValueCI(config, entry.getKey(), entry.getValue());
			}
		}
		if (getDriverClassName().isPresent()) {
			config.setJdbcUrl(getJdbcUrl().get());
		}
		if (getDriverClassName().isPresent()) {
			config.setDriverClassName(getDriverClassName().get());
		}
		if (getAutoCommit().isPresent()) {
			config.setAutoCommit(getAutoCommit().get());
		}
		if (getAllowPoolSuspension().isPresent()) {
			config.setAllowPoolSuspension(getAllowPoolSuspension().get());
		}
		if (getUsername().isPresent()) {
			config.setUsername(getUsername().get());
		}
		if (getPassword().isPresent()) {
			config.setPassword(getPassword().get());
		}
		if (getCatalog().isPresent()) {
			config.setCatalog(getCatalog().getOrNull());
		}
		if (getConnectionInitSql().isPresent()) {
			config.setConnectionInitSql(getConnectionInitSql().get());
		}
		if (getConnectionTestQuery().isPresent()) {
			config.setConnectionTestQuery(getConnectionTestQuery().get());
		}
		if (getConnectionTimeout().isPresent()) {
			config.setConnectionTimeout(getConnectionTimeout().get());
		}
		if (getIsolateInternalQueries().isPresent()) {
			config.setIsolateInternalQueries(getIsolateInternalQueries().get());
		}
		if (getInitializationFailTimeout().isPresent()) {
			config.setInitializationFailTimeout(getInitializationFailTimeout().get());
		}
		if (getIdleTimeout().isPresent()) {
			config.setIdleTimeout(getIdleTimeout().get());
		}
		if (getKeepaliveTime().isPresent()) {
			config.setKeepaliveTime(getKeepaliveTime().get());
		}
		if (getLeakDetectionThreshold().isPresent()) {
			config.setLeakDetectionThreshold(getLeakDetectionThreshold().get());
		}
		if (getMaximumPoolSize().isPresent()) {
			config.setMaximumPoolSize(getMaximumPoolSize().get());
		} else {
			config.setMaximumPoolSize(5);
		}
		if (getMaxLifetime().isPresent()) {
			config.setMaxLifetime(getMaxLifetime().get());
		}
		if (getMinimumIdle().isPresent()) {
			config.setMinimumIdle(getMinimumIdle().get());
		} else {
			config.setMinimumIdle(1);
		}

		if (getPoolName().isPresent()) {
			config.setPoolName(getPoolName().get());
		}
		if (getRegisterMbeans().isPresent()) {
			config.setRegisterMbeans(getRegisterMbeans().get());
		}
		if (getSchema().isPresent()) {
			config.setSchema(getSchema().get());
		}
		if (getTransactionIsolation().isPresent()) {
			config.setTransactionIsolation(getTransactionIsolation().get());
		}
		if (getValidationTimeout().isPresent()) {
			config.setValidationTimeout(getValidationTimeout().get());
		}
	}

	@Internal
	public HikariConfig toConfig() {
		final HikariConfig config = new HikariConfig();
		setConfig(config);
		return config;
	}

}