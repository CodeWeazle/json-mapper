/**
 * 
 */
package net.magiccode.kilauea.annotation;

import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Target;

@Documented
@Target(TYPE)
/**
 * Any class annotated with Mapped will generate a copy with 
 * all fields of the original class annotated with Jackson annotations
 * for easy json processing.
 * 
 *  json annotated instances are created by the use of the <i>of()</i> method of the 
 *  generated class, providing an instance of the annotated class as an argument.
 *  
 *  To create an instance of the annotated class from an instance of the generated
 *  class, the <i>to()</i> method can be used.
 *  
 *  An instance of the generated class can also be created by using the <i>of()</i> method
 *  with arguments, which basically functions just like an all arguments constructor.
 *  
 */
@Repeatable(Fields.class)
public @interface Field {
	

	String name() default "";
	
	Class<?> fieldClass();
	
}
