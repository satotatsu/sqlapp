/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import com.sqlapp.data.db.command.AbstractDataSourceCommand;

/**
 * Preserves service failures while releasing a command-owned runtime
 * DataSource.
 */
abstract class AbstractTransferFileCommand extends AbstractDataSourceCommand {
	protected final <T> T withRuntimeSource(java.util.concurrent.Callable<T> action) {
		Throwable primary = null;
		try {
			if (getDataSource() == null)
				throw new IllegalArgumentException("A runtime DataSource is required.");
			return action.call();
		} catch (RuntimeException | Error failure) {
			primary = failure;
			throw failure;
		} catch (Exception failure) {
			final var safe = new IllegalStateException(
					"Transfer file command failed; check configuration, package and runtime connection.");
			primary = safe;
			throw safe;
		} finally {
			if (isCloseDataSource() && getDataSource() instanceof AutoCloseable closeable) {
				try {
					closeable.close();
				} catch (Exception failure) {
					final var safe = new IllegalStateException(
							"Runtime DataSource cleanup failed. Inspect retained transfer results before retrying.");
					if (primary != null)
						primary.addSuppressed(safe);
					else
						throw safe;
				}
			}
		}
	}
}
