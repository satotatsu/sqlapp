/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util;

public enum BytePadding {
	PREFIX(){
		@Override
		public byte[] toPaddingBytes(final byte[] input, final int length, final byte[] padding) {
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
		public byte[] trimBytes(final byte[] input, final byte[] padding) {
			if (input==null||input.length==0) {
				return EMPTY_BYTES;
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
		public boolean isPrefix() {
			return true;
		}
	},
	SUFFIX,;
	
	private static final byte[] EMPTY_BYTES=new byte[0];

	/**
	 * 入力されたバイトを指定の長さになるまでpaddingします。
	 * @param input
	 * @param length
	 * @param padding
	 * @return　paddingしたバイト
	 */
	public byte[] toPaddingBytes(final byte[] input, final int length, final byte[] padding) {
		if (length<=0) {
			return EMPTY_BYTES;
		}
		final byte[] result=new byte[length];
		final int len=input.length>length?length:input.length;
		System.arraycopy(input, 0, result, 0, len);
		for(int i=len;i<length;i=i+padding.length) {
			for(int j=0;j<padding.length;j++) {
				result[i+j]=padding[j];
			}
		}
		return result;
	}

	/**
	 * 入力されたバイトからpaddingを除去します。
	 * @param input
	 * @param padding
	 * @return padding除去したバイト
	 */
	public byte[] trimBytes(final byte[] input, final byte[] padding) {
		if (input==null||input.length==0) {
			return EMPTY_BYTES;
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

	public boolean isPrefix() {
		return false;
	}
}
