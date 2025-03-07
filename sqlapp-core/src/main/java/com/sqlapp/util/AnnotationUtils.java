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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import static com.sqlapp.util.CommonUtils.*;
/**
 * アノテーション用のユーティリティ
 * @author satoh
 *
 */
public class AnnotationUtils {
	/**
	 * 指定したアノテーションを持つフィールドを返すメソッド
	 * @param clazz 対象のクラス
	 * @param aClazz フィールドに指定されたアノテーションのクラス
	 * @return 指定したアノテーションを持つフィールドのリスト
	 */
	public static List<Field> getDeclaredFieldByAnnotation(Class<?> clazz, Class<? extends Annotation> aClazz){
		return getFieldByAnnotation(clazz.getDeclaredFields(), aClazz);
	}

	private static List<Field> getFieldByAnnotation(Field[] fields, Class<? extends Annotation> aClazz){
		List<Field> list=list();
		for(int i=0;i<fields.length;i++){
			Field field=fields[i];
			Annotation[] annotations=field.getAnnotations();
			for(int j=0;j<annotations.length;j++){
				if (annotations[j].annotationType()==aClazz){
					list.add(field);
				}
			}
		}
		return list;
	}

	/**
	 * 指定したアノテーションを持つpublicフィールドを返すメソッド
	 * @param clazz 対象のクラス
	 * @param aClazz publicフィールドに指定されたアノテーションのクラス
	 * @return 指定したアノテーションを持つフィールドのリスト
	 */
	public static List<Field> getFieldByAnnotation(Class<?> clazz, Class<? extends Annotation> aClazz){
		return getFieldByAnnotation(clazz.getFields(), aClazz);
	}

	/**
	 * 指定したアノテーションを持つメソッドを返すメソッド
	 * @param clazz 対象のクラス
	 * @param aClazz メソッドに指定されたアノテーションのクラス
	 * @return 指定したアノテーションを持つメソッドのリスト
	 */
	public static List<Method> getDeclaredMethodByAnnotation(Class<?> clazz, Class<? extends Annotation> aClazz){
		Method[] methods=clazz.getMethods();
		List<Method> list=list();
		for(int i=0;i<methods.length;i++){
			Method method=methods[i];
			Annotation[] annotations=method.getAnnotations();
			for(int j=0;j<annotations.length;j++){
				if (annotations[j].annotationType()==aClazz){
					list.add(method);
				}
			}
		}
		return list;
	}

}
