/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.host.cobol;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.CharacterCodingException;
import java.util.HexFormat;
import java.util.Map;

import com.sqlapp.data.db.command.host.cobol.CobolCsvExtractConfiguration.BlankValue;
import com.sqlapp.data.db.command.host.cobol.CobolCsvExtractConfiguration.FloatingPoint;
import com.sqlapp.data.db.command.host.cobol.CobolCsvExtractConfiguration.InvalidValue;

/**
 * Decodes one field of a host record from the storage layout recorded by
 * {@link CobolSchemaImportCommand}. Text becomes {@link String}, numbers become
 * {@link BigDecimal} (or {@link Double} for COMP-1/COMP-2), and fields mapped
 * to NULL by the configuration become {@code null}.
 */
final class CobolFieldDecoder {

	/**
	 * A value that cannot be decoded; the message names the problem without the
	 * record position.
	 */
	static final class DecodeException extends Exception {
		private static final long serialVersionUID = 1L;

		DecodeException(String message) {
			super(message);
		}
	}

	/** Storage layout of a field, relative to the start of its table element. */
	record Layout(int offset, int length, String usage, String category, int digits, int scale, boolean signed,
			boolean signLeading, boolean signSeparate) {

		static Layout of(Map<?, ?> storage, Map<?, ?> conversion) {
			return new Layout(integer(storage, "offset"), integer(storage, "length"), string(storage.get("usage")),
					string(conversion.get("category")), integerOr(storage, "digits", 0), integerOr(storage, "scale", 0),
					Boolean.TRUE.equals(storage.get("signed")), "LEADING".equals(storage.get("signPosition")),
					Boolean.TRUE.equals(storage.get("signSeparate")));
		}

		private static int integer(Map<?, ?> map, String key) {
			if (!(map.get(key) instanceof Number number)) {
				throw new IllegalArgumentException("storage." + key + " is missing");
			}
			return number.intValue();
		}

		private static int integerOr(Map<?, ?> map, String key, int defaultValue) {
			return map.get(key) instanceof Number number ? number.intValue() : defaultValue;
		}

		private static String string(Object value) {
			return value == null ? null : value.toString();
		}
	}

	private static final byte EBCDIC_SPACE = 0x40;
	private static final byte EBCDIC_PLUS = 0x4E;
	private static final byte EBCDIC_MINUS = 0x60;

	private final HostTextDecoder text;
	private final CobolCsvExtractConfiguration configuration;

	CobolFieldDecoder(HostTextDecoder text, CobolCsvExtractConfiguration configuration) {
		this.text = text;
		this.configuration = configuration;
	}

	Object decode(Layout layout, byte[] bytes, int offset) throws DecodeException {
		String category = layout.category() == null ? "ALPHANUMERIC" : layout.category();
		try {
			return switch (category) {
			case "ALPHANUMERIC", "ALPHANUMERIC_EDITED", "NUMERIC_EDITED" -> text(layout, bytes, offset, false);
			case "NATIONAL" -> text(layout, bytes, offset, true);
			case "NUMERIC" -> numeric(layout, bytes, offset);
			case "FLOATING_POINT" -> floating(layout, bytes, offset);
			case "INDEX" -> new BigDecimal(new BigInteger(1, slice(bytes, offset, layout.length())));
			default -> throw new DecodeException("unsupported category " + category);
			};
		} catch (CharacterCodingException e) {
			throw new DecodeException("bytes X'" + hex(bytes, offset, layout.length()) + "' are not valid text in the"
					+ " configured encoding");
		}
	}

	private Object text(Layout layout, byte[] bytes, int offset, boolean graphic)
			throws CharacterCodingException, DecodeException {
		if (all(bytes, offset, layout.length(), (byte) 0) && layout.length() > 0) {
			if (configuration.getLowValueText() == InvalidValue.NULL) {
				return null;
			}
			throw new DecodeException("text is LOW-VALUES; set lowValueText: NULL to load it as NULL");
		}
		String value = graphic ? text.graphic(bytes, offset, layout.length())
				: text.text(bytes, offset, layout.length());
		if (configuration.isTrimTrailingSpaces()) {
			int end = value.length();
			while (end > 0 && (value.charAt(end - 1) == ' ' || value.charAt(end - 1) == '　')) {
				end--;
			}
			value = value.substring(0, end);
		}
		return value;
	}

	private Object numeric(Layout layout, byte[] bytes, int offset) throws DecodeException, CharacterCodingException {
		int length = layout.length();
		boolean blank = all(bytes, offset, length, EBCDIC_SPACE) || all(bytes, offset, length, (byte) 0);
		if (blank && !"COMP".equals(layout.usage()) && !"COMP-5".equals(layout.usage())) {
			return switch (configuration.getBlankNumeric()) {
			case NULL -> null;
			case ZERO -> BigDecimal.ZERO.setScale(Math.max(0, layout.scale()));
			case ERROR -> throw new DecodeException("numeric field is blank (X'" + hex(bytes, offset, length)
					+ "'); set blankNumeric to NULL or ZERO to accept it");
			};
		}
		try {
			BigInteger unscaled = switch (layout.usage()) {
			case "DISPLAY" -> zoned(layout, bytes, offset);
			case "COMP-3" -> packed(bytes, offset, length);
			case "COMP", "COMP-5" -> layout.signed() ? new BigInteger(slice(bytes, offset, length))
					: new BigInteger(1, slice(bytes, offset, length));
			default -> throw new DecodeException("unsupported numeric USAGE " + layout.usage());
			};
			return new BigDecimal(unscaled, layout.scale());
		} catch (InvalidNumber e) {
			if (configuration.getInvalidNumeric() == InvalidValue.NULL) {
				return null;
			}
			throw new DecodeException(e.getMessage() + " (X'" + hex(bytes, offset, length)
					+ "'); set invalidNumeric: NULL to load it as NULL");
		}
	}

	private static final class InvalidNumber extends Exception {
		private static final long serialVersionUID = 1L;

		InvalidNumber(String message) {
			super(message);
		}
	}

	private static BigInteger zoned(Layout layout, byte[] bytes, int offset) throws InvalidNumber {
		int start = offset;
		int end = offset + layout.length();
		boolean negative = false;
		if (layout.signSeparate()) {
			int signIndex = layout.signLeading() ? start : end - 1;
			byte sign = bytes[signIndex];
			if (sign == EBCDIC_MINUS) {
				negative = true;
			} else if (sign != EBCDIC_PLUS) {
				throw new InvalidNumber("separate sign is neither '+' nor '-'");
			}
			if (layout.signLeading()) {
				start++;
			} else {
				end--;
			}
		}
		long value = 0;
		BigInteger big = null;
		int signIndex = layout.signed() && !layout.signSeparate() ? (layout.signLeading() ? start : end - 1) : -1;
		for (int i = start; i < end; i++) {
			int b = bytes[i] & 0xFF;
			int zone = b >> 4;
			int digit = b & 0x0F;
			if (digit > 9) {
				throw new InvalidNumber("zoned decimal digit is not 0-9");
			}
			if (i == signIndex || !layout.signed() && i == end - 1) {
				if (zone == 0xD || zone == 0xB) {
					if (!layout.signed()) {
						throw new InvalidNumber("unsigned zoned decimal has a negative sign");
					}
					negative = true;
				} else if (zone != 0xF && zone != 0xC && zone != 0xA && zone != 0xE) {
					throw new InvalidNumber("zoned decimal sign zone is invalid");
				}
			} else if (zone != 0xF) {
				throw new InvalidNumber("zoned decimal zone is not X'F'");
			}
			if (big == null && value > (Long.MAX_VALUE - 9) / 10) {
				big = BigInteger.valueOf(value);
			}
			if (big != null) {
				big = big.multiply(BigInteger.TEN).add(BigInteger.valueOf(digit));
			} else {
				value = value * 10 + digit;
			}
		}
		BigInteger result = big != null ? big : BigInteger.valueOf(value);
		return negative ? result.negate() : result;
	}

	private static BigInteger packed(byte[] bytes, int offset, int length) throws InvalidNumber {
		BigInteger result = BigInteger.ZERO;
		long value = 0;
		boolean useBig = length > 9;
		for (int i = 0; i < length; i++) {
			int b = bytes[offset + i] & 0xFF;
			int high = b >> 4;
			int low = b & 0x0F;
			if (high > 9) {
				throw new InvalidNumber("packed decimal digit is not 0-9");
			}
			if (useBig) {
				result = result.multiply(BigInteger.TEN).add(BigInteger.valueOf(high));
			} else {
				value = value * 10 + high;
			}
			if (i < length - 1) {
				if (low > 9) {
					throw new InvalidNumber("packed decimal digit is not 0-9");
				}
				if (useBig) {
					result = result.multiply(BigInteger.TEN).add(BigInteger.valueOf(low));
				} else {
					value = value * 10 + low;
				}
			} else {
				boolean negative;
				if (low == 0xD || low == 0xB) {
					negative = true;
				} else if (low == 0xC || low == 0xF || low == 0xA || low == 0xE) {
					negative = false;
				} else {
					throw new InvalidNumber("packed decimal sign is invalid");
				}
				BigInteger magnitude = useBig ? result : BigInteger.valueOf(value);
				return negative ? magnitude.negate() : magnitude;
			}
		}
		throw new InvalidNumber("packed decimal is empty");
	}

	private Object floating(Layout layout, byte[] bytes, int offset) {
		long bits = 0;
		for (int i = 0; i < layout.length(); i++) {
			bits = bits << 8 | bytes[offset + i] & 0xFF;
		}
		if (configuration.getFloatingPoint() == FloatingPoint.IEEE) {
			return layout.length() == 4 ? (double) Float.intBitsToFloat((int) bits) : Double.longBitsToDouble(bits);
		}
		return hexadecimalFloat(bits, layout.length());
	}

	/**
	 * IBM hexadecimal floating point: sign, 7-bit excess-64 exponent of 16,
	 * fraction.
	 */
	static double hexadecimalFloat(long bits, int length) {
		int fractionBits = length * 8 - 8;
		boolean negative = (bits >>> (length * 8 - 1) & 1) == 1;
		int exponent = (int) (bits >>> fractionBits & 0x7F) - 64;
		long fraction = bits & ((1L << fractionBits) - 1);
		double value = fraction / Math.pow(2, fractionBits) * Math.pow(16, exponent);
		return negative ? -value : value;
	}

	private static boolean all(byte[] bytes, int offset, int length, byte value) {
		for (int i = offset; i < offset + length; i++) {
			if (bytes[i] != value) {
				return false;
			}
		}
		return true;
	}

	private static byte[] slice(byte[] bytes, int offset, int length) {
		byte[] result = new byte[length];
		System.arraycopy(bytes, offset, result, 0, length);
		return result;
	}

	static String hex(byte[] bytes, int offset, int length) {
		return HexFormat.of().withUpperCase().formatHex(bytes, offset, offset + length);
	}
}
