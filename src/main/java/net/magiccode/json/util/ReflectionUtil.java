/**
 * 
 */
package net.magiccode.json.util;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

import lombok.extern.log4j.Log4j2;

/**
 * 
 */
@Log4j2
public class ReflectionUtil {

	/**
	 * invoke the getter method on the given field.
	 * 
	 * @param src
	 * @param dest
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
	
}
