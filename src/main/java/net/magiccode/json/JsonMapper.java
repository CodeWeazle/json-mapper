/**
 * 
 */
package net.magiccode.json;

import java.io.IOException;
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
import net.magiccode.json.generator.ElementInfo;
import net.magiccode.json.generator.ElementInfo.ElementInfoBuilder;
import net.magiccode.json.generator.JSONClassGenerator;

/**
 * 
 */
@SupportedAnnotationTypes("net.magiccode.json.annotation.*")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@AutoService(Processor.class)
public class JsonMapper extends MapperBase {

	private Filer filer;
	private Messager messager;
	private ProcessingEnvironment procEnv;

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

		// collect annotation information
		processJSONMapped(roundEnv, result);

		// generate code with collected results
		try {
			new JSONClassGenerator(procEnv, filer, messager, result).generate();
		} catch (IOException e) {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
		}
		return true;
	}

	/**
	 * process @JSONMapped annotation
	 * 
	 * @param roundEnv
	 * @param result
	 */
	private void processJSONMapped(final RoundEnvironment roundEnv, final Map<ClassName, List<ElementInfo>> result) {
		
		// retrieve elements annotated with JSONMapped
		for (Element annotatedElement : roundEnv.getElementsAnnotatedWith(JSONMapped.class)) {
			// if the annotation is not on a class, report an error !
			if (annotatedElement.getKind() != ElementKind.CLASS) {
				messager.printMessage(Diagnostic.Kind.WARNING, "Only class can be annotated with JSONMapped",
						annotatedElement);
				continue;
			}
			
			TypeElement typeElement = (TypeElement) annotatedElement;
			// only execute the following code when the given element has is @JSONMapped
			JSONMapped jsonMapped = annotatedElement.getAnnotation(JSONMapped.class);
			if (jsonMapped != null) {
				generateClassInformation (result, typeElement, jsonMapped);
			}
			
		}

	}

	
	/**
	 * collect class information for later generation
	 * 
	 * @param result
	 * @param annotatedElement
	 */
	private void generateClassInformation (final Map<ClassName, List<ElementInfo>> result,
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
				
				ElementInfo elementInfo = createElementInfo(jsonMapped, annotatedElement, className, fields, superClassElement, interfaces);
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
	 * @param jsonMapped
	 * @param typeElement
	 * @param className
	 * @param fields
	 * @param superClassElement
	 * @param interfaces
	 * @return
	 */
	private ElementInfo createElementInfo(JSONMapped jsonMapped, TypeElement typeElement, ClassName className,
			List<VariableElement> fields, TypeElement superClassElement, final Map<String, TypeElement> interfaces) {
		
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
