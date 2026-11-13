/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * Tracks connection pools created while a task action runs, so that they are
 * closed when the action finishes instead of leaking into the Gradle daemon.
 */
public final class CreatedDataSources {

	private static final Logger LOGGER = Logging.getLogger(CreatedDataSources.class);

	private static final ThreadLocal<Deque<List<AutoCloseable>>> SCOPES = ThreadLocal.withInitial(ArrayDeque::new);

	private CreatedDataSources() {
	}

	/**
	 * Opens a scope on the current thread. Every data source registered until
	 * the returned scope is closed is closed with it.
	 *
	 * @return the scope
	 */
	public static Scope open() {
		final List<AutoCloseable> resources = new ArrayList<>();
		SCOPES.get().push(resources);
		return new Scope(resources);
	}

	/**
	 * Registers a resource with the innermost open scope of the current thread.
	 * Outside a scope (for example at configuration time) the caller remains
	 * responsible for closing it.
	 *
	 * @param <D>      resource type
	 * @param resource the resource
	 * @return the same resource
	 */
	public static <D extends AutoCloseable> D register(final D resource) {
		final Deque<List<AutoCloseable>> scopes = SCOPES.get();
		if (!scopes.isEmpty()) {
			scopes.peek().add(resource);
		}
		return resource;
	}

	public static final class Scope implements AutoCloseable {
		private final List<AutoCloseable> resources;

		private Scope(final List<AutoCloseable> resources) {
			this.resources = resources;
		}

		@Override
		public void close() {
			final Deque<List<AutoCloseable>> scopes = SCOPES.get();
			// identity match: two empty scopes are equal() but must not be confused
			scopes.removeIf(r -> r == resources);
			if (scopes.isEmpty()) {
				SCOPES.remove();
			}
			for (int i = resources.size() - 1; i >= 0; i--) {
				try {
					resources.get(i).close();
				} catch (final Exception e) {
					LOGGER.warn("Failed to close a data source created by the task: {}", e.toString());
				}
			}
			resources.clear();
		}
	}
}
