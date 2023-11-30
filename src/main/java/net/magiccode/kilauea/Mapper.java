/**
 * kilauea
 * 
 * Published under Apache-2.0 license (https://github.com/CodeWeazle/kilauea/blob/main/LICENSE)
 * 
 * Code: https://github.com/CodeWeazle/kilauea
 * 
 * @author CodeWeazle (2023)
 * 
 * Filename: JsonMapper.java
 */
package net.magiccode.kilauea;

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
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;

import net.magiccode.kilauea.annotation.Field;
import net.magiccode.kilauea.annotation.Mapped;
import net.magiccode.kilauea.annotation.Mappers;
import net.magiccode.kilauea.generator.ClassGeneratorFactory;
import net.magiccode.kilauea.generator.ElementInfo;
import net.magiccode.kilauea.generator.ElementInfo.ElementInfoBuilder;
import net.magiccode.kilauea.generator.GeneratorType;
import net.magiccode.kilauea.util.StringUtil;

/**
 * Annotation processor with the purpose to generate Jackson annotated Java code for JSON DTOs.
 * 
 *  Supports use of the {@code JSONMapped} annotation to create a copy of the annotated class.
 *  This provides methods (of/to) to map between populated instances of the annotated and the 
 *  generated class.
 */
@SupportedAnnotationTypes("net.magiccode.kilauea.annotation.*")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@AutoService(Processor.class)
public class Mapper extends MapperBase {

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
	public Mapper() {
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
	public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {

		final Map<ClassName, List<ElementInfo>> result = new HashMap<>();

		// collect annotation information
		processMappedClasses(roundEnv, result, Mapped.class);

		// generate code with collected results

		for (ClassName key : result.keySet()) {
			result.get(key).stream().forEach(annotationInfo -> {
					GeneratorType type = annotationInfo.type();
					if (type == null) {
						type = GeneratorType.POJO;
					}
					// generate class for each given type
					try {
						ClassGeneratorFactory.getClassGenerator(type, procEnv, filer, messager, annotationInfo, key, result).generate();
					} catch (IOException e) {
						messager.printMessage(Diagnostic.Kind.ERROR, "IOException during class generation. ("+e.getLocalizedMessage()+")");
					}
			});
		}
		return true;
	}
	
	/**
 	 * process @Mapped annotation
 	 * 
	 * @param roundEnv - The environment for this round
	 * @param result - Map containing the generated information from the annotated {@code TypeElement}
	 */
	private void processMappedClasses(final RoundEnvironment roundEnv, final Map<ClassName, List<ElementInfo>> result, final Class<? extends Annotation> annotationClass) {
		
		// retrieve elements annotated with JSONMapped
		for (Element annotatedElement : roundEnv.getElementsAnnotatedWithAny( procEnv.getElementUtils().getTypeElement(annotationClass.getCanonicalName()), 
																			  procEnv.getElementUtils().getTypeElement(Mappers.class.getCanonicalName())))  {
			// if the annotation is not on a class, report an error !
			if (annotatedElement.getKind() != ElementKind.CLASS) {
				messager.printMessage(Diagnostic.Kind.WARNING, "Only class can be annotated with "+annotatedElement.getSimpleName(), annotatedElement);
				continue;
			}

			TypeElement typeElement = (TypeElement) annotatedElement;
			
		    Arrays.asList(annotatedElement.getAnnotationsByType(Mapped.class)).stream()
				  .forEach(annotation -> {
					  generateClassInformation (result, typeElement, annotation);												  
				  });
		}
	}

//	/**
//	 * @param result
//	 * @param annotatedElement
//	 * @param typeElement
//	 */
//	private void generateClassInformation(final Map<ClassName, List<ElementInfo>> result,
//		Element annotatedElement, TypeElement typeElement) {
//		// only execute the following code when the given element has is @Mapped
//		Mapped mapped = annotatedElement.getAnnotation(Mapped.class);
//		if (mapped != null) {
//			generateClassInformation (result, typeElement, mapped);
//		}
//	}

	
	/**
	 * collect class information for later generation
	 * 
	 * @param result - Map containing the generated information from the annotated {@code TypeElement}
	 * @param annotatedElement - The annotated {@code TypeElement}
	 * @param mapped - The {@code @Mapped} annotation
	 */
	private void generateClassInformation (final Map<ClassName, List<ElementInfo>> result,
										  TypeElement annotatedElement,
										  Mapped mapped) {
					
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
					superClassElement = procEnv.getElementUtils().getTypeElement(mapped.superclass());
				
				if (mapped.inheritFields())
					addSuperclassFields(annotatedElement, fields);
				
				/**
				 * we have to remember, which interface exists and what needs to be created.
				 * We use a map for this purpose. Key is the class-name, value the found TypeElement
				 */
				final Map<String, TypeElement> interfaces = new HashMap<>();
				if (mapped.interfaces() != null) {
					Arrays.asList(mapped.interfaces()).stream().forEach(intf -> {
						TypeElement interfaceElement = procEnv.getElementUtils().getTypeElement(intf);
						interfaces.put(intf, interfaceElement);
					});
				}
				ElementInfo elementInfo = createElementInfo(mapped, annotatedElement, className, fields, superClassElement, interfaces);
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
				// only generate superclasselement if it does not have a @Mapped annotation
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
	 * @param mapped - The {@code @Mapped} annotation
	 * @param typeElement - the annotated {@code TypeElement}
	 * @param className {@code ClassName} instance of the class containing the{@code @Mapped} annotaton
	 * @param fields - {@code VariableElement} representation of the fields of the annotated class
	 * @param superClassElement - {@code TypeElement} of (an existing) class to be extended by every generated class
	 * @param interfaces - {@code java.util.Map} containing all interfaces the generated classes are to implement.
	 * @return an instance of the class {@code ElementInfo} containing all information from the annotation (or defaults), that are going to be used for the code generation. 
	 */
	private ElementInfo createElementInfo(final Mapped mapped, 
										  final TypeElement typeElement, 
										  final ClassName className,
										  final List<VariableElement> fields, 
										  final TypeElement superClassElement, 
										  final Map<String, TypeElement> interfaces) {
		
		// handle default subpackage name
		String subpackageName = mapped.subpackageName();
		if (StringUtil.isBlank(subpackageName)) {
			subpackageName=mapped.type().name().toLowerCase();
		}

		// handle default class prefix
		String prefix = mapped.prefix();
		if (StringUtil.isBlank(prefix)) {
			prefix=mapped.type().name().toUpperCase();
		}

		// additional fields to be created that are not available in the 
		// annotated class
		final Field[] additionalFields = mapped.additionalFields().value();		
		final Map<String, TypeMirror> additionalFieldMap = new HashMap<>();
		if (additionalFields != null && additionalFields.length > 0) {
			Arrays.asList(additionalFields).stream().forEach(field -> {
				TypeMirror fieldClass = null;
				if( field != null ) {
				    try  {
				        field.fieldClass();
				    }
				    catch( MirroredTypeException mte ) {
				    	fieldClass = mte.getTypeMirror();
				    }
				}
				additionalFieldMap.put(field.name(), fieldClass);
			});			
		}
		
		// build the annotation information object for the generator
		ElementInfoBuilder elementInfoBuiler = ElementInfo.builder().className(className.simpleName()) // the name of the class
																										// containing the
																										// annotation
																	.type(mapped.type())
																	.packageName(mapped.packageName()) // package name of the generated class (optional), default is
																	// package of annotated class
																	.subpackageName(subpackageName) // subpackage name added to annotated class package if
																	// package name is not given
																	.prefix(prefix) // prefix for generated class, defaults to JSON
																	.chainedSetters(mapped.chainedSetters()) // true generates "return this" for setters.
																	.fluentAccessors(mapped.fluentAccessors())
																	.jsonInclude(mapped.jsonInclude()) // type=JSON only. Defines the generated value for @JsonInclude generated.
																	.element(typeElement) // the current element
																	.fields(fields) // field descriptions of the annotated class
																	.inheritFields(mapped.inheritFields()) // inherit fields from superclasses
																	.datePattern(mapped.datePattern())
																	.dateTimePattern(mapped.dateTimePattern())
																	.useLombok(mapped.useLombok())
																	.additionalFields(additionalFieldMap)
																	// xml only
																	.xmlns(mapped.xmlns());
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
    * Return the qualified name of a type (those that contain a $ sign for
    * nested classes).
    * 
    * @param tm Represent the class
    * @return The class name
    */
   public static String getClassName(TypeMirror tm) {
       if (tm.getKind().equals(TypeKind.DECLARED)) {
           TypeElement el = (TypeElement) ((DeclaredType) tm).asElement();
           return getClassName(el);
       } else {
           return tm.toString();
       }
   }


}
