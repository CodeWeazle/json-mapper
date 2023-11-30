/**
 * 
 */
package net.magiccode.kilauea.annotation;

import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Target;

import com.fasterxml.jackson.annotation.JsonInclude.Include;

import net.magiccode.kilauea.generator.GeneratorType;

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
@Repeatable(Mappers.class)
public @interface Mapped {
	
	/**
	 * type of generator to be used for the mapped class. 
	 * defaults to GeneratorType.POJO, also available GeneratorType.JSON
	 * and GeneratorType.XML
	 *  
	 * @return the selected generator types
	 */
	GeneratorType type() default GeneratorType.POJO;
	
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
	 * @return  as set or the uppercase type (POJO,JSON,XML)
	 */
	String prefix() default "";
	
	/**
	 * Defines the name of the packacke for the generated class. If no packageName is given, this defaults to the package of the annotated class.
	 * 
	 * @return as set or empty (default), means the package name of the annotated class plus subpackageName is used.
	 */
	String packageName() default "";
	
	/**
	 * Defines the name for a subpackage added to the default if packageName is not specified.
	 * 
	 * @return as set or the lowercase type (pojo,json,xml)
	 */
	String subpackageName() default "";
	
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
	
	/**
	 * LocalDate fields are annotated with {@code @JsonFormat} in the generated class. This option allows to specify a custom format 
	 * for a date pattern to customise the JSON output. Default pattern is "yyyy-MM-dd"
	 * 
	 * @return as set or true (default)
	 */
	String datePattern() default  "yyyy-MM-dd";

	/**
	 * LocalDateTime fields are annotated with {@code @JsonFormat} in the generated class. This option allows to specify a custom format 
	 * for a date pattern to customise the JSON output. Default pattern is "yyyy-MM-dd HH:mm:ss" 
	 */
	String dateTimePattern() default  "yyyy-MM-dd HH:mm:ss";
	
	/**
	 * Generated classes are annotated with @JsonInclude. This defaults to ALWAYS, but can be specified otherwise by using the jsonInclude argument.
	 * (ALWAYS, NON_NULL, NON_ABSENT, NON_EMPTY, NON_DEFAULT, CUSTOM, USE_DEFAULTS)
	 * (Only for type=GeneratorType.JSON)
	 * 
	 * @return as set or Include.ALWAYS (default)
	 */
	Include jsonInclude() default Include.ALWAYS;
	
	/**
	 * Defines the default namespace to  be generated into the Property annotation for 
	 * all fields. 
	 * (Only for type=GeneratorType.XML)
	 * 
	 * @return the namespace set for the generated XML annotated class.
	 */
	String xmlns() default "";
	
	/**
	 * yet undocumented and experimental feature which allows to specify @Field annotations
	 * to generate additional fields. 
	 * Example:
	 * {@code
	 * @Mapped(type=GeneratorType.JSON,fluentAccessors = false, 
			useLombok = false,
			additionalFields = @Fields (
					@Field(name="name", 
						   fieldClass=String.class)
				)
			)}
	 * 
	 * @return the defined fields
	 */
	Fields additionalFields() default @Fields(value = { });
}
