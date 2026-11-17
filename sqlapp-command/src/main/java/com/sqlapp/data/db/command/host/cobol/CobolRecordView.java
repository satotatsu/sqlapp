/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.host.cobol;

import java.nio.charset.CharacterCodingException;
import java.util.HexFormat;

/** Read-only view of one record for record type checks. */
public final class CobolRecordView {

	private final byte[] bytes;
	private final int length;
	private final HostTextDecoder decoder;

	CobolRecordView(byte[] bytes, int length, HostTextDecoder decoder) {
		this.bytes = bytes;
		this.length = length;
		this.decoder = decoder;
	}

	/** Record length in bytes. */
	public int length() {
		return length;
	}

	/** Byte at {@code offset} as 0-255. */
	public int byteAt(int offset) {
		check(offset, 1);
		return bytes[offset] & 0xFF;
	}

	/** Bytes in upper-case hexadecimal, such as {@code C8F1}. */
	public String hex(int offset, int count) {
		check(offset, count);
		return HexFormat.of().withUpperCase().formatHex(bytes, offset, offset + count);
	}

	/**
	 * Single-byte characters; a range beyond the record end returns {@code null}.
	 */
	public String text(int offset, int count) {
		if (offset < 0 || count < 0 || offset + count > length) {
			return null;
		}
		try {
			return decoder.singleByteText(bytes, offset, count);
		} catch (CharacterCodingException e) {
			return null;
		}
	}

	private void check(int offset, int count) {
		if (offset < 0 || count < 0 || offset + count > length) {
			throw new IndexOutOfBoundsException(
					"Range " + offset + "+" + count + " is outside the record of " + length + " bytes.");
		}
	}
}
