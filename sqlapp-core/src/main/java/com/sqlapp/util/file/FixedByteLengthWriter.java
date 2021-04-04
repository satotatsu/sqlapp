/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.util.file;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.function.Consumer;

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowIteratorHandler;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.file.FixedByteLengthFileSetting.FixedByteLengthFieldSetting;

public class FixedByteLengthWriter extends AbstractFixedByteLength implements AutoCloseable {

	private final BufferedOutputStream bos; 
    private final byte[] buffer;
    private final FixedByteLengthFileSetting setting;
	
	public FixedByteLengthWriter(final File file, final Charset charset, final Consumer<FixedByteLengthFileSetting> cons) {
		super(new FixedByteLengthFileSetting(), charset, cons);
		this.bos=toBufferedOutputStream(file);
        setting=getCharsetSetting().clone();
        buffer=setting.createBuffer();
	}
	
	public FixedByteLengthWriter(final File file, final Charset charset, final Table table, final Consumer<FixedByteLengthFieldSetting> cons) {
		super(new FixedByteLengthFileSetting(), charset, table, cons);
		this.bos=toBufferedOutputStream(file);
        setting=getCharsetSetting().clone();
        buffer=setting.createBuffer();
	}

	public FixedByteLengthWriter(final OutputStream os, final Charset charset) {
		super(new FixedByteLengthFileSetting(), charset, (setting)->{});
		this.bos = toBufferedOutputStream(os);
        setting=getCharsetSetting().clone();
        buffer=setting.createBuffer();
	}

	public FixedByteLengthWriter(final OutputStream os, final Charset charset, final Table table, final Consumer<FixedByteLengthFieldSetting> cons) {
		super(new FixedByteLengthFileSetting(), charset, table, cons);
		this.bos = toBufferedOutputStream(os);
        setting=getCharsetSetting().clone();
        buffer=setting.createBuffer();
	}

	public FixedByteLengthWriter(final File file, final String charset) {
		super(new FixedByteLengthFileSetting(), Charset.forName(charset), (setting)->{});
		this.bos = toBufferedOutputStream(file);
        setting=getCharsetSetting().clone();
        buffer=setting.createBuffer();
	}

	public FixedByteLengthWriter(final OutputStream os, final String charset) {
		super(new FixedByteLengthFileSetting(), Charset.forName(charset), (setting)->{});
		this.bos = toBufferedOutputStream(os);
        setting=getCharsetSetting().clone();
        buffer=setting.createBuffer();
	}

	private static BufferedOutputStream toBufferedOutputStream(final File file) {
		try {
			return new BufferedOutputStream(new FileOutputStream(file));
		} catch (final FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	private static BufferedOutputStream toBufferedOutputStream(final OutputStream os) {
		if (os instanceof BufferedOutputStream) {
			return (BufferedOutputStream)os;
		}
		return new BufferedOutputStream(os);
	}
	
	public void writeRow(final byte[] arg) throws IOException  {
		bos.write(arg);
	}

	public void writeRow(final Row row) throws IOException  {
		setting.setByteRow(row, buffer);
		writeRow(buffer);
	}

	public void write(final Table table, final RowIteratorHandler rowIteratorHandler) throws IOException  {
		for(final Row row:table.getRows(rowIteratorHandler)) {
			setting.setByteRow(row, buffer);
			writeRow(buffer);
		}
	}

	public void write(final Table table) throws IOException  {
		for(final Row row:table.getRows()) {
			setting.setByteRow(row, buffer);
			writeRow(buffer);
		}
	}

	@Override
	public void close() {
		FileUtils.close(bos);
	}

}
