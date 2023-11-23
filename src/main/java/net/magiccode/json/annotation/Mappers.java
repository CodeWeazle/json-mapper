/**
 * 
 */
package net.magiccode.json.annotation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Documented
@Retention(CLASS)
@Target(TYPE)
/**
 * super annotation for repeatable {@code @Mapped}
 */
public @interface Mappers {
	Mapped[] value();
}
