/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.host.cobol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One data description entry of a COBOL copybook with its computed storage
 * layout. Offsets are relative to the start of the enclosing record; for an
 * item inside an OCCURS table they are the offsets of the first occurrence.
 */
public final class CobolItem {

	/** Category of an item, derived from its PICTURE and USAGE. */
	public enum Category {
		GROUP, ALPHANUMERIC, NATIONAL, NUMERIC, NUMERIC_EDITED, ALPHANUMERIC_EDITED, FLOATING_POINT, INDEX
	}

	/** Storage format of an elementary item. */
	public enum Usage {
		DISPLAY, BINARY, PACKED_DECIMAL, NATIVE_BINARY, FLOAT_SHORT, FLOAT_LONG, NATIONAL, DISPLAY_1, INDEX
	}

	/** A level-88 condition name and its values as written in the copybook. */
	public record Condition(String name, List<String> values) {
		public Condition {
			values = List.copyOf(values);
		}
	}

	int level;
	String name;
	CobolItem parent;
	final List<CobolItem> children = new ArrayList<>();
	String picture;
	Usage usage;
	boolean usageExplicit;
	Category category = Category.GROUP;
	int digits;
	/**
	 * Decimal places; negative when the PICTURE ends with P (scaling to the left).
	 */
	int scale;
	boolean signed;
	boolean signLeading;
	boolean signSeparate;
	boolean signExplicit;
	boolean signInherited;
	Integer occursMinimum;
	Integer occursMaximum;
	String dependingOn;
	CobolItem dependingOnItem;
	String redefines;
	CobolItem redefinesItem;
	final List<CobolItem> redefinedBy = new ArrayList<>();
	final List<Condition> conditions = new ArrayList<>();
	String remarks;
	int offset;
	int length;
	int line;

	CobolItem() {
	}

	public int getLevel() {
		return level;
	}

	/** Name as written, or {@code FILLER} for an unnamed or FILLER item. */
	public String getName() {
		return name == null ? "FILLER" : name;
	}

	public boolean isFiller() {
		return name == null;
	}

	public CobolItem getParent() {
		return parent;
	}

	public List<CobolItem> getChildren() {
		return Collections.unmodifiableList(children);
	}

	public boolean isGroup() {
		return category == Category.GROUP;
	}

	public String getPicture() {
		return picture;
	}

	public Usage getUsage() {
		return usage;
	}

	public Category getCategory() {
		return category;
	}

	/**
	 * Number of digit positions (9) of a numeric item, or characters of other
	 * items.
	 */
	public int getDigits() {
		return digits;
	}

	public int getScale() {
		return scale;
	}

	public boolean isSigned() {
		return signed;
	}

	public boolean isSignLeading() {
		return signLeading;
	}

	public boolean isSignSeparate() {
		return signSeparate;
	}

	/** Maximum occurrences, or {@code null} without OCCURS. */
	public Integer getOccursMaximum() {
		return occursMaximum;
	}

	public Integer getOccursMinimum() {
		return occursMinimum;
	}

	/** The item named by OCCURS DEPENDING ON, or {@code null}. */
	public CobolItem getDependingOn() {
		return dependingOnItem;
	}

	/** The item this item redefines, or {@code null}. */
	public CobolItem getRedefines() {
		return redefinesItem;
	}

	public List<CobolItem> getRedefinedBy() {
		return Collections.unmodifiableList(redefinedBy);
	}

	public List<Condition> getConditions() {
		return Collections.unmodifiableList(conditions);
	}

	public String getRemarks() {
		return remarks;
	}

	/** Offset in bytes of the first occurrence from the start of the record. */
	public int getOffset() {
		return offset;
	}

	/** Length in bytes of one occurrence. */
	public int getLength() {
		return length;
	}

	/** Length in bytes of all occurrences (maximum for OCCURS DEPENDING ON). */
	public int getTotalLength() {
		return occursMaximum == null ? length : length * occursMaximum;
	}

	/** Source line number of the entry, starting at 1. */
	public int getLine() {
		return line;
	}

	/** Names from the record down to this item, joined with '.'. */
	public String getPath() {
		return parent == null ? getName() : parent.getPath() + "." + getName();
	}

	/** The item with this name in the subtree, depth first, or {@code null}. */
	CobolItem find(String target) {
		if (target.equalsIgnoreCase(name)) {
			return this;
		}
		for (CobolItem child : children) {
			CobolItem found = child.find(target);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	/**
	 * Declaration text recorded in the migration mapping, such as
	 * {@code PIC S9(7)V99 COMP-3}.
	 */
	public String getDeclaration() {
		StringBuilder builder = new StringBuilder();
		if (picture != null) {
			builder.append("PIC ").append(picture);
		}
		if (usage != null && (usage != Usage.DISPLAY || usageExplicit)) {
			if (!builder.isEmpty()) {
				builder.append(' ');
			}
			builder.append(usageText(usage));
		}
		if (signExplicit) {
			builder.append(" SIGN ").append(signLeading ? "LEADING" : "TRAILING");
			if (signSeparate) {
				builder.append(" SEPARATE");
			}
		}
		if (occursMaximum != null) {
			builder.append(builder.isEmpty() ? "" : " ").append("OCCURS ");
			if (dependingOnItem != null) {
				builder.append(occursMinimum).append(" TO ");
			}
			builder.append(occursMaximum);
			if (dependingOnItem != null) {
				builder.append(" DEPENDING ON ").append(dependingOnItem.getName());
			}
		}
		return builder.isEmpty() ? null : builder.toString();
	}

	static String usageText(Usage usage) {
		return switch (usage) {
		case DISPLAY -> "DISPLAY";
		case BINARY -> "COMP";
		case PACKED_DECIMAL -> "COMP-3";
		case NATIVE_BINARY -> "COMP-5";
		case FLOAT_SHORT -> "COMP-1";
		case FLOAT_LONG -> "COMP-2";
		case NATIONAL -> "NATIONAL";
		case DISPLAY_1 -> "DISPLAY-1";
		case INDEX -> "INDEX";
		};
	}

	@Override
	public String toString() {
		return level + " " + getPath() + " offset=" + offset + " length=" + length;
	}
}
