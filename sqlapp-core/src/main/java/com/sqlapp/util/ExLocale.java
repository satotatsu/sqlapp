/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.util;

import java.util.Locale;

public enum ExLocale {
	ja_JP(){
		@Override
		public Locale getLocale(){
			return Locale.JAPANESE;
		}
		@Override
		public boolean isCharacter(String text){
			if (text==null){
				return false;
			}
			for(int i = 0 ; i < text.length() ; i++) {
				char ch = text.charAt(i);
				Character.UnicodeBlock unicodeBlock = Character.UnicodeBlock.of(ch);
				if (Character.UnicodeBlock.HIRAGANA.equals(unicodeBlock)){
					return true;
				}else if (Character.UnicodeBlock.KATAKANA.equals(unicodeBlock)){
					return true;
				}else if (Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS.equals(unicodeBlock)){
					return true;
				}else if (Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS.equals(unicodeBlock)){
					return true;
				}else if (Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION.equals(unicodeBlock)){
					return true;
				}
			}
			return false;
		}
	},
	;
	
	private Locale locale;
	
	public Locale getLocale(){
		if (locale==null){
			locale=CommonUtils.getLocale(this.toString());
		}
		return locale;
	}
	
	public boolean isCharacter(String text){
		return false;
	}
}
