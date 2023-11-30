/**
 * kilauea
 * 
 * Published under Apache-2.0 license (https://github.com/CodeWeazle/kilauea/blob/main/LICENSE)
 * 
 * Code: https://github.com/CodeWeazle/kilauea
 * 
 * @author CodeWeazle (2023)
 * 
 * Filename: MapperBase.java
 */
package net.magiccode.kilauea;

import java.util.Map.Entry;

import javax.annotation.processing.AbstractProcessor;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.squareup.javapoet.ClassName;


/**
 * abstract superclass for class mappers.
 * provides useful methods for the processing
 * of the mapper annotations.
 */
public abstract class MapperBase extends AbstractProcessor {

	public MapperBase() {
	}
	
	public static ClassName getName(TypeMirror typeMirror) {
		if (typeMirror instanceof DeclaredType) {
			if (((DeclaredType) typeMirror).asElement() instanceof TypeElement) {
				return ClassName.get(getClass((TypeElement) ((DeclaredType) typeMirror).asElement()));
			}
		}
		return null;
	}
	
	/**
	 * get the class represented by the given TypeElement
	 * @param element the TypeElement to inspect
	 * @return the class object for the TypeElement if available, else null
	 */
	public static Class<?> getClass(Element element) {
        try {
            return Class.forName(getClassName(element));
        } catch (Exception e) {
            System.out.println(e);
        }
        return null;
    }

	/**
	 * return class name including inner classes
	 * @param element the TypeElement to inspect
	 * @return the name of the class.
	 */
	public static String getClassName(Element element) {
        Element currElement = element;
        String result = element.getSimpleName().toString();
        while (currElement.getEnclosingElement() != null) {
            currElement = currElement.getEnclosingElement();
            if (currElement instanceof TypeElement) {
                result = currElement.getSimpleName() + "$" + result;
            } else if (currElement instanceof PackageElement) {
                if (!"".equals(currElement.getSimpleName().toString())) {
                    result = ((PackageElement) currElement)
                            .getQualifiedName() + "." + result;
                }
            }
        }
        return result;
    }
	
   /**
     * helper method for the extraction of the values of an Annotation
     *   
	 * @param annotationMirror of the annotation to get the value from
	 * @param key  - the key of the value to get
	 * @return the value of the annotation field as an {@code AnnotationValue}
	 */
	public static AnnotationValue getAnnotationValue(AnnotationMirror annotationMirror, String key) {
	    for(Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotationMirror.getElementValues().entrySet() ) {
	        if(entry.getKey().getSimpleName().toString().equals(key)) {
	            return entry.getValue();
	        }
	    }
	    return null;
	}

}
