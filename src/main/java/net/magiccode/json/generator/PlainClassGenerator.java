/**
 * json-mapper
 * 
 * Published under Apache-2.0 license (https://github.com/CodeWeazle/json-mapper/blob/main/LICENSE)
 * 
 * Code: https://github.com/CodeWeazle/json-mapper
 * 
 * @author CodeWeazle (2023)
 * 
 * Filename: JSONClassGenerator.java
 */
package net.magiccode.json.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

import net.magiccode.json.annotation.Mapped;
import net.magiccode.json.annotation.MapperTransient;
import net.magiccode.json.annotation.POJOMappedBy;

// 
/**
 * Generates JSON annotated mapping class for a given java class.
 * 
 * The generated class provides all the fields of the annotated class as well as methods to 
 * map between instances of these two classes seamlessly. (to/of methods)
 */
public class PlainClassGenerator extends AbstractClassGenerator {

	/**
	 * The purpose of this class is to generate JSON annotated Java code using the
	 * JavaPoet framework. See documentation for more details about
	 * <i>JSONMapped</i>
	 * 
	 * @param procEnv  - the processing environment
	 * @param filer    - the filer
	 * @param messager - used to output messages
  	 * @param annotationInfo {@code ElementInfo} instance describing the annotation options
	 */
	public PlainClassGenerator(final ProcessingEnvironment procEnv, 
							   final Filer filer, 
							   final Messager messager,
							   final ElementInfo annotationInfo,
							   final ClassName annotatedClass,
							   final Map<ClassName, List<ElementInfo>> input) {
		super(procEnv, filer, messager, annotationInfo, annotatedClass, input);
	}


	
	/**
	 * create JSON specific methods
	 */
	@Override
	public
	void createSpecificFieldsAndMethods(ClassName incomingObjectClass, String packageName, String className,
			ElementInfo annotationInfo, List<FieldSpec> fields, Map<String, MethodSpec> methods) {
	}
	
	/**
	 * mapped by annotation is specific to type of mapper
	 *
	 * @param annotationInfo {@code ElementInfo} instance describing the annotation options
	 * @return annotation {@code AnnotationSpec} instance of created mapped-by annotation
	 */
	public AnnotationSpec createMappedByAnnotation(final ElementInfo annotationInfo) {
		return AnnotationSpec.builder(POJOMappedBy.class)
				.addMember("mappedClass", "$T.class", ClassName.get(annotationInfo.element())).build();
	}
	
	/**
	 * no annotations necessary
	 *
	 * @param annotationInfo {@code ElementInfo} instance describing the annotation options
	 * @return List of annotations {@code AnnotationSpec} instances or null.
	 */
	
	public List<AnnotationSpec>  getAdditionalAnnotationsForClass(final ElementInfo annotationInfo) {
		return null;
	}
	

	/**
	 * create field
	 * 
	 * @param field         - VariableElement representation of field to be created
	 * @param annotationInfo - {@code ElementInfo} instance containing information about the {@code @JSONMapped} annotation
	 * @param fieldClass    - TypeName for class field shall be created in.
	 * @param fieldIsMapped - indicates whether or not the given field is annotated  with {@code JSONMapped}.
	 * @return field specification for the create field.
	 */
	@Override
	public FieldSpec createFieldSpec(final VariableElement field, 
									 final ElementInfo annotationInfo, 
									 final TypeName fieldClass,				
									 boolean fieldIsMapped) {

		List<AnnotationSpec> annotations = new ArrayList<>();
		
		FieldSpec fieldspec = null;
		String fieldName = field.getSimpleName().toString();
		if (field.getAnnotation(MapperTransient.class) == null) {
			
			TypeMirror type = field.asType();

			TypeName fieldType = fieldClass;
			if (fieldIsMapped) {
				TypeMirror fieldTypeMirror = field.asType();
				Element fieldElement = typeUtils.asElement(fieldTypeMirror);
				if (fieldElement instanceof TypeElement) {
					ClassName fieldClassName = ClassName.get((TypeElement) fieldElement);
					fieldType = getMappedTypeForClassName(fieldClassName);
				}
			}
			fieldType = checkFieldTypeForCollections(annotationInfo, type, fieldType);

			FieldSpec.Builder fieldSpecBuilder = FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE); 
			annotations.stream().forEach(annotation -> {
				fieldSpecBuilder.addAnnotation(annotation);
			});
			fieldspec = fieldSpecBuilder.build();
			
		} else {
			fieldspec = FieldSpec.builder(fieldClass, fieldName, Modifier.PRIVATE, Modifier.FINAL)
								 .build();
		}
		return fieldspec;
	}

	@Override
	public Types getTypeUtils() {
		return procEnv.getTypeUtils();
	}

	@Override
	public Elements getElementUtils() {
		return procEnv.getElementUtils();
	}
	
	public boolean fieldIsMapped(final Element field) {
		return fieldIsAnnotedWith(field, Mapped.class, GeneratorType.POJO);
	}

	public boolean typeIsMapped(final TypeElement typeElement) {
		return typeIsAnnotatedWith(typeElement, Mapped.class, GeneratorType.POJO);
	}

}
