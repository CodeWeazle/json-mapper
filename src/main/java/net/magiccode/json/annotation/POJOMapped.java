/**
 * 
 */
package net.magiccode.json.annotation;

import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;

@Documented
@Target(TYPE)
/**
 * Any class annotated with POJOMapped will generate a plain copy with 
 * all fields of the original class.
 * 
 *  To create an instance of the annotated class from an instance of the generated
 *  class, the <i>to()</i> method can be used.
 *  
 *  An instance of the generated class can also be created by using the <i>of()</i> method
 *  with arguments, which basically functions just like an all arguments constructor.
 *  
 */
public @interface POJOMapped {
	
	/**
	 * Generates setters which return this. Defaults to <i>true</i>
	 * 
	 * @return  as set or true (default)
	 */
	boolean chainedSetters() default true;
	
	/**
	 * Creates getters and setters that do not start with get, is or set rather than the actual name of the field. If useLombok is true, this setting is passed on to @Accessors(fluent=true|false).
	 * 
	 * @return as set or false (default)
	 */
	boolean fluentAccessors() default false;
	
	/**
	 * Adds a prefix to the name of the generated class. Defaults to "JSON"
	 * 
	 * @return  as set or "JSON" (default)
	 */
	String prefix() default "Plain";
	
	/**
	 * Defines the name of the packacke for the generated class. If no packageName is given, this defaults to the package of the annotated class.
	 * 
	 * @return as set or empty (default), means the package name of the annotated class plus subpackageName is used.
	 */
	String packageName() default "";
	
	/**
	 * Defines the name for a subpackage added to the default if packageName is not specified.
	 * 
	 * @return as set or "json" (default)
	 */
	String subpackageName() default "dto";
	
	/**
	 * Setting useLombok to true generates much less code, because getters and setters can be replace by lombok annotations, just as the constructor(s), toString etc.
	 * 
	 * @return as set or false (default)
	 */
	boolean useLombok() default false;
	
	/**
	 * 
	 * Fully qualified name of the superclass that the generated class will extend.
	 * 
	 * @return as set or empty
	 */
	String superclass() default "";
	
	/**
	 * Comma separated list of fully qualified name of the interfaces that the generated class will implement.
	 * 
	 * @return as set or empty)
	 */
	String[] interfaces() default{};
	
	/**
	 * Defines whether or not fields from the super-class hierarchy of the annotated class should be generated. Default is true
	 * 
	 * @return as set or true (default)
	 */
	boolean inheritFields() default true;
	
}
