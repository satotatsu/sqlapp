/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.sql.SQLException;
import java.util.*;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;

/** Duplicate-preserving multiset comparison using externally sorted SHA-256 row hashes. */
public final class BulkMigrationUnorderedVerifier {
	public static final String HASH_PREFIX = "multiset-sha256-v1:";
	private BulkMigrationUnorderedVerifier() {
	}

	public static BulkMigrationVerificationResult verify(Table expected, Iterator<Row> expectedRows,
			Table actual, Iterator<Row> actualRows, List<String> expectedNames, List<String> actualNames,
			int chunkSize) throws SQLException {
		return verify(expected, expectedRows, actual, actualRows, expectedNames, actualNames, chunkSize, 65_536);
	}

	static BulkMigrationVerificationResult verify(Table expected, Iterator<Row> expectedRows,
			Table actual, Iterator<Row> actualRows, List<String> expectedNames, List<String> actualNames,
			int chunkSize, int rowsPerRun) throws SQLException {
		Throwable failure = null;
		try {
			if (chunkSize <= 0 || rowsPerRun <= 0 || expectedNames.isEmpty()
					|| expectedNames.size() != actualNames.size()
					|| new HashSet<>(expectedNames).size() != expectedNames.size()
					|| new HashSet<>(actualNames).size() != actualNames.size())
				throw new IllegalArgumentException("Require positive sizes and equal, nonempty unique verification columns.");
			var canonical = columns(expected, expectedNames);
			var actualColumns = columns(actual, actualNames);
			try (var spool = new HashSpool()) {
				Path left = spool.sort(expectedRows, canonical, canonical, rowsPerRun);
				Path right = spool.sort(actualRows, actualColumns, canonical, rowsPerRun);
				try (var expectedHashes = Files.newBufferedReader(left, StandardCharsets.US_ASCII);
						var actualHashes = Files.newBufferedReader(right, StandardCharsets.US_ASCII)) {
					var chunks = new ArrayList<BulkMigrationVerificationChunk>();
					long expectedCount = 0, actualCount = 0;
					while (true) {
						var e = hashChunk(expectedHashes, chunkSize);
						var a = hashChunk(actualHashes, chunkSize);
						if (e.rows() == 0 && a.rows() == 0) break;
						chunks.add(new BulkMigrationVerificationChunk(chunks.size(), e.rows(), a.rows(), e.hash(), a.hash()));
						expectedCount = Math.addExact(expectedCount, e.rows());
						actualCount = Math.addExact(actualCount, a.rows());
					}
					return new BulkMigrationVerificationResult(chunkSize, expectedCount, actualCount, expectedNames, chunks);
				}
			}
		} catch (IOException io) {
			var rejected = new SQLException("Unable to sort row fingerprints for unordered verification.", io);
			failure = rejected;
			throw rejected;
		} catch (RuntimeException | Error rejected) {
			failure = rejected;
			throw rejected;
		} finally {
			BulkMigrationIteratorSupport.close(failure, expectedRows, actualRows);
		}
	}

	private static List<Column> columns(Table table, List<String> names) {
		return names.stream().map(name -> {
			var column = table.getColumns().get(name);
			if (column == null) throw new IllegalArgumentException("Unknown verification column: " + name);
			return column;
		}).toList();
	}

	private record ChunkHash(int rows, String hash) {
	}

	private static ChunkHash hashChunk(BufferedReader reader, int size) throws IOException {
		MessageDigest hash;
		try { hash = MessageDigest.getInstance("SHA-256"); }
		catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
		int count = 0;
		for (; count < size; count++) {
			String value = reader.readLine();
			if (value == null) break;
			if (value.length() != 64) throw new IOException("Truncated verification hash run.");
			hash.update(HexFormat.of().parseHex(value));
		}
		return new ChunkHash(count, HASH_PREFIX + HexFormat.of().formatHex(hash.digest()));
	}

	/** Bounded runs and at most 32 open readers; temporary files contain hashes only. */
	private static final class HashSpool implements AutoCloseable {
		private final Path directory = Files.createTempDirectory("sqlapp-row-hashes-");
		HashSpool() throws IOException {
		}

		Path sort(Iterator<Row> rows, List<Column> values, List<Column> canonical, int limit) throws IOException {
			var runs = new ArrayList<Path>();
			var buffer = new ArrayList<String>(limit);
			while (rows.hasNext()) {
				buffer.add(BulkMigrationHash.rows(List.of(rows.next()), values, canonical));
				if (buffer.size() == limit) { runs.add(write(buffer)); buffer.clear(); }
			}
			if (!buffer.isEmpty() || runs.isEmpty()) runs.add(write(buffer));
			while (runs.size() > 1) {
				var next = new ArrayList<Path>();
				for (int start = 0; start < runs.size(); start += 32)
					next.add(merge(runs.subList(start, Math.min(start + 32, runs.size()))));
				runs = next;
			}
			return runs.getFirst();
		}

		private Path write(List<String> values) throws IOException {
			values.sort(Comparator.naturalOrder());
			Path run = Files.createTempFile(directory, "run-", ".sha256");
			Files.write(run, values, StandardCharsets.US_ASCII);
			return run;
		}

		private record Head(String value, int reader) {
		}

		private Path merge(List<Path> inputs) throws IOException {
			Path output = Files.createTempFile(directory, "merge-", ".sha256");
			var readers = new ArrayList<BufferedReader>();
			IOException closeFailure = null;
			Throwable primary = null;
			try (var writer = Files.newBufferedWriter(output, StandardCharsets.US_ASCII)) {
				var queue = new PriorityQueue<Head>(Comparator.comparing(Head::value));
				for (Path input : inputs) {
					var reader = Files.newBufferedReader(input, StandardCharsets.US_ASCII);
					readers.add(reader);
					String value = reader.readLine();
					if (value != null) queue.add(new Head(value, readers.size() - 1));
				}
				while (!queue.isEmpty()) {
					var head = queue.remove();
					writer.write(head.value()); writer.newLine();
					String value = readers.get(head.reader()).readLine();
					if (value != null) queue.add(new Head(value, head.reader()));
				}
			} catch (IOException | RuntimeException | Error rejected) {
				primary = rejected;
				throw rejected;
			} finally {
				for (var reader : readers) try { reader.close(); }
				catch (IOException rejected) {
					if (primary != null) primary.addSuppressed(rejected);
					else if (closeFailure == null) closeFailure = rejected;
					else closeFailure.addSuppressed(rejected);
				}
				if (closeFailure != null) throw closeFailure;
			}
			for (Path input : inputs) Files.delete(input);
			return output;
		}

		@Override public void close() throws IOException {
			List<Path> files;
			try (var walk = Files.walk(directory)) { files = walk.sorted(Comparator.reverseOrder()).toList(); }
			IOException failure = null;
			for (Path path : files) try { Files.deleteIfExists(path); }
			catch (IOException rejected) {
				if (failure == null) failure = rejected; else failure.addSuppressed(rejected);
			}
			if (failure != null) throw failure;
		}
	}
}
