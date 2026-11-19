package com.sqlapp.data.db.dialect.spanner.sql;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.util.JsonConverter;

/** Stores typed stream definitions in the existing Catalog XML extension data. */
public final class SpannerChangeStreams {
	static final String KEY = "SPANNER_CHANGE_STREAMS";

	private SpannerChangeStreams() {
	}

	public static void set(final Catalog catalog, final Collection<SpannerChangeStream> streams) {
		final var definitions = index(streams);
		catalog.getSpecifics().put(KEY, new JsonConverter().getObjectMapper().writeValueAsString(definitions.values()));
	}

	/** An absent value represents a catalog with no change stream snapshot. */
	public static Optional<List<SpannerChangeStream>> get(final Catalog catalog) {
		final String json = catalog.getSpecifics().get(KEY);
		if (json == null)
			return Optional.empty();
		final var streams = new JsonConverter().getObjectMapper().readValue(json, SpannerChangeStream[].class);
		return Optional.of(List.copyOf(index(List.of(streams)).values()));
	}

	static Map<String, SpannerChangeStream> index(final Collection<SpannerChangeStream> streams) {
		final var result = new TreeMap<String, SpannerChangeStream>(String.CASE_INSENSITIVE_ORDER);
		for (final var stream : streams)
			if (result.putIfAbsent(stream.name(), stream) != null)
				throw new IllegalArgumentException("Duplicate Spanner change stream: " + stream.name());
		return Collections.unmodifiableMap(result);
	}
}
