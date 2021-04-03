/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util;

import java.nio.charset.Charset;

public enum PaddingType {
	LEFT(){
		@Override
		public String addPadding(final String input, final int length, final String padding) {
			if (length<=0) {
				return "";
			}
			final StringBuilder builder=new StringBuilder(length);
			final int remain = length-input.length();
			while(builder.length() < remain) {
				builder.append(padding);
			}
			builder.append(input);
			return builder.substring(builder.length()-length, builder.length());
		}

		@Override
		public String addPaddingCodePoint(final String input, final int length, final String padding) {
			if (length<=0) {
				return "";
			}
			final StringBuilder builder=new StringBuilder(length);
			final int remain = length-input.codePointCount(0, input.length());
			final int paddingCodepointLen=padding.codePointCount(0, padding.length());
			int len=0;
			while(len < remain) {
				builder.append(padding);
				len=len+paddingCodepointLen;
			}
			builder.append(input);
			return builder.substring(builder.length()-length, builder.length());
		}

		@Override
		public String trimPadding(final String input, final String padding) {
			if (input==null||input.length()==0) {
				return "";
			}
			int i=0;
			boolean match=true;
			for (i=0;i<input.length();i=i+padding.length()) {
				for (int j=0;j<padding.length();j++) {
					if (input.charAt(i+j)!=padding.charAt(j)) {
						match=false;
						break;
					}
				}
				if (!match) {
					break;
				}
			}
			return input.substring(i);
		}

		@Override
		public byte[] addPadding(final byte[] input, final int length, final byte[] padding) {
			if (length<=0) {
				return EMPTY_BYTES;
			}
			final byte[] result=new byte[length];
			final int len=input.length>length?length:input.length;
			System.arraycopy(input, 0, result, length-len, len);
			for(int i=0;i<(length-len);i=i+padding.length) {
				for(int j=0;j<padding.length;j++) {
					result[i+j]=padding[j];
				}
			}
			return result;
		}

		@Override
		public byte[] trimPadding(final byte[] input, final byte[] padding) {
			if (input==null||input.length==0) {
				return EMPTY_BYTES;
			}
			if (padding==null||padding.length==0) {
				return input;
			}
			final int len=input.length;
			boolean match=true;
			int currentPosition=0;
			for(int i=0;i<=len-padding.length;i=i+padding.length) {
				for(int j=0;j<padding.length;j++) {
					if (input[i+j]!=padding[j]) {
						match=false;
						break;
					}
				}
				if (!match) {
					break;
				}
				currentPosition=i+padding.length;
			}
			final byte[] result=new byte[len-currentPosition];
			System.arraycopy(input, currentPosition, result, 0, len-currentPosition);
			return result;
		}

		@Override
		public String toString(final byte[] input, final int offset, final int length, final byte[] padding, final Charset charset) {
			if (input==null||input.length==0) {
				return "";
			}
			if (offset>=input.length) {
				return "";
			}
			final int len;
			if ((offset+length)>input.length) {
				len=input.length;
			} else {
				len=offset+length;
			}
			boolean match=true;
			int currentPosition=offset;
			for(int i=offset;i<=len-padding.length;i=i+padding.length) {
				for(int j=0;j<padding.length;j++) {
					if (input[i+j]!=padding[j]) {
						match=false;
						break;
					}
				}
				if (!match) {
					break;
				}
				currentPosition=i+padding.length;
			}
			return new String(input, currentPosition, len-currentPosition, charset);
		}
		
		@Override
		public boolean isPrefix() {
			return true;
		}
	},
	RIGHT(){
		@Override
		public String addPadding(final String input, final int length, final String padding) {
			if (length<=0) {
				return "";
			}
			final StringBuilder builder=new StringBuilder(length);
			builder.append(input);
			while(builder.length() < length) {
				builder.append(padding);
			}
			return builder.substring(0, length);
		}

		@Override
		public String addPaddingCodePoint(final String input, final int length, final String padding) {
			if (length<=0) {
				return "";
			}
			final StringBuilder builder=new StringBuilder(length);
			builder.append(input);
			final int paddingCodepointLen=padding.codePointCount(0, padding.length());
			int len=input.codePointCount(0, input.length());
			while(len < length) {
				builder.append(padding);
				len=len+paddingCodepointLen;
			}
			if (builder.codePointCount(0, builder.length())==length) {
				return builder.toString();
			}
			return StringUtils.substringCodePoint(builder.toString(), 0, length);
		}
		
		@Override
		public String trimPadding(final String input, final String padding) {
			if (input==null||input.length()==0) {
				return "";
			}
			final int len=input.length();
			boolean match=true;
			int currentPosition=input.length();
			for(int i=len-padding.length();i>=0;i=i-padding.length()) {
				for(int j=0;j<padding.length();j++) {
					if (input.charAt(i+j)!=padding.charAt(j)) {
						match=false;
						break;
					}
				}
				if (!match) {
					break;
				}
				currentPosition=i;
			}
			return input.substring(0,currentPosition);
		}

		@Override
		public byte[] addPadding(final byte[] input, final int length, final byte[] padding) {
			if (length<=0) {
				return EMPTY_BYTES;
			}
			final byte[] result=new byte[length];
			final int len=input.length>length?length:input.length;
			System.arraycopy(input, 0, result, 0, len);
			for(int i=len;i<length;i=i+padding.length) {
				for(int j=0;j<padding.length;j++) {
					if ((i+j)>=length) {
						break;
					}
					result[i+j]=padding[j];
				}
			}
			return result;
		}

		@Override
		public byte[] trimPadding(final byte[] input, final byte[] padding) {
			if (input==null||input.length==0) {
				return EMPTY_BYTES;
			}
			if (padding==null||padding.length==0) {
				return input;
			}
			final int len=input.length;
			boolean match=true;
			int currentPosition=input.length;
			for(int i=len-padding.length;i>=0;i=i-padding.length) {
				for(int j=0;j<padding.length;j++) {
					if (input[i+j]!=padding[j]) {
						match=false;
						break;
					}
				}
				if (!match) {
					break;
				}
				currentPosition=i;
			}
			final byte[] result=new byte[currentPosition];
			System.arraycopy(input, 0, result, 0, currentPosition);
			return result;
		}

		@Override
		public String toString(final byte[] input, final int offset, final int length, final byte[] padding, final Charset charset) {
			if (input==null||input.length==0) {
				return "";
			}
			if (offset>=input.length) {
				return "";
			}
			final int len;
			if ((offset+length)>input.length) {
				len=input.length;
			} else {
				len=offset+length;
			}
			boolean match=true;
			int currentPosition=len;
			for(int i=len-padding.length;i>=offset;i=i-padding.length) {
				for(int j=0;j<padding.length;j++) {
					if (input[i+j]!=padding[j]) {
						match=false;
						break;
					}
				}
				if (!match) {
					break;
				}
				currentPosition=i;
			}
			return new String(input, offset, currentPosition-offset, charset);
		}
		
		@Override
		public boolean isSuffix() {
			return true;
		}
	},
	NO_PADDING(){
		@Override
		public boolean isNoPadding() {
			return true;
		}
	};
	
	private static final byte[] EMPTY_BYTES=new byte[0];

	/**
	 * 入力されたバイトを指定の長さになるまでpaddingします。
	 * @param input
	 * @param length
	 * @param padding
	 * @return　paddingしたバイト
	 */
	public byte[] addPadding(final byte[] input, final int length, final byte[] padding) {
		if (length<=0) {
			return EMPTY_BYTES;
		}
		return input;
	}

	/**
	 * 入力されたバイトからpaddingを除去します。
	 * @param input
	 * @param padding
	 * @return padding除去したバイト
	 */
	public byte[] trimPadding(final byte[] input, final byte[] padding) {
		if (input==null||input.length==0) {
			return EMPTY_BYTES;
		}
		return input;
	}

	/**
	 * 入力されたバイトからpaddingを除去します。
	 * @param input
	 * @param padding
	 * @return padding除去したバイト
	 */
	public String toString(final byte[] input, final int offset, final int length, final byte[] padding, final Charset charset) {
		if (input==null||input.length==0) {
			return "";
		}
		if (offset>=input.length) {
			return "";
		}
		if ((offset+length)>input.length) {
			return new String(input, offset, input.length-offset, charset);
		}
		return new String(input, offset, length, charset);
	}

	/**
	 * 入力されたバイトを指定の長さになるまでpaddingします。
	 * @param input
	 * @param length
	 * @param padding
	 * @return　paddingしたバイト
	 */
	public String addPadding(final String input, final int length, final String padding) {
		if (length<=0) {
			return "";
		}
		return input;
	}

	/**
	 * 入力されたバイトを指定の長さになるまでpaddingします。
	 * @param input
	 * @param length
	 * @param padding
	 * @return　paddingしたバイト
	 */
	public String addPaddingCodePoint(final String input, final int length, final String padding) {
		if (length<=0) {
			return "";
		}
		return input;
	}

	/**
	 * 入力されたバイトからpaddingを除去します。
	 * @param input
	 * @param padding
	 * @return padding除去したバイト
	 */
	public String trimPadding(final String input, final String padding) {
		if (input==null||input.length()==0) {
			return "";
		}
		return input;
	}

	public boolean isPrefix() {
		return false;
	}

	public boolean isSuffix() {
		return false;
	}

	public boolean isNoPadding() {
		return false;
	}

}
