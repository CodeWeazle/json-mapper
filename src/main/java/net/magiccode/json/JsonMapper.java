/**
 * json-mapper
 * 
 * Published under Apache-2.0 license (https://github.com/CodeWeazle/json-mapper/blob/main/LICENSE)
 * 
 * Code: https://github.com/CodeWeazle/json-mapper
 * 
 * @author CodeWeazle (2023)
 * 
 * Filename: JsonMapper.java
 */
package net.magiccode.json;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;

import net.magiccode.json.annotation.JSONMapped;
import net.magiccode.json.annotation.POJOMapped;
import net.magiccode.json.generator.ClassGeneratorFactory;
import net.magiccode.json.generator.ElementInfo;
import net.magiccode.json.generator.ElementInfo.ElementInfoBuilder;
import net.magiccode.json.generator.GeneratorType;
import net.magiccode.json.generator.JSONClassGenerator;

/**
 * Annotation processor with the purpose to generate Jackson annotated Java code for JSON DTOs.
 * 
 *  Supports use of the {@code JSONMapped} annotation to create a copy of the annotated class.
 *  This provides methods (of/to) to map between populated instances of the annotated and the 
 *  generated class.
 */
@SupportedAnnotationTypes("net.magiccode.json.annotation.*")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@AutoService(Processor.class)
public class JsonMapper extends MapperBase {

	/**
	 * supports the creation of new files.
	 */
	private Filer filer;
	/**
	 * messager instance allows to report errors and warnings.
	 */
	private Messager messager;
	/**
	 * The process environment
	 */
	private ProcessingEnvironment procEnv;

	/**
	 * hollow constructor
	 */
	public JsonMapper() {
	}

	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		filer = processingEnv.getFiler();
		messager = processingEnv.getMessager();
		this.procEnv = processingEnv;
	}

	/**
	 * Overriding @see AbstractProcessor.process() allows us to inspect the
	 * code to start the generation
	 *
	 * @param annotations
	 * @param roundEnv - contains the environment of the compiler round.
	 */
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

		Map<ClassName, List<ElementInfo>> result = new HashMap<>();

		// JSON
		// collect annotation information
		processMappedClasses(roundEnv, result,JSONMapped.class);

		// generate code with collected results
		try {
			ClassGeneratorFactory.getClassGenerator(GeneratorType.JSON, procEnv, filer, messager, result).generate();
		} catch (IOException e) {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
		}
		
		result = new HashMap<>();
		// POJO
		// collect annotation information
		processMappedClasses(roundEnv, result, POJOMapped.class);

		// generate code with collected results
		try {						
			ClassGeneratorFactory.getClassGenerator(GeneratorType.POJO, procEnv, filer, messager, result).generate();
		} catch (IOException e) {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
		}
		
		
		return true;
	}

	/**
 	 * process @JSONMapped annotation
 	 * 
	 * @param roundEnv - The environment for this round
	 * @param result - Map containing the generated information from the annotated {@code TypeElement}
	 */
	private void processMappedClasses(final RoundEnvironment roundEnv, final Map<ClassName, List<ElementInfo>> result, final Class<? extends Annotation> annotationClass) {
		
		// retrieve elements annotated with JSONMapped
		for (Element annotatedElement : roundEnv.getElementsAnnotatedWith( annotationClass ) ) {
			// if the annotation is not on a class, report an error !
			if (annotatedElement.getKind() != ElementKind.CLASS) {
				messager.printMessage(Diagnostic.Kind.WARNING, "Only class can be annotated with "+annotatedElement.getSimpleName(),
						annotatedElement);
				continue;
			}
			
			TypeElement typeElement = (TypeElement) annotatedElement;
			
			if (annotationClass.getName().equals(JSONMapped.class.getName()))
				generateJSONClassInformation(result, annotatedElement, typeElement);
			else if (annotationClass.getName().equals(POJOMapped.class.getName()))
				generatePOJOClassInformation(result, annotatedElement, typeElement);
		}

	}

	/**
	 * @param result
	 * @param annotatedElement
	 * @param typeElement
	 */
	private void generateJSONClassInformation(final Map<ClassName, List<ElementInfo>> result,
		Element annotatedElement, TypeElement typeElement) {
		// only execute the following code when the given element has is @JSONMapped
		JSONMapped jsonMapped = annotatedElement.getAnnotation(JSONMapped.class);
		if (jsonMapped != null) {
			generateJSONClassInformation (result, typeElement, jsonMapped);
		}
	}

	
	/**
	 * @param result
	 * @param annotatedElement
	 * @param typeElement
	 */
	private void generatePOJOClassInformation(final Map<ClassName, List<ElementInfo>> result,
		Element annotatedElement, TypeElement typeElement) {
		// only execute the following code when the given element has is @JSONMapped
		POJOMapped pojoMapped = annotatedElement.getAnnotation(POJOMapped.class);
		if (pojoMapped != null) {
			generatePojoClassInformation (result, typeElement, pojoMapped);
		}
	}

	
	/**
	 * collect class information for later generation
	 * 
	 * @param result - Map containing the generated information from the annotated {@code TypeElement}
	 * @param annotatedElement - The annotated {@code TypeElement}
	 * @param jsonMapped - The {@code @JSONMapped} annotation
	 */
	private void generateJSONClassInformation (final Map<ClassName, List<ElementInfo>> result,
										  TypeElement annotatedElement,
										  JSONMapped jsonMapped) {
					
			/* check for superclass
			 * 
			 */
			TypeElement superClassElement = null;			
			// deriving the name of the class containing the annotation
			ClassName className = ClassName.get(annotatedElement);	
			messager.printMessage(Diagnostic.Kind.NOTE, "Class " + className.canonicalName());
	
			if (!result.containsKey(className))
				result.put(className, new ArrayList<ElementInfo>());
	
			if (result.containsKey(className)) {
				
				/** find fields */
				List<VariableElement> fields = ElementFilter.fieldsIn(annotatedElement.getEnclosedElements());				
				/** find typeElement for specified super-classs */				
				
				if (superClassElement == null)
					superClassElement = procEnv.getElementUtils().getTypeElement(jsonMapped.superclass());
				
				if (jsonMapped.inheritFields())
					addSuperclassFields(annotatedElement, fields);
				
				/**
				 * we have to remember, which interface exists and what needs to be created.
				 * We use a map for this purpose. Key is the class-name, value the found TypeElement
				 */
				final Map<String, TypeElement> interfaces = new HashMap<>();
				if (jsonMapped.interfaces() != null) {
					Arrays.asList(jsonMapped.interfaces()).stream().forEach(intf -> {
						TypeElement interfaceElement = procEnv.getElementUtils().getTypeElement(intf);
						interfaces.put(intf, interfaceElement);
					});
				}
				ElementInfo elementInfo = createJsonElementInfo(jsonMapped, annotatedElement, className, fields, superClassElement, interfaces);
				result.get(className).add(elementInfo);
				
			}
			
	}
	
	
	/**
	 * collect class information for later generation
	 * 
	 * @param result - Map containing the generated information from the annotated {@code TypeElement}
	 * @param annotatedElement - The annotated {@code TypeElement}
	 * @param jsonMapped - The {@code @POJOMapped} annotation
	 */
	private void generatePojoClassInformation (final Map<ClassName, List<ElementInfo>> result,
										  TypeElement annotatedElement,
										  POJOMapped jsonMapped) {
					
			/* check for superclass
			 * 
			 */
			TypeElement superClassElement = null;			
			// deriving the name of the class containing the annotation
			ClassName className = ClassName.get(annotatedElement);	
			messager.printMessage(Diagnostic.Kind.NOTE, "Class " + className.canonicalName());
	
			if (!result.containsKey(className))
				result.put(className, new ArrayList<ElementInfo>());
	
			if (result.containsKey(className)) {
				
				/** find fields */
				List<VariableElement> fields = ElementFilter.fieldsIn(annotatedElement.getEnclosedElements());				
				/** find typeElement for specified super-classs */				
				
				if (superClassElement == null)
					superClassElement = procEnv.getElementUtils().getTypeElement(jsonMapped.superclass());
				
				if (jsonMapped.inheritFields())
					addSuperclassFields(annotatedElement, fields);
				
				/**
				 * we have to remember, which interface exists and what needs to be created.
				 * We use a map for this purpose. Key is the class-name, value the found TypeElement
				 */
				final Map<String, TypeElement> interfaces = new HashMap<>();
				if (jsonMapped.interfaces() != null) {
					Arrays.asList(jsonMapped.interfaces()).stream().forEach(intf -> {
						TypeElement interfaceElement = procEnv.getElementUtils().getTypeElement(intf);
						interfaces.put(intf, interfaceElement);
					});
				}
				
				ElementInfo elementInfo = createPojoElementInfo(jsonMapped, annotatedElement, className, fields, superClassElement, interfaces);
				result.get(className).add(elementInfo);
				
			}

	}

	/**
	 * recursively add superclass fields
	 * 
	 * @param classElement
	 * @param fields
	 */
	private void addSuperclassFields(TypeElement classElement, 
											List<VariableElement> fields) {
		
		TypeMirror superclass = classElement.getSuperclass();
		// Only act on TypeKind.DECLARED
		if (superclass != null) { // no supertype, return
			if (!superclass.getKind().equals(TypeKind.DECLARED)) {
					return;
			} else  {				
				// only generate superclasselement if it does not have a @JSONMapped annotation
				TypeElement superClassElement = procEnv.getElementUtils().getTypeElement(superclass.toString());
				if (superClassElement != null /*&& superclass.getAnnotationsByType(JSONMapped.class) == null*/) {
					ElementFilter.fieldsIn(superClassElement.getEnclosedElements()).stream()
						.filter(element -> fields.stream().noneMatch(foundField -> foundField.getSimpleName().equals(element.getSimpleName())))
						.forEach(element -> {
							fields.add(element);
						
						});
					addSuperclassFields(superClassElement, fields);
				}
			}
		}
	}
	
	
	/**
	 * create ElementInfo object out of given JSONMapped information, extended by 
	 * information about the environment like the fields of the annotated class.
	 * 
	 * @param pojoMapped - The {@code @JSONMapped} annotation
	 * @param typeElement - the annotated {@code TypeElement}
	 * @param className {@code ClassName} instance of the class containing the{@code @POJOMapped} annotaton
	 * @param fields - {@code VariableElement} representation of the fields of the annotated class
	 * @param superClassElement - {@code TypeElement} of (an existing) class to be extended by every generated class
	 * @param interfaces - {@code java.util.Map} containing all interfaces the generated classes are to implement.
	 * @return an instance of the class {@code ElementInfo} containing all information from the annotation (or defaults), that are going to be used for the code generation. 
	 */
	private ElementInfo createPojoElementInfo(final POJOMapped pojoMapped, 
										  	  final TypeElement typeElement, 
										  	  final ClassName className,
										  	  final List<VariableElement> fields, 
										  	  final TypeElement superClassElement, 
										  	  final Map<String, TypeElement> interfaces) {
		
		ElementInfoBuilder elementInfoBuiler = ElementInfo.builder().className(className.simpleName()) // the name of the class
																										// containing the
																										// annotation
																	.packageName(pojoMapped.packageName()) // package name of the generated class (optional), default is
																	// package of annotated class
																	.subpackageName(pojoMapped.subpackageName()) // subpackage name added to annotated class package if
																	// packaage name is not given
																	.prefix(pojoMapped.prefix()) // prefix for generated class, defaults to JSON
																	.chainedSetters(pojoMapped.chainedSetters()) // true generates "return this" for setters.
																	.fluentAccessors(pojoMapped.fluentAccessors())
																	.element(typeElement) // the current element
																	.fields(fields) // field descriptions of the annotated class
																	.inheritFields(pojoMapped.inheritFields()) // inherit fields from superclasses
																	.useLombok(pojoMapped.useLombok());
		// add superclass
		if (superClassElement != null) {
			elementInfoBuiler.superclass(ClassName.get((TypeElement)superClassElement));
		}
		// add interfaces
		ElementInfo elementInfo = elementInfoBuiler.build();
		if (interfaces != null && interfaces.size()>0) {
			interfaces.entrySet().stream()
								 // add only existing interfaces
								 .filter(entry -> entry.getValue() != null)
								 // use only the TypeElement
								 .map(entry -> entry.getValue())
								 .forEach(intf-> {
									 elementInfo.addInterface(ClassName.get(intf));
			});
		}
		return elementInfo;
	}


	/**
	 * create ElementInfo object out of given JSONMapped information, extended by 
	 * information about the environment like the fields of the annotated class.
	 * 
	 * @param jsonMapped - The {@code @JSONMapped} annotation
	 * @param typeElement - the annotated {@code TypeElement}
	 * @param className {@code ClassName} instance of the class containing the{@code @JSONMapped} annotaton
	 * @param fields - {@code VariableElement} representation of the fields of the annotated class
	 * @param superClassElement - {@code TypeElement} of (an existing) class to be extended by every generated class
	 * @param interfaces - {@code java.util.Map} containing all interfaces the generated classes are to implement.
	 * @return an instance of the class {@code ElementInfo} containing all information from the annotation (or defaults), that are going to be used for the code generation. 
	 */
	private ElementInfo createJsonElementInfo(final JSONMapped jsonMapped, 
										  final TypeElement typeElement, 
										  final ClassName className,
										  final List<VariableElement> fields, 
										  final TypeElement superClassElement, 
										  final Map<String, TypeElement> interfaces) {
		
		ElementInfoBuilder elementInfoBuiler = ElementInfo.builder().className(className.simpleName()) // the name of the class
																										// containing the
																										// annotation
																	.packageName(jsonMapped.packageName()) // package name of the generated class (optional), default is
																	// package of annotated class
																	.subpackageName(jsonMapped.subpackageName()) // subpackage name added to annotated class package if
																	// packaage name is not given
																	.prefix(jsonMapped.prefix()) // prefix for generated class, defaults to JSON
																	.chainedSetters(jsonMapped.chainedSetters()) // true generates "return this" for setters.
																	.fluentAccessors(jsonMapped.fluentAccessors()).jsonInclude(jsonMapped.jsonInclude()) // defines the
																										// generated
																										// value for
																										// @JsonInclude
																										// generated.
																	.element(typeElement) // the current element
																	.fields(fields) // field descriptions of the annotated class
																	.inheritFields(jsonMapped.inheritFields()) // inherit fields from superclasses
																	.datePattern(jsonMapped.datePattern())
																	.dateTimePattern(jsonMapped.dateTimePattern())
																	.useLombok(jsonMapped.useLombok());
		// add superclass
		if (superClassElement != null) {
			elementInfoBuiler.superclass(ClassName.get((TypeElement)superClassElement));
		}
		// add interfaces
		ElementInfo elementInfo = elementInfoBuiler.build();
		if (interfaces != null && interfaces.size()>0) {
			interfaces.entrySet().stream()
								 // add only existing interfaces
								 .filter(entry -> entry.getValue() != null)
								 // use only the TypeElement
								 .map(entry -> entry.getValue())
								 .forEach(intf-> {
									 elementInfo.addInterface(ClassName.get(intf));
			});
		}
		return elementInfo;
	}


}
