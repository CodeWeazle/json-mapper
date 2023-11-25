/**
 * 
 */
package net.magiccode.kilauea.annotation;

import static java.lang.annotation.ElementType.FIELD;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;

@Documented
@Target({FIELD})
/**
 * For classes having a {@code @JSONMapped} annotation, 
 * this marker annotation indicates that the annotated
 * field will be generated with a {@code @JsonProperty}
 * annotation that has an additiona argument<i>required=true</i> 
 * in the generated class.
 */
public @interface JSONRequired {

}
