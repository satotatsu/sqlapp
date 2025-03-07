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

package com.sqlapp.data.db.dialect.util;
/**
 * Geometry関係のユーティリティ
 * @author tatsuo satoh
 *
 */
public class GeometryUtils {

	private static boolean exists=false;

	private static Class<?> geometryClass;
	

	/**
	 * Geometry関係のクラスが存在するときだけ処理を実行します。
	 * @param run
	 */
	public static void run(Runnable run){
		try{
			Class.forName("org.geolatte.geom.Geometry");
			Class.forName("com.vividsolutions.jts.geom.Geometry");
			exists=true;
		} catch (Exception e){
			
		}
		try{
			if (exists){
				run.run();
			}
		} catch (Error e){
			
		}
	}
	
	public static Class<?> getGeographyClass(){
		return getGeometryClass();
	}
	
	public static Class<?> getGeometryClass(){
		if (geometryClass!=null){
			return geometryClass;
		}
		try {
			geometryClass= Class.forName("org.geolatte.geom.Geometry");
			exists=true;
			return geometryClass;
		} catch (ClassNotFoundException e) {
			geometryClass= Object.class;
		}
		return geometryClass;
	}
	
}
