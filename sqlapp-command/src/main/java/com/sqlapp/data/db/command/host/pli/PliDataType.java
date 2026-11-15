/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.host.pli;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;

/**
 * Resolved data attributes of a PL/I scalar item.
 */
final class PliDataType {

	enum Kind {
		CHAR, BIT, GRAPHIC, WIDECHAR, FIXED_BIN, FIXED_DEC, FLOAT_BIN, FLOAT_DEC, PICTURE
	}

	enum Varying {
		NONE, VARYING, VARYINGZ
	}

	private final Kind kind;
	private final long length;
	private final int precision;
	private final int scale;
	private final Varying varying;
	private final boolean unsigned;
	private final String picture;
	private final boolean numericPicture;

	private PliDataType(Kind kind, long length, int precision, int scale, Varying varying, boolean unsigned,
			String picture, boolean numericPicture) {
		this.kind = kind;
		this.length = length;
		this.precision = precision;
		this.scale = scale;
		this.varying = varying;
		this.unsigned = unsigned;
		this.picture = picture;
		this.numericPicture = numericPicture;
	}

	static PliDataType string(Kind kind, long length, Varying varying) {
		return new PliDataType(kind, length, 0, 0, varying, false, null, false);
	}

	static PliDataType fixedBinary(int precision, boolean unsigned) {
		return new PliDataType(Kind.FIXED_BIN, 0, precision, 0, Varying.NONE, unsigned, null, false);
	}

	static PliDataType fixedDecimal(int precision, int scale) {
		return new PliDataType(Kind.FIXED_DEC, 0, precision, scale, Varying.NONE, false, null, false);
	}

	static PliDataType floating(Kind kind, int precision) {
		return new PliDataType(kind, 0, precision, 0, Varying.NONE, false, null, false);
	}

	static PliDataType picture(String picture, PliPicture parsed) {
		return new PliDataType(Kind.PICTURE, parsed.length(), parsed.precision(), parsed.scale(), Varying.NONE, false,
				picture, parsed.numeric());
	}

	Kind kind() {
		return kind;
	}

	/**
	 * @return the value written to the migration mapping as the PL/I type
	 */
	String pliType() {
		return kind.name();
	}

	boolean isNumeric() {
		return switch (kind) {
		case FIXED_BIN, FIXED_DEC, FLOAT_BIN, FLOAT_DEC -> true;
		case PICTURE -> numericPicture;
		default -> false;
		};
	}

	/**
	 * @return character or bit length for strings, precision for numbers
	 */
	long sourceLength() {
		if (kind == Kind.PICTURE) {
			return numericPicture ? precision : length;
		}
		return isNumeric() ? precision : length;
	}

	Integer sourceScale() {
		return isNumeric() && kind != Kind.FLOAT_BIN && kind != Kind.FLOAT_DEC ? scale : null;
	}

	String declaration() {
		String suffix = switch (varying) {
		case VARYING -> " VARYING";
		case VARYINGZ -> " VARYINGZ";
		default -> "";
		};
		return switch (kind) {
		case CHAR, BIT, GRAPHIC, WIDECHAR -> kind.name() + "(" + length + ")" + suffix;
		case FIXED_BIN -> "FIXED BIN(" + precision + ")" + (unsigned ? " UNSIGNED" : "");
		case FIXED_DEC -> "FIXED DEC(" + precision + "," + scale + ")";
		case FLOAT_BIN -> "FLOAT BIN(" + precision + ")";
		case FLOAT_DEC -> "FLOAT DEC(" + precision + ")";
		case PICTURE -> "PIC '" + picture + "'";
		};
	}

	/**
	 * PL/I defaults: string and numeric character data are unaligned, coded
	 * arithmetic data is aligned.
	 */
	boolean alignedByDefault() {
		return switch (kind) {
		case CHAR, BIT, GRAPHIC, WIDECHAR, PICTURE -> false;
		default -> true;
		};
	}

	long storageBits(boolean aligned) {
		return switch (kind) {
		case CHAR -> characterBits(8);
		case GRAPHIC, WIDECHAR -> characterBits(16);
		case BIT -> aligned ? roundUp(length, 8) : length;
		case FIXED_BIN -> fixedBinaryBits();
		case FIXED_DEC -> 8L * (precision / 2 + 1);
		case FLOAT_BIN, FLOAT_DEC -> isShortFloat() ? 32 : 64;
		case PICTURE -> 8L * length;
		};
	}

	int alignmentBits(boolean aligned) {
		if (!aligned) {
			return kind == Kind.BIT ? 1 : 8;
		}
		return switch (kind) {
		case CHAR, GRAPHIC, WIDECHAR -> varying == Varying.VARYING ? 16 : 8;
		case BIT, FIXED_DEC, PICTURE -> 8;
		case FIXED_BIN -> (int) fixedBinaryBits();
		case FLOAT_BIN, FLOAT_DEC -> isShortFloat() ? 32 : 64;
		};
	}

	Column toColumn(String name, boolean bit1AsBoolean) {
		Column column = new Column(name);
		switch (kind) {
		case CHAR:
			column.setDataType(varying == Varying.NONE ? DataType.CHAR : DataType.VARCHAR).setLength(length);
			break;
		case GRAPHIC, WIDECHAR:
			column.setDataType(varying == Varying.NONE ? DataType.NCHAR : DataType.NVARCHAR).setLength(length);
			break;
		case BIT:
			if (length == 1 && bit1AsBoolean) {
				column.setDataType(DataType.BOOLEAN);
			} else {
				column.setDataType(DataType.BINARY).setLength(roundUp(length, 8) / 8);
			}
			break;
		case FIXED_BIN:
			int valueBits = unsigned ? precision : precision + 1;
			if (valueBits <= 16) {
				column.setDataType(DataType.SMALLINT);
			} else if (valueBits <= 32) {
				column.setDataType(DataType.INT);
			} else if (valueBits <= 64) {
				column.setDataType(DataType.BIGINT);
			} else {
				column.setDataType(DataType.DECIMAL).setLength(20).setScale(0);
			}
			break;
		case FIXED_DEC:
			setDecimal(column, precision, scale);
			break;
		case FLOAT_BIN, FLOAT_DEC:
			column.setDataType(isShortFloat() ? DataType.REAL : DataType.DOUBLE);
			break;
		case PICTURE:
			if (numericPicture) {
				setDecimal(column, precision, scale);
			} else {
				column.setDataType(DataType.CHAR).setLength(length);
			}
			break;
		}
		return column;
	}

	private static void setDecimal(Column column, int precision, int scale) {
		if (scale < 0) {
			column.setDataType(DataType.DECIMAL).setLength(precision - scale).setScale(0);
		} else if (scale > precision) {
			column.setDataType(DataType.DECIMAL).setLength(scale).setScale(scale);
		} else {
			column.setDataType(DataType.DECIMAL).setLength(precision).setScale(scale);
		}
	}

	private long characterBits(int bitsPerCharacter) {
		return switch (varying) {
		case NONE -> bitsPerCharacter * length;
		case VARYING -> 16 + bitsPerCharacter * length;
		case VARYINGZ -> bitsPerCharacter * (length + 1);
		};
	}

	private long fixedBinaryBits() {
		int bits = unsigned ? precision : precision + 1;
		if (bits <= 8) {
			return 8;
		} else if (bits <= 16) {
			return 16;
		} else if (bits <= 32) {
			return 32;
		}
		return 64;
	}

	/**
	 * Short floating point precision limits that hold for both hexadecimal and IEEE
	 * formats.
	 */
	private boolean isShortFloat() {
		return kind == Kind.FLOAT_BIN ? precision <= 21 : precision <= 6;
	}

	static long roundUp(long value, long unit) {
		return (value + unit - 1) / unit * unit;
	}
}
