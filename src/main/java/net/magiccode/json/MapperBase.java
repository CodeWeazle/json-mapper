/**
 * 
 */
package net.magiccode.json;

import javax.annotation.processing.AbstractProcessor;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.squareup.javapoet.ClassName;


public abstract class MapperBase extends AbstractProcessor {

	public MapperBase() {
	}
	
	protected ClassName getName(TypeMirror typeMirror) {
		if (typeMirror instanceof DeclaredType) {
			if (((DeclaredType) typeMirror).asElement() instanceof TypeElement) {
				return ClassName.get(getClass((TypeElement) ((DeclaredType) typeMirror).asElement()));
			}
		}
		return null;
	}
	
	protected static Class<?> getClass(TypeElement element) {
        try {/*w w w .j  a va 2s .  c  om*/
            return Class.forName(getClassName(element));
        } catch (Exception e) {
            //System.out.println(e);
        }
        return null;
    }

	protected static String getClassName(TypeElement element) {
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
}
