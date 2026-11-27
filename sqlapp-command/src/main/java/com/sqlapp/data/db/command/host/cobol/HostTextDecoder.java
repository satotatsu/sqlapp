/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.host.cobol;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

import com.sqlapp.exceptions.CommandException;

/**
 * Decodes host (EBCDIC) text.
 * <p>
 * {@link #text} decodes DISPLAY data that may mix single-byte characters and
 * double-byte characters enclosed in shift codes (PIC X). {@link #graphic}
 * decodes pure double-byte data without shift codes (PIC N, PIC G).
 * </p>
 * <p>
 * {@code IBM} uses a JDK charset such as {@code x-IBM939} or {@code x-IBM930}
 * with the shift codes X'0E' and X'0F'. {@code TABLE} uses conversion tables
 * supplied as files, for code sets without a JDK charset such as Fujitsu JEF;
 * its shift codes must be configured. Both accept a table of user-defined
 * double-byte characters (gaiji) that takes precedence over the code set.
 * </p>
 */
public final class HostTextDecoder {

	/** Code set family. */
	public enum Profile {
		/** IBM EBCDIC through a JDK charset. */
		IBM,
		/** Conversion tables supplied as files, such as Fujitsu JEF. */
		TABLE
	}

	/** What to do with bytes that have no character. */
	public enum Unmappable {
		ERROR, REPLACE
	}

	private final Profile profile;
	private final Charset charset;
	private final char[] singleByte;
	private final Map<Integer, String> doubleByte;
	private final Map<Integer, String> userDefined;
	private final int shiftOut;
	private final int shiftIn;
	private final Unmappable unmappable;

	private HostTextDecoder(Profile profile, Charset charset, char[] singleByte, Map<Integer, String> doubleByte,
			Map<Integer, String> userDefined, int shiftOut, int shiftIn, Unmappable unmappable) {
		this.profile = profile;
		this.charset = charset;
		this.singleByte = singleByte;
		this.doubleByte = doubleByte;
		this.userDefined = userDefined;
		this.shiftOut = shiftOut;
		this.shiftIn = shiftIn;
		this.unmappable = unmappable;
	}

	/**
	 * Creates a decoder from the extraction configuration; relative files resolve
	 * against {@code baseDirectory}.
	 */
	public static HostTextDecoder of(CobolCsvExtractConfiguration.Encoding encoding, File baseDirectory) {
		Profile profile = encoding.getProfile();
		Unmappable unmappable = encoding.getUnmappable();
		Map<Integer, String> userDefined = encoding.getUserDefinedCharacters() == null ? Map.of()
				: readTable(file(baseDirectory, encoding.getUserDefinedCharacters()), 2, "userDefinedCharacters");
		if (profile == Profile.IBM) {
			if (encoding.getCharset() == null) {
				throw new CommandException("encoding.charset is required for profile IBM, such as x-IBM939.");
			}
			Charset charset;
			try {
				charset = Charset.forName(encoding.getCharset());
			} catch (RuntimeException e) {
				throw new CommandException("Unsupported charset: " + encoding.getCharset(), e);
			}
			if (encoding.getSingleByteTable() != null || encoding.getDoubleByteTable() != null) {
				throw new CommandException("Conversion tables are used only with profile TABLE.");
			}
			return new HostTextDecoder(profile, charset, null, null, userDefined,
					code(encoding.getShiftOut(), 0x0E, "shiftOut"), code(encoding.getShiftIn(), 0x0F, "shiftIn"),
					unmappable);
		}
		if (encoding.getSingleByteTable() == null || encoding.getDoubleByteTable() == null) {
			throw new CommandException(
					"encoding.singleByteTable and encoding.doubleByteTable are required for profile" + " TABLE.");
		}
		if (encoding.getShiftOut() == null || encoding.getShiftIn() == null) {
			throw new CommandException("encoding.shiftOut and encoding.shiftIn (hexadecimal) are required for profile"
					+ " TABLE; they differ between code sets.");
		}
		if (encoding.getCharset() != null) {
			throw new CommandException("encoding.charset is used only with profile IBM.");
		}
		Map<Integer, String> single = readTable(file(baseDirectory, encoding.getSingleByteTable()), 1,
				"singleByteTable");
		char[] singleByte = new char[256];
		java.util.Arrays.fill(singleByte, '￿');
		single.forEach((code, value) -> {
			if (value.length() != 1) {
				throw new CommandException("singleByteTable maps X'" + hex(code, 1) + "' to more than one char.");
			}
			singleByte[code] = value.charAt(0);
		});
		return new HostTextDecoder(profile, null, singleByte,
				readTable(file(baseDirectory, encoding.getDoubleByteTable()), 2, "doubleByteTable"), userDefined,
				code(encoding.getShiftOut(), -1, "shiftOut"), code(encoding.getShiftIn(), -1, "shiftIn"), unmappable);
	}

	/** Decodes PIC X data, which may contain shifted double-byte characters. */
	public String text(byte[] bytes, int offset, int length) throws CharacterCodingException {
		StringBuilder builder = new StringBuilder(length);
		int i = offset;
		int end = offset + length;
		while (i < end) {
			int b = bytes[i] & 0xFF;
			if (b == shiftOut) {
				int close = i + 1;
				while (close < end && (bytes[close] & 0xFF) != shiftIn) {
					close++;
				}
				if (close >= end) {
					throw new CharacterCodingException();
				}
				doubleBytes(bytes, i + 1, close - i - 1, builder);
				i = close + 1;
				continue;
			}
			int start = i;
			while (i < end && (bytes[i] & 0xFF) != shiftOut) {
				if ((bytes[i] & 0xFF) == shiftIn) {
					throw new CharacterCodingException();
				}
				i++;
			}
			singleBytes(bytes, start, i - start, builder);
		}
		return builder.toString();
	}

	/** Decodes PIC N or PIC G data: double-byte characters without shift codes. */
	public String graphic(byte[] bytes, int offset, int length) throws CharacterCodingException {
		StringBuilder builder = new StringBuilder(length / 2);
		doubleBytes(bytes, offset, length, builder);
		return builder.toString();
	}

	/**
	 * Decodes single-byte characters only, as used for record type checks and
	 * separate signs.
	 */
	public String singleByteText(byte[] bytes, int offset, int length) throws CharacterCodingException {
		StringBuilder builder = new StringBuilder(length);
		singleBytes(bytes, offset, length, builder);
		return builder.toString();
	}

	private void singleBytes(byte[] bytes, int offset, int length, StringBuilder builder)
			throws CharacterCodingException {
		if (length == 0) {
			return;
		}
		if (profile == Profile.IBM) {
			builder.append(decoder().decode(ByteBuffer.wrap(bytes, offset, length)));
			return;
		}
		for (int i = offset; i < offset + length; i++) {
			char c = singleByte[bytes[i] & 0xFF];
			if (c == '￿') {
				if (unmappable == Unmappable.ERROR) {
					throw new CharacterCodingException();
				}
				c = '�';
			}
			builder.append(c);
		}
	}

	private void doubleBytes(byte[] bytes, int offset, int length, StringBuilder builder)
			throws CharacterCodingException {
		if (length % 2 != 0) {
			throw new CharacterCodingException();
		}
		int runStart = -1;
		for (int i = offset; i < offset + length; i += 2) {
			int code = (bytes[i] & 0xFF) << 8 | bytes[i + 1] & 0xFF;
			String special = userDefined.get(code);
			if (special == null && profile == Profile.TABLE) {
				special = doubleByte.get(code);
				if (special == null) {
					if (unmappable == Unmappable.ERROR) {
						throw new CharacterCodingException();
					}
					special = "�";
				}
			}
			if (special != null) {
				if (runStart >= 0) {
					ibmDoubleBytes(bytes, runStart, i - runStart, builder);
					runStart = -1;
				}
				builder.append(special);
			} else if (runStart < 0) {
				runStart = i;
			}
		}
		if (runStart >= 0) {
			ibmDoubleBytes(bytes, runStart, offset + length - runStart, builder);
		}
	}

	private void ibmDoubleBytes(byte[] bytes, int offset, int length, StringBuilder builder)
			throws CharacterCodingException {
		byte[] shifted = new byte[length + 2];
		shifted[0] = (byte) shiftOut;
		System.arraycopy(bytes, offset, shifted, 1, length);
		shifted[length + 1] = (byte) shiftIn;
		CharBuffer decoded = decoder().decode(ByteBuffer.wrap(shifted));
		builder.append(decoded);
	}

	private CharsetDecoder decoder() {
		CodingErrorAction action = unmappable == Unmappable.ERROR ? CodingErrorAction.REPORT
				: CodingErrorAction.REPLACE;
		return charset.newDecoder().onMalformedInput(action).onUnmappableCharacter(action);
	}

	private static File file(File baseDirectory, String path) {
		File file = new File(path);
		return file.isAbsolute() || baseDirectory == null ? file : new File(baseDirectory, path);
	}

	private static int code(String value, int defaultValue, String name) {
		if (value == null) {
			return defaultValue;
		}
		try {
			int code = Integer.parseInt(value.trim(), 16);
			if (code < 0 || code > 0xFF) {
				throw new NumberFormatException();
			}
			return code;
		} catch (NumberFormatException e) {
			throw new CommandException("encoding." + name + " must be one hexadecimal byte, such as 0E: " + value);
		}
	}

	/**
	 * Reads a conversion table: one entry per line, the host code in hexadecimal
	 * and the Unicode characters as {@code U+XXXX} (several for a sequence),
	 * separated by whitespace; {@code #} starts a comment.
	 */
	static Map<Integer, String> readTable(File file, int bytes, String name) {
		if (!file.isFile()) {
			throw new CommandException(name + " was not found: " + file);
		}
		Map<Integer, String> table = new HashMap<>();
		try {
			int number = 0;
			for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
				number++;
				int comment = line.indexOf('#');
				String content = (comment >= 0 ? line.substring(0, comment) : line).trim();
				if (content.isEmpty()) {
					continue;
				}
				String[] parts = content.split("\\s+");
				if (parts.length < 2 || parts[0].length() != bytes * 2) {
					throw new CommandException(
							name + " line " + number + " must be '<" + bytes * 2 + " hex digits> U+XXXX': " + line);
				}
				int code = HexFormat.fromHexDigits(parts[0]);
				StringBuilder value = new StringBuilder();
				for (int i = 1; i < parts.length; i++) {
					String part = parts[i].toUpperCase(Locale.ROOT);
					if (!part.startsWith("U+")) {
						throw new CommandException(name + " line " + number + " has an invalid character: " + line);
					}
					value.appendCodePoint(HexFormat.fromHexDigits(part.substring(2)));
				}
				if (table.put(code, value.toString()) != null) {
					throw new CommandException(name + " line " + number + " repeats code " + parts[0]);
				}
			}
		} catch (IOException | IllegalArgumentException e) {
			throw new CommandException("Failed to read " + name + ": " + file + ": " + e.getMessage(), e);
		}
		return table;
	}

	private static String hex(int code, int bytes) {
		return HexFormat.of().withUpperCase().toHexDigits(code).substring(8 - bytes * 2);
	}
}
