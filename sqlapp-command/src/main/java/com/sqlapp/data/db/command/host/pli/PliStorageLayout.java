/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.host.pli;

/**
 * Calculates storage offsets of a PL/I level-1 structure.
 *
 * <p>
 * Offsets are calculated only when every item is byte or bit aligned, which is
 * the case for unaligned structures and for structures that contain only
 * character, bit, numeric character and FIXED DECIMAL data. Items that require
 * halfword or larger alignment are mapped by the compiler's structure mapping
 * algorithm, which can shift the whole structure; for those structures no
 * offsets are produced and a warning recommends the compiler AGGREGATE listing.
 * </p>
 *
 * <p>
 * Offsets are relative to the start of the level-1 record, or to the start of
 * one element for items inside an array.
 * </p>
 */
final class PliStorageLayout {

	private PliStorageLayout() {
	}

	/**
	 * @return {@code null} when offsets were resolved, otherwise the reason
	 */
	static String compute(PliItem root) {
		String reason = unresolvedReason(root);
		if (reason != null) {
			root.layoutResolved = false;
			return reason;
		}
		root.offsetBits = 0L;
		long end = layoutMembers(root, 0);
		root.sizeBits = PliDataType.roundUp(end, alignment(root));
		root.layoutResolved = true;
		return null;
	}

	private static String unresolvedReason(PliItem item) {
		if (item.unsupported != null) {
			return "item " + item.location() + " has unsupported attributes";
		}
		if (item.type != null) {
			int alignment = item.type.alignmentBits(item.effectiveAligned());
			if (alignment > 8) {
				return "item " + item.location() + " (" + item.type.declaration() + ") requires " + alignment / 8
						+ "-byte alignment";
			}
			return null;
		}
		for (PliItem child : item.children) {
			String reason = unresolvedReason(child);
			if (reason != null) {
				return reason;
			}
		}
		return null;
	}

	private static long layoutMembers(PliItem group, long start) {
		long cursor = start;
		for (PliItem child : group.children) {
			cursor = layout(child, cursor);
		}
		return cursor;
	}

	private static long layout(PliItem item, long cursor) {
		long start = PliDataType.roundUp(cursor, alignment(item));
		item.offsetBits = start;
		long element;
		if (item.type != null) {
			element = item.type.storageBits(item.effectiveAligned());
		} else {
			long end = layoutMembers(item, item.isArray() ? 0 : start);
			element = item.isArray() ? PliDataType.roundUp(end, alignment(item)) : end - start;
		}
		if (item.isArray()) {
			item.elementBits = element;
			item.sizeBits = element * item.occurs;
		} else {
			item.sizeBits = element;
		}
		return start + item.sizeBits;
	}

	private static int alignment(PliItem item) {
		if (item.type != null) {
			return item.type.alignmentBits(item.effectiveAligned());
		}
		int result = 1;
		for (PliItem child : item.children) {
			result = Math.max(result, alignment(child));
		}
		return result;
	}
}
