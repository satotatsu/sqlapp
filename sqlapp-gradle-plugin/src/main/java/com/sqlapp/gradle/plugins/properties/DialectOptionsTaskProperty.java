/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-gradle-plugin.
 */
package com.sqlapp.gradle.plugins.properties;

import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/**
 * sqlapp dialect options for tasks that generate SQL without a connection, for
 * example {@code oracle.maxStringSize}. Tasks with a data source take them from
 * {@code dataSource { dialectOptions }} instead.
 */
public interface DialectOptionsTaskProperty {

	@Input
	@Optional
	abstract MapProperty<String, String> getDialectOptions();
}
