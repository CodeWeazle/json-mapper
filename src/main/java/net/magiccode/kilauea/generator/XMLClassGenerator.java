/**
 * kilauea
 * 
 * Published under Apache-2.0 license (https://github.com/CodeWeazle/kilauea/blob/main/LICENSE)
 * 
 * Code: https://github.com/CodeWeazle/kilauea
 * 
 * @author CodeWeazle (2023)
 * 
 * Filename: XMLClassGenerator.java
 */
package net.magiccode.kilauea.generator;

import java.util.ArrayList;
import java.util.Arrays;
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
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

import net.magiccode.kilauea.annotation.Mapped;
import net.magiccode.kilauea.annotation.XMLMappedBy;
import net.magiccode.kilauea.annotation.XMLNamespace;
import net.magiccode.kilauea.annotation.XMLRequired;
import net.magiccode.kilauea.annotation.XMLTransient;
import net.magiccode.kilauea.util.StringUtil;

// 
/**
 * Generates XML annotated mapping class for a given java class.
 * 
 * The generated class provides all the fields of the annotated class as well as methods to 
 * map between instances of these two classes seamlessly. (to/of methods)
 */
public class XMLClassGenerator extends AbstractClassGenerator {

	/**
	 * The purpose of this class is to generate XML annotated Java code using the
	 * JavaPoet framework. See documentation for more details about
	 * <i>Mapped(type=GeneratorType.XML)</i>
	 * 
	 * @param procEnv  - the processing environment
	 * @param filer    - the filer
	 * @param messager - used to output messages
	 * @param annotationInfo - {@code ElementInfo} instance containing information about the {@code @Mapped(type=GeneratorType.XML)} annotation
	 */
	public XMLClassGenerator(final ProcessingEnvironment procEnv, 
							  final Filer filer, 
							  final Messager messager,
							  final ElementInfo annotationInfo,
							  final ClassName annotatedClass,
							  final Map<ClassName, List<ElementInfo>> input) {
		super(procEnv, filer, messager, annotationInfo, annotatedClass, input);
	}


	
	/**
	 * create XML specific methods
	 */
	@Override
	public
	void createSpecificFieldsAndMethods(ClassName incomingObjectClass, String packageName, String className,
			ElementInfo annotationInfo, List<FieldSpec> fields, Map<String, MethodSpec> methods) {
		createToXMLString(methods);		
	}
	
	/**
	 * mapped by annotation is specific to type of mapper
	 *
	 * @param annotationInfo {@code ElementInfo} instance describing the annotation options
	 * @return annotation {@code AnnotationSpec} instance of created mapped-by annotation
	 */
	public AnnotationSpec createMappedByAnnotation(final ElementInfo annotationInfo) {
		return AnnotationSpec.builder(XMLMappedBy.class)
				.addMember("mappedClass", "$T.class", ClassName.get(annotationInfo.element())).build();
	}
	
	/**
	 * annotations specifically for the type XML
	 *
	 * @param annotationInfo {@code ElementInfo} instance describing the annotation options
	 * @return List of annotations {@code AnnotationSpec} instances or null.
	 */
	
	public List<AnnotationSpec> getAdditionalAnnotationsForClass(final ElementInfo annotationInfo) {
		AnnotationSpec.Builder xmlRootAnnotationBuilder = AnnotationSpec.builder(XmlRootElement.class);		
		xmlRootAnnotationBuilder.addMember("name", "$S", StringUtil.camelToSnake( annotatedClass.simpleName() ) );
		if (StringUtil.isNotBlank(annotationInfo.xmlns())) {
			xmlRootAnnotationBuilder.addMember("namespace", "$S" , annotationInfo.xmlns());
		}
		
		List<AnnotationSpec> annotations =
				List.of(xmlRootAnnotationBuilder.build(),
							  
				AnnotationSpec.builder(XmlAccessorType.class)
							  .addMember("value", "$T.$L", XmlAccessType.class, XmlAccessType.FIELD.name())
							  .build());
		return annotations;
	}
	

	/**
	 * generate toString method
	 * 
	 * @param methods - Map containing the methods to be created. Key is the name of the method, value a MethodSpec instance
	 * 		
	 */
	private void createToXMLString(final Map<String, MethodSpec> methods) {
		// create toXMLString method
		MethodSpec.Builder toStringBuilder = MethodSpec.methodBuilder("toXMLString").addModifiers(Modifier.PUBLIC)
				.addJavadoc(CodeBlock.builder().add("provides a formatted XML string with all fields\n")
						.add("and their current values.\n").build())
				.addStatement("$T mapper = new $T()", ObjectMapper.class, XmlMapper.class)
				.addStatement("mapper.registerModule(new $T())", JaxbAnnotationModule.class)
				
				.addStatement("mapper.findAndRegisterModules()")
				.addStatement("String value = this.getClass().getName()+\"\\n\"")
				.beginControlFlow("try")
				.addStatement("value += mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this)")
				.endControlFlow().beginControlFlow("catch ($T e)", JsonProcessingException.class)
				.addStatement("e.printStackTrace()").endControlFlow().addStatement("return value")
				.returns(ClassName.get(String.class));
		methods.put("toXMLString",  toStringBuilder.build());
	}

	/**
	 * create field
	 * 
	 * @param field         - VariableElement representation of field to be created
	 * @param annotationInfo - {@code ElementInfo} instance containing information about the {@code @Mapped(type=GeneratorType.XML)} annotation
	 * @param fieldClass    - TypeName for class field shall be created in.
	 * @param fieldIsMapped - indicates whether or not the given field is annotated  with {@code Mapped(type=GeneratorType.XML)}.
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
		if (field.getAnnotation(XMLTransient.class) == null) {
			AnnotationSpec.Builder xmlPropertyAnnotationBuilder;
			// primitive types (and types in java.lang) are mapped as attributes
			if (fieldClass.isPrimitive() || field.asType().toString().startsWith("java.lang") ) {
				xmlPropertyAnnotationBuilder = AnnotationSpec.builder(XmlAttribute.class).addMember(
						"name", StringUtil.quote(StringUtil.camelToSnake(field.getSimpleName().toString()), '"'));
			} else { // others as elements
				xmlPropertyAnnotationBuilder = AnnotationSpec.builder(XmlElement.class).addMember(
						"name", StringUtil.quote(StringUtil.camelToSnake(field.getSimpleName().toString()), '"'));
			}
			
			if (field.getAnnotation(XMLRequired.class) != null) {
				xmlPropertyAnnotationBuilder.addMember("required", "true");
			}


			// check for namespace annotation on field
			if (field.getAnnotation(XMLNamespace.class) != null) {
				String namespace = field.getAnnotation(XMLNamespace.class).value();
				xmlPropertyAnnotationBuilder.addMember("namespace", "$S" , namespace);
			} else {
				String namespace = fieldIsMapped ? getMappingAnnotationNamespace(field) : annotationInfo.xmlns();
				if (StringUtil.isBlank(namespace) && StringUtil.isNotBlank(annotationInfo.xmlns())) {	// add default namespace if not blank
					namespace = annotationInfo.xmlns();
				}
				if (StringUtil.isNotBlank(namespace))
					xmlPropertyAnnotationBuilder.addMember("namespace", "$S" , namespace);
			}

			annotations.add(xmlPropertyAnnotationBuilder.build());
			
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
			fieldspec = FieldSpec.builder(fieldClass, fieldName, Modifier.PRIVATE)
								 .addAnnotation(XmlTransient.class)
								 .build();
		}
		return fieldspec;
	}

	/**
	 * retrieve namespace entry of {@code Mapped} annotation of field mapped 
	 * with {@code Mappde(type=GeneratorType.XML)}
	 * 
	 * @param field the field to check for as a {@code VariableElement}
	 * @return the namespace of the class of the field.
	 */
	private String getMappingAnnotationNamespace(VariableElement field) {
		TypeElement fieldClassType = getElementUtils().getTypeElement(field.asType().toString());
		String nameSpace="";
		if (fieldClassType != null) {
			nameSpace = 
				Arrays.asList(fieldClassType.getAnnotationsByType(Mapped.class))
				.stream()
				.filter(annotation -> ((Mapped) annotation).type().equals(GeneratorType.XML))
				.map(annotation -> ((Mapped) annotation).xmlns())
				.findFirst().orElse("");
		}
		return nameSpace;		
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
		return fieldIsAnnotedWith(field, Mapped.class, GeneratorType.XML);
	}

	public boolean typeIsMapped(final TypeElement typeElement) {
		return typeIsAnnotatedWith(typeElement, Mapped.class, GeneratorType.XML);
	}

}
