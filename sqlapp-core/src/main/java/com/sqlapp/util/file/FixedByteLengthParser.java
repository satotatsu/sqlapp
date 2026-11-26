/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.util.file;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.file.FixedByteLengthFileSetting.FixedByteLengthFieldSetting;

public class FixedByteLengthParser extends AbstractFixedByteLength implements AutoCloseable {

	private final BufferedInputStream bis;

	public FixedByteLengthParser(final File file, final Charset charset,
			final Consumer<FixedByteLengthFileSetting> cons) {
		super(new FixedByteLengthFileSetting(), charset, cons);
		this.bis = toBufferedInputStream(file);
	}

	public FixedByteLengthParser(final File file, final Charset charset, final Table table,
			final Consumer<FixedByteLengthFileSetting> cons, final Consumer<FixedByteLengthFieldSetting> fieldCons) {
		super(new FixedByteLengthFileSetting(), charset, table, cons, fieldCons);
		this.bis = toBufferedInputStream(file);
	}

	public FixedByteLengthParser(final File file, final Charset charset, final Table table,
			final Consumer<FixedByteLengthFileSetting> cons) {
		super(new FixedByteLengthFileSetting(), charset, table, cons);
		this.bis = toBufferedInputStream(file);
	}

	public FixedByteLengthParser(final File file, final String charset) {
		super(new FixedByteLengthFileSetting(), Charset.forName(charset), (setting) -> {
		});
		this.bis = toBufferedInputStream(file);
	}

	public FixedByteLengthParser(final InputStream is, final Charset charset,
			final Consumer<FixedByteLengthFileSetting> cons) {
		super(new FixedByteLengthFileSetting(), charset, cons);
		this.bis = toBufferedInputStream(is);
	}

	public FixedByteLengthParser(final InputStream is, final Charset charset, final Table table,
			final Consumer<FixedByteLengthFileSetting> cons, final Consumer<FixedByteLengthFieldSetting> fieldCons) {
		super(new FixedByteLengthFileSetting(), charset, table, cons, fieldCons);
		this.bis = toBufferedInputStream(is);
	}

	public FixedByteLengthParser(final InputStream is, final Charset charset, final Table table,
			final Consumer<FixedByteLengthFileSetting> cons) {
		super(new FixedByteLengthFileSetting(), charset, table, cons);
		this.bis = toBufferedInputStream(is);
	}

	public FixedByteLengthParser(final InputStream is, final Charset charset) {
		super(new FixedByteLengthFileSetting(), charset, (setting) -> {
		});
		this.bis = toBufferedInputStream(is);
	}

	public FixedByteLengthParser(final InputStream is, final String charset) {
		super(new FixedByteLengthFileSetting(), Charset.forName(charset), (setting) -> {
		});
		this.bis = toBufferedInputStream(is);
	}

	private static BufferedInputStream toBufferedInputStream(final File file) {
		try {
			return new BufferedInputStream(new FileInputStream(file));
		} catch (final FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	private static BufferedInputStream toBufferedInputStream(final InputStream is) {
		if (is instanceof BufferedInputStream) {
			return (BufferedInputStream) is;
		}
		return new BufferedInputStream(is);
	}

	/**
	 * Reads complete fixed-size records, including configured line-break bytes.
	 * @throws EOFException if the final record is incomplete
	 */
	public void readAll(final BiConsumer<byte[], Long> cons) throws IOException {
		long i = 0;
		final FixedByteLengthFileSetting setting = getCharsetSetting().clone();
		final byte[] buffer = setting.createBuffer();
		while (readRecord(buffer)) {
			if (i == Long.MAX_VALUE) {
				i = 0;
				cons.accept(buffer, i);
			} else {
				cons.accept(buffer, i++);
			}
		}
	}

	/**
	 * Reads complete fixed-size records as schema rows.
	 * @throws EOFException if the final record is incomplete
	 */
	public void readAllRecord(final BiConsumer<Row, Long> cons) throws IOException {
		long i = 0;
		final FixedByteLengthFileSetting setting = getCharsetSetting().clone();
		final byte[] buffer = setting.createBuffer();
		while (readRecord(buffer)) {
			final Row row = setting.toRow(buffer);
			if (i == Long.MAX_VALUE) {
				i = 0;
				cons.accept(row, i);
			} else {
				cons.accept(row, i++);
			}
		}
	}

	private boolean readRecord(final byte[] buffer) throws IOException {
		if (buffer.length == 0) {
			throw new IllegalArgumentException("Fixed record length must be greater than zero.");
		}
		final int length = bis.readNBytes(buffer, 0, buffer.length);
		if (length == 0) {
			return false;
		}
		if (length != buffer.length) {
			throw new EOFException("Incomplete fixed-length record: expected " + buffer.length
					+ " bytes but read " + length + ".");
		}
		return true;
	}

	@Override
	public void close() {
		try {
			bis.close();
		} catch (final IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
