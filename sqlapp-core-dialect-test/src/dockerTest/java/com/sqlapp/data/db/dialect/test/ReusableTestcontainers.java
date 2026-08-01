/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test;

import org.testcontainers.containers.GenericContainer;

/** Opt-in local container reuse while retaining disposable containers by default. */
public final class ReusableTestcontainers {
	private static final boolean REUSE = Boolean.getBoolean("sqlapp.testcontainers.reuse");

	private ReusableTestcontainers() {
	}

	public static <T extends GenericContainer<?>> T configure(final T container) {
		container.withReuse(REUSE);
		return container;
	}

	public static void start(final GenericContainer<?> container) {
		container.start();
	}

	public static void stop(final GenericContainer<?> container) {
		if (!REUSE) {
			container.stop();
		}
	}

	public static boolean isReuseEnabled() {
		return REUSE;
	}
}
