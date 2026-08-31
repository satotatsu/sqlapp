/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import com.sqlapp.jdbc.bulk.BulkMigrationJobLease;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseStore;

/** Cross-process migration job leases stored by locked atomic file replacement. */
public final class FileBulkMigrationJobLeaseStore
		implements BulkMigrationJobLeaseStore {
	private static final ConcurrentMap<Path, ReentrantLock> JVM_LOCKS =
			new ConcurrentHashMap<>();
	private final Path directory;

	public FileBulkMigrationJobLeaseStore(final Path directory) {
		this.directory = Objects.requireNonNull(directory, "directory")
				.toAbsolutePath().normalize();
	}

	@Override
	public Optional<BulkMigrationJobLease> load(final String planFingerprint)
			throws SQLException {
		validateFingerprint(planFingerprint);
		return loadFile(planFingerprint);
	}

	@Override
	public boolean tryAcquire(final BulkMigrationJobLease lease, final Instant now)
			throws SQLException {
		validateCandidate(lease, now);
		return locked(lease.planFingerprint(), () -> {
			final BulkMigrationJobLease current = loadFile(lease.planFingerprint())
					.orElse(null);
			if (current != null && !current.isExpiredAt(now)) {
				return false;
			}
			write(lease);
			return true;
		});
	}

	@Override
	public boolean renew(final BulkMigrationJobLease lease, final Instant now)
			throws SQLException {
		validateCandidate(lease, now);
		return locked(lease.planFingerprint(), () -> {
			final BulkMigrationJobLease current = loadFile(lease.planFingerprint())
					.orElse(null);
			if (current == null || current.isExpiredAt(now)
					|| !current.ownerId().equals(lease.ownerId())) {
				return false;
			}
			write(lease);
			return true;
		});
	}

	@Override
	public void release(final String planFingerprint, final String ownerId)
			throws SQLException {
		new BulkMigrationJobLease(planFingerprint, ownerId, Instant.MAX);
		locked(planFingerprint, () -> {
			final BulkMigrationJobLease current = loadFile(planFingerprint).orElse(null);
			if (current != null && current.ownerId().equals(ownerId)) {
				Files.deleteIfExists(stateFile(planFingerprint));
			}
			return null;
		});
	}

	private Optional<BulkMigrationJobLease> loadFile(final String fingerprint)
			throws SQLException {
		final Path file = stateFile(fingerprint);
		if (!Files.exists(file)) {
			return Optional.empty();
		}
		final Properties values = new Properties();
		try (InputStream input = Files.newInputStream(file)) {
			values.load(input);
			if (!fingerprint.equals(required(values, "planFingerprint"))) {
				throw new IllegalArgumentException(
						"lease planFingerprint does not match its file");
			}
			return Optional.of(new BulkMigrationJobLease(fingerprint,
					required(values, "ownerId"),
					Instant.parse(required(values, "expiresAt"))));
		} catch (IOException | IllegalArgumentException e) {
			throw new SQLException("Failed to read migration job lease: " + file, e);
		}
	}

	private void write(final BulkMigrationJobLease lease) throws SQLException {
		final Path file = stateFile(lease.planFingerprint());
		Path temporary = null;
		try {
			temporary = Files.createTempFile(directory, file.getFileName().toString(),
					".tmp");
			final Properties values = new Properties();
			values.setProperty("planFingerprint", lease.planFingerprint());
			values.setProperty("ownerId", lease.ownerId());
			values.setProperty("expiresAt", lease.expiresAt().toString());
			try (OutputStream output = Files.newOutputStream(temporary)) {
				values.store(output, "sqlapp bulk migration job lease");
			}
			try {
				Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
			}
			temporary = null;
		} catch (IOException e) {
			throw new SQLException("Failed to save migration job lease: " + file, e);
		} finally {
			if (temporary != null) {
				try {
					Files.deleteIfExists(temporary);
				} catch (IOException ignored) {
				}
			}
		}
	}

	private <T> T locked(final String fingerprint, final IoCallable<T> callable)
			throws SQLException {
		final Path lockFile = lockFile(fingerprint);
		final ReentrantLock jvmLock = JVM_LOCKS.computeIfAbsent(lockFile,
				key -> new ReentrantLock());
		jvmLock.lock();
		try {
			Files.createDirectories(directory);
			try (FileChannel channel = FileChannel.open(lockFile,
					StandardOpenOption.CREATE, StandardOpenOption.WRITE);
					var ignored = channel.lock()) {
				return callable.call();
			}
		} catch (IOException e) {
			throw new SQLException("Failed to lock migration job lease: " + fingerprint, e);
		} finally {
			jvmLock.unlock();
		}
	}

	private Path stateFile(final String fingerprint) {
		return directory.resolve(hash(fingerprint) + ".lease");
	}

	private Path lockFile(final String fingerprint) {
		return directory.resolve(hash(fingerprint) + ".lease.lock");
	}

	private static String hash(final String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void validateFingerprint(final String fingerprint) {
		new BulkMigrationJobLease(fingerprint, "validation", Instant.MAX);
	}

	private static void validateCandidate(final BulkMigrationJobLease lease,
			final Instant now) {
		Objects.requireNonNull(lease, "lease");
		Objects.requireNonNull(now, "now");
		if (lease.isExpiredAt(now)) {
			throw new IllegalArgumentException("lease must expire after now");
		}
	}

	private static String required(final Properties values, final String name) {
		final String value = values.getProperty(name);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Missing migration lease property: " + name);
		}
		return value;
	}

	@FunctionalInterface
	private interface IoCallable<T> {
		T call() throws IOException, SQLException;
	}
}
