/**
 * kilauea
 * 
 * Published under Apache-2.0 license (https://github.com/CodeWeazle/kilauea/blob/main/LICENSE)
 * 
 * Code: https://github.com/CodeWeazle/kilauea
 * 
 * @author CodeWeazle (2023)
 * 
 * Filename: ReflectionUtil.java
 */
package net.magiccode.json.util;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

import lombok.extern.log4j.Log4j2;

/**
 * Collection of utility methods used for reflection purposes, such as invoking getter and setter methods
 * 
 */
@Log4j2
public class ReflectionUtil {

	/**
	 * invoke the getter method on the given field.
	 * 
	 * @param src
	 * @param field
	 * @throws IllegalAccessException
	 */
	public static Object invokeGetterMethod(final Object src, final Field field) throws IllegalAccessException {
		PropertyDescriptor objPropertyDescriptor;
		Object variableValue = null;
		try {
			objPropertyDescriptor = new PropertyDescriptor(field.getName(), src.getClass(),
					"get" + StringUtil.capitalise(field.getName()), null);
			variableValue = objPropertyDescriptor.getReadMethod().invoke(src);
		} catch (IllegalAccessException | IntrospectionException | InvocationTargetException e) {
			try {
				objPropertyDescriptor = new PropertyDescriptor(field.getName(), src.getClass(),
						"is" + StringUtil.capitalise(field.getName()), null);
				variableValue = objPropertyDescriptor.getReadMethod().invoke(src);
			} catch (IntrospectionException | InvocationTargetException e1) {				
				try {
					objPropertyDescriptor = new PropertyDescriptor(field.getName(), src.getClass(),field.getName(), null);
					variableValue = objPropertyDescriptor.getReadMethod().invoke(src);
				} catch (IntrospectionException | InvocationTargetException e2) {
					logger.error("Exception occured when reading property descriptor for " + src.getClass() + ", field "+ field.getName(), e2);
					if (field.canAccess(src)) {
						variableValue = field.get(src);
					}
				}
			}
		}
		return variableValue;
	}
	
	/**
	 * invoke the setter method on the given field to the set value of the field to
	 * the given value
	 * 
	 * @param dest
	 * @param field
	 * @param srcValue
	 * @throws IllegalAccessException
	 */
	public static void invokeSetterMethod(final Object dest, final Field field, final Object srcValue)
			throws IllegalAccessException {
		/* Set field/variable value using getWriteMethod() */
		//ConvertUtils.register (new LocalDateTimeConverter (), LocalDateTime.class);
		PropertyDescriptor objPropertyDescriptor;
		try {
			objPropertyDescriptor = new PropertyDescriptor(field.getName(), dest.getClass(), null,
					"set" + StringUtil.capitalise(field.getName()));
			objPropertyDescriptor.getWriteMethod().invoke(dest, srcValue);
		} catch (IntrospectionException | IllegalArgumentException | InvocationTargetException e) {
			try {
				objPropertyDescriptor = new PropertyDescriptor(field.getName(), dest.getClass(), null, field.getName());
				objPropertyDescriptor.getWriteMethod().invoke(dest, srcValue);
			} catch (IntrospectionException | IllegalArgumentException | InvocationTargetException e1) {
				// in case, we can set it by reflection...
				field.setAccessible(true);

				if (field.canAccess(dest)) {
					field.set(dest, srcValue);
				} else {
					logger.error(
							"Exception occured when writing property for " + dest.getClass() + ", field " + field.getName(),
							e1);
				}
			}
		}
	}
	
	/**
	 * get a field from the given class or any of it's super-classes.
	 * 
	 * @param clazz
	 * @param fieldName
	 * @return
	 */
	public final static Field deepGetField(final Class<?> clazz, final String fieldName) {
		return deepGetField(clazz, fieldName, false);
	}
	
	/**
	 * get a field from the given class or any of it's super-classes.
	 * Setting ignoreMissing to <i>true</i> suppresses the error logging
	 * 
	 * @param clazz
	 * @param fieldName
	 * @param ignoreMissing
	 * @return
	 */
	public final static Field deepGetField(final Class<?> clazz, final String fieldName, boolean ignoreMissing) {
		Class<?> entityClass = clazz;
		Field field = null;
		try {
			field = findOneField(entityClass, fieldName);
			if (field == null && entityClass.getSuperclass() != null) { // we don't want to process Object.class
				field = deepGetField(entityClass.getSuperclass(), fieldName);
			}
		} catch (SecurityException e) {
			if (! ignoreMissing)
				logger.error("Exception occured during deep scan of " + clazz.getName() + ", field " + fieldName, e);
		}
		return field;
	}

	
	/**
	 * return one particular field from the given class
	 * #
	 * @param clazz
	 * @param fieldName
	 * @return
	 */
	private static Field findOneField(Class<?> clazz, String fieldName) {
		Field field = null;
		try {
			field = clazz.getDeclaredField(fieldName);
		} catch (NoSuchFieldException | SecurityException e) {
		}
		return field;
	}

}
