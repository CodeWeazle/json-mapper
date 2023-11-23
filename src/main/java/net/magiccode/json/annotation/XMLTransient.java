/**
 * 
 */
package net.magiccode.json.annotation;

import static java.lang.annotation.ElementType.FIELD;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;

@Documented
@Target({FIELD})
/**
 * For classes having a {@code @JSONMapped} annotation, 
 * this marker annotation indicates that the annotated
 * field must be annotated with {@code @JsonIgnore} in the 
 * generated class
 */
public @interface XMLTransient {

}
