/**
 * 
 */
package net.magiccode.json.annotation;

import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;

import com.fasterxml.jackson.annotation.JsonInclude.Include;

@Documented
@Target(TYPE)
/**
 * 
 */
public @interface JSONMapped {
	
	boolean fromEntity() default true;

	boolean chainedSetters() default true;
	
	boolean fluentAccessors() default false;
	
	String prefix() default "JSON";
	
	String packageName() default "";
	
	String subpackageName() default "json";
	
	Include jsonInclude() default Include.ALWAYS;
	
	boolean useLombok() default false;
	
	String superclass() default "";
	
	String[] interfaces() default{};
	
	boolean inheritFields() default true;
}
