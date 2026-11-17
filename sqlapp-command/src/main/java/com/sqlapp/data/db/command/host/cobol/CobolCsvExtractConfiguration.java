/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.host.cobol;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Configuration of {@link CobolCsvExtractCommand}. */
@Getter
@Setter
public class CobolCsvExtractConfiguration {

	/** Record format of the data set as transferred in binary. */
	public enum RecordFormat {
		/** RECFM=F/FB: every record has {@code recordLength} bytes. */
		FIXED,
		/** RECFM=V: each record starts with a 4-byte record descriptor word (RDW). */
		VARIABLE,
		/**
		 * RECFM=VB: blocks start with a block descriptor word (BDW) and contain RDW
		 * records.
		 */
		VARIABLE_BLOCKED,
		/**
		 * Variable records whose RDW was removed in transfer: the length is computed
		 * from the record layout and its OCCURS DEPENDING ON counter.
		 */
		COMPUTED
	}

	/** Handling of numeric fields that hold no valid number. */
	public enum InvalidValue {
		ERROR, NULL
	}

	/** Handling of numeric fields that are all spaces or all LOW-VALUES. */
	public enum BlankValue {
		ERROR, NULL, ZERO
	}

	/** Floating-point (COMP-1, COMP-2) format. */
	public enum FloatingPoint {
		/** IBM hexadecimal floating point, the mainframe default. */
		HEXADECIMAL,
		/** IEEE 754 binary floating point (FLOAT-FORMAT(IEEE) or FLOAT(IEEE)). */
		IEEE
	}

	/** Handling of records whose type is not identified. */
	public enum UnknownRecord {
		ERROR, SKIP
	}

	private int formatVersion = 1;

	private RecordFormat recordFormat = RecordFormat.FIXED;

	/**
	 * Physical record length of FIXED; defaults to the record length of a single
	 * record type.
	 */
	private Integer recordLength;

	private Encoding encoding = new Encoding();

	private FloatingPoint floatingPoint = FloatingPoint.HEXADECIMAL;

	/** Removes trailing spaces of text fields, as CHAR columns do not keep them. */
	private boolean trimTrailingSpaces = true;

	private InvalidValue invalidNumeric = InvalidValue.ERROR;

	private BlankValue blankNumeric = BlankValue.ERROR;

	/** Text fields that are all LOW-VALUES (X'00'). */
	private InvalidValue lowValueText = InvalidValue.ERROR;

	/** Record types of a data set with several record layouts, checked in order. */
	private List<RecordType> recordTypes = new ArrayList<>();

	/**
	 * Class implementing {@link CobolRecordTypeResolver}, used when recordTypes do
	 * not identify a record.
	 */
	private String recordTypeResolver;

	private UnknownRecord unknownRecord = UnknownRecord.ERROR;

	/**
	 * Checks of control (trailer) records against the records read since the
	 * previous record of the same trailer, or since the start of the data set.
	 */
	private List<TrailerCheck> trailerChecks = new ArrayList<>();

	@Getter
	@Setter
	public static class Encoding {
		private HostTextDecoder.Profile profile = HostTextDecoder.Profile.IBM;
		/** JDK charset of profile IBM, such as x-IBM939, x-IBM930 or IBM037. */
		private String charset;
		/** Single-byte conversion table of profile TABLE. */
		private String singleByteTable;
		/** Double-byte conversion table of profile TABLE. */
		private String doubleByteTable;
		/**
		 * Shift-out (start of double-byte) code in hexadecimal; IBM default 0E,
		 * required for TABLE.
		 */
		private String shiftOut;
		/**
		 * Shift-in (end of double-byte) code in hexadecimal; IBM default 0F, required
		 * for TABLE.
		 */
		private String shiftIn;
		/**
		 * Table of user-defined double-byte characters (gaiji), checked before the code
		 * set.
		 */
		private String userDefinedCharacters;
		private HostTextDecoder.Unmappable unmappable = HostTextDecoder.Unmappable.ERROR;
	}

	@Getter
	@Setter
	public static class RecordType {
		/** COBOL record name (level 01). */
		private String record;
		/** Byte range that identifies the record. */
		private When when;
		/**
		 * MVEL expression returning true for this record type; variables:
		 * {@code record} ({@link CobolRecordView}) and {@code recordNumber}.
		 */
		private String expression;
		/** Recognize the record but write nothing, as for trailer records. */
		private boolean skip;
	}

	@Getter
	@Setter
	public static class TrailerCheck {
		/** Control record imported with control: true. */
		private String record;
		/**
		 * Trailer item holding the number of records, such as TRAILER-REC.REC-COUNT.
		 */
		private String count;
		/**
		 * Record types counted by {@code count}; default: every record that is not a
		 * control record.
		 */
		private List<String> countOf = new ArrayList<>();
		private List<Sum> sums = new ArrayList<>();
		/** The data set must end with this trailer after its last counted record. */
		private boolean required = true;
	}

	@Getter
	@Setter
	public static class Sum {
		/** Trailer item holding the total, such as TRAILER-REC.TOTAL-QTY. */
		private String field;
		/** Item of a counted record that is summed, such as DETAIL-REC.QTY. */
		private String of;
	}

	@Getter
	@Setter
	public static class When {
		private int offset;
		private int length = 1;
		/** Expected text, decoded as single-byte characters. */
		private String text;
		/** Expected bytes in hexadecimal. */
		private String hex;
	}
}
