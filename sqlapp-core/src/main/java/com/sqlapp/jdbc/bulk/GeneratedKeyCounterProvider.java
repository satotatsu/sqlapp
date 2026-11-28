/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

/**
 * Database-specific access to the generator behind an identity or serial column,
 * used to raise it after imported keys so later automatic inserts cannot
 * collide with them. Implementations live in dialect modules and are found with
 * {@link ServiceLoader}; use {@link GeneratedKeyCounters} to synchronize.
 */
public interface GeneratedKeyCounterProvider {
	boolean supports(Dialect dialect);

	/**
	 * @return true when the database advances the counter past explicitly inserted
	 *         keys by itself (for example MySQL AUTO_INCREMENT), so nothing is done
	 */
	default boolean followsInsertedKeys() {
		return false;
	}

	/**
	 * @return true when the counter may only be changed when the caller asked for
	 *         it explicitly (Db2 never restarts a generator automatically)
	 */
	default boolean requiresExplicitRequest() {
		return false;
	}

	/**
	 * @return false when the target column has no generator, so there is nothing
	 *         to synchronize
	 */
	boolean hasGenerator(Connection connection, Dialect dialect, Table table, Column column) throws SQLException;

	/**
	 * @return the next value the generator will hand out, an allocation boundary at
	 *         or above it, or null when it cannot be read
	 */
	BigInteger currentNext(Connection connection, Dialect dialect, Table table, Column column) throws SQLException;

	/**
	 * Restarts the generator so its next value is {@code next}. Called only when
	 * {@code next} is beyond {@link #currentNext}.
	 *
	 * @return the executed statements, for reporting
	 */
	List<String> restart(Connection connection, Dialect dialect, Table table, Column column, BigInteger next)
			throws SQLException;

	static Optional<GeneratedKeyCounterProvider> find(final Dialect dialect) {
		java.util.Objects.requireNonNull(dialect, "dialect");
		final List<GeneratedKeyCounterProvider> providers = ServiceLoader.load(GeneratedKeyCounterProvider.class)
				.stream().map(ServiceLoader.Provider::get).filter(provider -> provider.supports(dialect)).toList();
		if (providers.size() > 1) {
			throw new IllegalStateException("Multiple generated-key counter providers support: "
					+ dialect.getProductName() + " (" + providers.stream().map(p -> p.getClass().getName()).toList()
					+ ")");
		}
		return providers.stream().findFirst();
	}
}
