/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlSplitter.SplitResult;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.FileUtils;

/** One name-addressed SQL migration reapplied whenever its content changes. */
public record RepeatableMigrationFile(String name, File source, List<SplitResult> statements, String checksum) {
	private static final Pattern NAME = Pattern.compile("R__(.+)\\.sql", Pattern.CASE_INSENSITIVE);

	public RepeatableMigrationFile {
		statements = List.copyOf(statements);
	}

	public static List<RepeatableMigrationFile> read(final File directory, final boolean recursive,
			final String encoding, final SqlSplitter splitter) {
		if (directory == null || !directory.isDirectory()) {
			return List.of();
		}
		final List<File> sources = new ArrayList<>();
		collect(directory, recursive, sources);
		sources.sort(Comparator.comparing(File::getName).thenComparing(File::getAbsolutePath));
		final Set<String> names = new HashSet<>();
		final List<RepeatableMigrationFile> result = new ArrayList<>();
		for (final File source : sources) {
			final Matcher matcher = NAME.matcher(source.getName());
			if (!matcher.matches()) {
				continue;
			}
			final String name = matcher.group(1);
			if (!names.add(name)) {
				throw new CommandException("Duplicate repeatable migration name: " + name);
			}
			final String sql = FileUtils.readText(source, encoding);
			result.add(new RepeatableMigrationFile(name, source, splitter.parse(sql), checksum(sql)));
		}
		return List.copyOf(result);
	}

	private static void collect(final File directory, final boolean recursive, final List<File> files) {
		final File[] children = directory.listFiles();
		if (children == null) {
			return;
		}
		for (final File child : children) {
			if (child.isFile()) {
				files.add(child);
			} else if (recursive && child.isDirectory()) {
				collect(child, true, files);
			}
		}
	}

	private static String checksum(final String sql) {
		try {
			return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(sql.getBytes(StandardCharsets.UTF_8)));
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
