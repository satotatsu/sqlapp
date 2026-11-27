/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.driver;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import org.w3c.dom.Element;

/**
 * Reads artifacts from Maven repositories ({@code https}, {@code http} or
 * {@code file}), trying each repository in order. Downloads are verified with
 * the repository's {@code .sha256}, {@code .sha512} or {@code .sha1} checksum;
 * an artifact without a checksum is rejected.
 */
public final class MavenRepositoryClient {
	/** Maven Central. */
	public static final URI MAVEN_CENTRAL = URI.create("https://repo.maven.apache.org/maven2/");
	/** Largest artifact accepted. */
	public static final long MAX_ARTIFACT_BYTES = 256L * 1024 * 1024;
	/** Largest POM, metadata or checksum accepted. */
	public static final long MAX_TEXT_BYTES = 8L * 1024 * 1024;
	private static final String[][] CHECKSUMS = { { ".sha256", "SHA-256" }, { ".sha512", "SHA-512" },
			{ ".sha1", "SHA-1" } };
	private final List<URI> repositories;
	private HttpClient http;

	/** A downloaded file and the repository URL it came from. */
	public record Download(byte[] content, URI source) {
	}

	/**
	 * Versions published in the repositories.
	 *
	 * @param versions newest first, as listed in {@code maven-metadata.xml}
	 * @param release  the release version the metadata names, or null
	 */
	public record Versions(List<String> versions, String release) {
	}

	public MavenRepositoryClient(final List<URI> repositories) {
		if (repositories == null || repositories.isEmpty())
			throw new IllegalArgumentException("Specify at least one Maven repository URL.");
		final var normalized = new ArrayList<URI>();
		for (final URI repository : repositories)
			normalized.add(normalize(repository));
		this.repositories = List.copyOf(normalized);
	}

	/**
	 * Checks and normalizes a repository URL: {@code https}, {@code http} or
	 * {@code file}, no user name or password, ending with {@code /}.
	 */
	public static URI normalize(final URI repository) {
		if (repository == null)
			throw new IllegalArgumentException("Maven repository URL is empty.");
		final String scheme = repository.getScheme() == null ? "" : repository.getScheme().toLowerCase(Locale.ROOT);
		if (!List.of("https", "http", "file").contains(scheme) || !repository.isAbsolute())
			throw new IllegalArgumentException("Maven repository URL must start with https://, http:// or file:");
		if (repository.getRawUserInfo() != null)
			throw new IllegalArgumentException(
					"Do not put a user name or password in a Maven repository URL: " + redact(repository));
		if (repository.getRawQuery() != null || repository.getRawFragment() != null)
			throw new IllegalArgumentException("Maven repository URL must not have a query or fragment.");
		final String text = repository.toString();
		return URI.create(text.endsWith("/") ? text : text + "/");
	}

	private static String redact(final URI uri) {
		return uri.getScheme() + "://***@" + uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort())
				+ uri.getRawPath();
	}

	public List<URI> repositories() {
		return repositories;
	}

	/** Versions of a module, merged across repositories. */
	public Versions versions(final String groupId, final String artifactId) throws IOException {
		final var module = new MavenCoordinate(groupId, artifactId, null);
		final var versions = new LinkedHashSet<String>();
		String release = null;
		boolean found = false;
		IOException failure = null;
		for (final URI repository : repositories) {
			final byte[] content;
			try {
				content = fetch(repository.resolve(module.moduleDirectory() + "maven-metadata.xml"), MAX_TEXT_BYTES);
			} catch (final InterruptedIOException interrupted) {
				throw interrupted;
			} catch (final IOException problem) {
				failure = add(failure, problem);
				continue;
			}
			if (content == null)
				continue;
			found = true;
			final Element root = MavenXml.parse(content, "maven-metadata.xml of " + module.module());
			final Element versioning = MavenXml.child(root, "versioning");
			if (versioning == null)
				continue;
			if (release == null)
				release = MavenXml.text(versioning, "release");
			final var listed = new ArrayList<String>();
			final Element list = MavenXml.child(versioning, "versions");
			if (list != null)
				for (final Element version : MavenXml.children(list, "version"))
					listed.add(version.getTextContent().trim());
			for (int index = listed.size() - 1; index >= 0; index--)
				versions.add(listed.get(index));
		}
		if (!found) {
			if (failure != null)
				throw failure;
			throw new IOException("Not found in the Maven repositories: " + module.module());
		}
		return new Versions(List.copyOf(versions), release);
	}

	/**
	 * Downloads a repository path from the first repository that has it, and
	 * verifies its checksum from the same repository.
	 *
	 * @return the verified file, or null when no repository has it
	 */
	public Download download(final String path, final long limit) throws IOException {
		IOException failure = null;
		for (final URI repository : repositories) {
			final URI uri = repository.resolve(path);
			final byte[] content;
			try {
				content = fetch(uri, limit);
				if (content == null)
					continue;
				verify(repository, path, content);
			} catch (final InterruptedIOException interrupted) {
				throw interrupted;
			} catch (final IOException problem) {
				failure = add(failure, problem);
				continue;
			}
			return new Download(content, uri);
		}
		if (failure != null)
			throw failure;
		return null;
	}

	private static IOException add(final IOException first, final IOException next) {
		if (first == null)
			return next;
		first.addSuppressed(next);
		return first;
	}

	private void verify(final URI repository, final String path, final byte[] content) throws IOException {
		for (final String[] checksum : CHECKSUMS) {
			final URI uri = repository.resolve(path + checksum[0]);
			final byte[] published = fetch(uri, MAX_TEXT_BYTES);
			if (published == null)
				continue;
			final String expected = new String(published, StandardCharsets.US_ASCII).trim().split("\\s+")[0]
					.toLowerCase(Locale.ROOT);
			final String actual;
			try {
				actual = HexFormat.of().formatHex(MessageDigest.getInstance(checksum[1]).digest(content));
			} catch (final NoSuchAlgorithmException impossible) {
				throw new IllegalStateException(impossible);
			}
			if (!actual.equals(expected))
				throw new IOException("Checksum mismatch (" + checksum[1] + ") for " + repository.resolve(path)
						+ ". The download was discarded.");
			return;
		}
		throw new IOException("No .sha256, .sha512 or .sha1 checksum published for " + repository.resolve(path)
				+ ". Unverified files are not used.");
	}

	/** @return the content, or null when the file does not exist */
	private byte[] fetch(final URI uri, final long limit) throws IOException {
		if ("file".equalsIgnoreCase(uri.getScheme())) {
			final Path file = Path.of(uri);
			if (!Files.isRegularFile(file))
				return null;
			if (Files.size(file) > limit)
				throw new IOException("File is larger than " + limit + " bytes: " + uri);
			return Files.readAllBytes(file);
		}
		final var request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5))
				.header("User-Agent", "sqlapp-jdbc-driver-download").GET().build();
		final HttpResponse<InputStream> response;
		try {
			response = client().send(request, HttpResponse.BodyHandlers.ofInputStream());
		} catch (final InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new InterruptedIOException("Download cancelled: " + uri);
		}
		try (InputStream body = response.body()) {
			final int status = response.statusCode();
			if (status == 404 || status == 410)
				return null;
			if (status != 200)
				throw new IOException("HTTP " + status + " from " + uri);
			final byte[] content = body.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, limit + 1));
			if (content.length > limit)
				throw new IOException("File is larger than " + limit + " bytes: " + uri);
			return content;
		}
	}

	private synchronized HttpClient client() {
		if (http == null) {
			final var builder = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
					.connectTimeout(Duration.ofSeconds(30));
			final ProxySelector proxy = ProxySelector.getDefault();
			if (proxy != null)
				builder.proxy(proxy);
			http = builder.build();
		}
		return http;
	}
}
