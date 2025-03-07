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

/**
 * JavaVMの情報を表すクラス
 * 
 * @author tatsuo satoh
 * 
 */
public class JavaVmInfo {
	/**
	 * バージョン情報
	 */
	private Version version = new Version();

	private String home;

	private static JavaVmInfo instance;

	static {
		instance = new JavaVmInfo();
	}

	public static JavaVmInfo getInstance() {
		return instance;
	}

	private JavaVmInfo() {
		home = System.getProperty("java.home");
	}

	/**
	 * @return the home
	 */
	public String getHome() {
		return home;
	}

	/**
	 * @return the version
	 */
	public Version getVersion() {
		return version;
	}

	/**
	 * バージョン情報クラス
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	public static class Version {

		Version() {
			String val = System.getProperty("java.version");
			String[] splits = val.split("\\.");
			int i = 0;
			specification.setBase(Integer.valueOf(splits[i++]));
			specification.setMajor(Integer.valueOf(splits[i++]));
			String last = splits[i++];
			setMaintenance(last);
		}

		private int minor = 0;
		private String maintenance = null;
		private Specification specification = new Specification();

		/**
		 * @return the specification
		 */
		public Specification getSpecification() {
			return specification;
		}

		/**
		 * @return the minor
		 */
		public int getMinor() {
			return minor;
		}

		/**
		 * @param minor
		 *            the minor to set
		 */
		public void setMinor(int minor) {
			this.minor = minor;
		}

		/**
		 * @return the maintenance
		 */
		public String getMaintenance() {
			return maintenance;
		}

		/**
		 * @param maintenance
		 *            the maintenance to set
		 */
		public void setMaintenance(String maintenance) {
			this.maintenance = maintenance;
		}

		/**
		 * 
		 * @author tatsuo satoh
		 * 
		 */
		public static class Specification {
			private int base = 0;
			private int major = 0;

			/**
			 * @return the base
			 */
			public int getBase() {
				return base;
			}

			/**
			 * @param base
			 *            the base to set
			 */
			protected void setBase(int base) {
				this.base = base;
			}

			/**
			 * @return the major
			 */
			public int getMajor() {
				return major;
			}

			/**
			 * @param major
			 *            the major to set
			 */
			protected void setMajor(int major) {
				this.major = major;
			}

			/**
			 * 指定されたバージョンより大きいかを判定します
			 * 
			 * @param base
			 * @param major
			 */
			public boolean gt(int base, int major) {
				if (getBase() > base) {
					return true;
				} else if (getBase() < base) {
					return false;
				} else {
					if (getMajor() > major) {
						return true;
					} else {
						return false;
					}
				}
			}

			/**
			 * 指定されたバージョン以上かを判定します
			 * 
			 * @param base
			 * @param major
			 */
			public boolean gte(int base, int major) {
				if (gt(base, major)) {
					return true;
				} else if (this.getBase() == base && this.getMajor() == major) {
					return true;
				}
				return false;
			}

			/**
			 * 指定されたバージョンより小さいかを判定します
			 * 
			 * @param base
			 * @param major
			 */
			public boolean lt(int base, int major) {
				if (getBase() < base) {
					return true;
				} else if (getBase() > base) {
					return false;
				} else {
					if (getMajor() < major) {
						return true;
					} else {
						return false;
					}
				}
			}

			/**
			 * 指定されたバージョン以下かを判定します
			 * 
			 * @param base
			 * @param major
			 */
			public boolean lte(int base, int major) {
				if (lt(base, major)) {
					return true;
				} else if (this.getBase() == base && this.getMajor() == major) {
					return true;
				}
				return false;
			}

		}

	}

}
