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

package com.sqlapp.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public enum MessageDigests {
	MD2("MD2")
	/** MD5 */
	, MD5("MD5")
	/** SHA */
	, SHA("SHA")
	/** SHA-224 */
	, SHA224("SHA-224")
	/** SHA-256 */
	, SHA256("SHA-256")
	/** SHA-384 */
	, SHA384("SHA-384")
	/** SHA-512 */
	, SHA512("SHA-512"),
	;

	private String value;

	private MessageDigests(final String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	/**
	 * ファイルからチェックサムを生成します。
	 * 
	 * @param file
	 */
	public byte[] checksum(final File file) {
		try (FileInputStream in = new FileInputStream(file)) {
			return checksum(in);
		} catch (final FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * ファイルからチェックサム文字列を生成します。
	 * 
	 * @param file
	 */
	public String checksumAsString(final File file) {
		return toHex(checksum(file));
	}

	/**
	 * ストリームからチェックサムを生成します。
	 * 
	 * @param in
	 */
	public byte[] checksum(final InputStream in) {
		try {
			final MessageDigest digester = MessageDigest.getInstance(this.getValue());
			final byte[] block = new byte[4096];
			int length;
			while ((length = in.read(block)) > 0) {
				digester.update(block, 0, length);
			}
			return digester.digest();
		} catch (final IOException e) {
			throw new RuntimeException(e);
		} catch (final NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * ストリームからチェックサム文字列を生成します。
	 * 
	 * @param in
	 */
	public String checksumAsString(final InputStream in) {
		return toHex(checksum(in));
	}

	/**
	 * バイト配列からチェックサムを生成します。
	 * 
	 * @param bytes
	 */
	public byte[] checksum(final byte[] bytes) {
		try {
			final MessageDigest digester = MessageDigest.getInstance(this.getValue());
			digester.update(bytes, 0, bytes.length);
			return digester.digest();
		} catch (final NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 複数の文字列からチェックサムを生成します。
	 * 
	 * @param args
	 */
	public byte[] checksum(final String... args) {
		try {
			final MessageDigest digester = MessageDigest.getInstance(this.getValue());
			for (final String arg : args) {
				digester.update(arg.getBytes("UTF8"));
			}
			return digester.digest();
		} catch (final NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		} catch (final UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * バイト配列からチェックサム文字列を生成します。
	 * 
	 * @param bytes
	 */
	public String checksumAsString(final byte[] bytes) {
		return toHex(checksum(bytes));
	}

	public static String toHex(final byte[] bytes) {
		if (bytes == null) {
			return null;
		}
		final StringBuilder buf = new StringBuilder(bytes.length * 2);
		for (int i = 0; i < bytes.length; i++) {
			int b = bytes[i];
			if (b < 0) {
				b += 256;
			}
			if (b < 16) {
				buf.append("0");
			}
			buf.append(Integer.toString(b, 16));
		}
		return buf.toString();
	}
}
