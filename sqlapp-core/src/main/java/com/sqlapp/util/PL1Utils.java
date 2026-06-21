/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.util;

import java.math.BigDecimal;

public class PL1Utils {

	/**
	 * FIXED DEC（X）
	 * 
	 * @param bytes
	 * @return
	 */
	public static long unpackDecimal(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < bytes.length; i++) {
			int b = bytes[i] & 0xFF;
			int high = (b >> 4) & 0x0F;
			int low = b & 0x0F;
			if (i == bytes.length - 1) {
				sb.append(high); // 最後は上位ニブルが数字
				// 下位ニブルは符号 (C/D/Fなど)
			} else {
				sb.append(high).append(low);
			}
		}
		return Long.parseLong(sb.toString());
	}

	/**
	 * FIXED DEC（X,Y）
	 * 
	 * @param bytes
	 * @return
	 */
	public static BigDecimal unpackPackedDecimal(byte[] packed, int precision, int scale) {
		StringBuilder digits = new StringBuilder(precision);
		boolean negative = false;
		for (int i = 0; i < packed.length; i++) {
			int b = packed[i] & 0xFF;
			int high = (b >>> 4) & 0x0F;
			int low = b & 0x0F;
			if (i == packed.length - 1) {
				// 最後の1ニブルは符号
				digits.append(high);
				switch (low) {
				case 0x0D: // 負
				case 0x0B:
					negative = true;
					break;
				case 0x0C: // 正
				case 0x0F:
				case 0x0A:
				case 0x0E:
					break;
				default:
					throw new IllegalArgumentException("Invalid packed decimal sign: " + low);
				}
			} else {
				digits.append(high);
				digits.append(low);
			}
		}
		// precision分だけ利用（余分な桁があれば切り捨て）
		if (digits.length() > precision) {
			digits.setLength(precision);
		}
		BigDecimal value = new BigDecimal(digits.toString()).movePointLeft(scale);
		return negative ? value.negate() : value;
	}
}
