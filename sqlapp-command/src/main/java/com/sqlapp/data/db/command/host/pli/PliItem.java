/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.host.pli;

import java.util.ArrayList;
import java.util.List;

/**
 * One item of a PL/I level declaration.
 */
final class PliItem {
	int level;
	/** Upper-case name, or {@code null} for an unnamed ({@code *}) item. */
	String name;
	int line;
	Integer occurs;
	int lowerBound = 1;
	PliDataType type;
	/** Explicit ALIGNED/UNALIGNED attribute, or {@code null} when inherited. */
	Boolean aligned;
	String remarks;
	/** Reason why the item cannot be imported, or {@code null}. */
	String unsupported;
	PliItem parent;
	final List<PliItem> children = new ArrayList<>();
	/** Structural errors found while parsing a level-1 declaration. */
	final List<String> errors = new ArrayList<>();

	/** Offset in bits relative to the record or array element, when resolved. */
	Long offsetBits;
	/** Total size in bits, when resolved. */
	Long sizeBits;
	/** Size of one array element in bits, when resolved. */
	Long elementBits;
	/** Whether storage offsets were resolved for the level-1 declaration. */
	boolean layoutResolved;

	boolean isArray() {
		return occurs != null;
	}

	boolean isGroup() {
		return type == null;
	}

	boolean isFiller() {
		return name == null;
	}

	String displayName() {
		return name == null ? "*" : name;
	}

	String path() {
		return parent == null ? displayName() : parent.path() + "." + displayName();
	}

	PliItem root() {
		PliItem current = this;
		while (current.parent != null) {
			current = current.parent;
		}
		return current;
	}

	boolean effectiveAligned() {
		for (PliItem current = this; current != null; current = current.parent) {
			if (current.aligned != null) {
				return current.aligned;
			}
		}
		return type != null && type.alignedByDefault();
	}

	String dimension() {
		if (occurs == null) {
			return null;
		}
		return lowerBound == 1 ? "(" + occurs + ")" : "(" + lowerBound + ":" + (lowerBound + occurs - 1) + ")";
	}

	String declaration() {
		if (type == null) {
			return occurs == null ? null : displayName() + dimension();
		}
		return occurs == null ? type.declaration() : type.declaration() + " DIM" + dimension();
	}

	String location() {
		return path() + " (line " + line + ")";
	}
}
