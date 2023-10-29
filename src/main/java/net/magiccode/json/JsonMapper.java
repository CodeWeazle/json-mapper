/**
 * 
 */
package net.magiccode.json;

import java.io.IOException;
import java.util.ArrayList;
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
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;

import net.magiccode.json.annotation.JSONMapped;
import net.magiccode.json.generator.ElementInfo;
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

	public JsonMapper() {
	}

	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		filer = processingEnv.getFiler();
		messager = processingEnv.getMessager();
	}

	/**
	 * Overriding <link>AbstractProcessor.process</link> allows us to inspect the
	 * code to start the generation
	 *
	 * @param Set<?            extends TypeElement> annotations - annotations
	 * @param RoundEnvironment roundEnv - contains the environment of the compiler
	 *                         round
	 */
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

		Map<ClassName, List<ElementInfo>> result = new HashMap<>();

		// collect annotation information
		processJSONMapped(roundEnv, result);

		// generate code with collected results
		try {
			new JSONClassGenerator(filer, result).generate();
		} catch (IOException e) {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
		}
		return true;
	}

	private void processJSONMapped(final RoundEnvironment roundEnv, final Map<ClassName, List<ElementInfo>> result) {
		
		// retrieve elements annotated with JSONMapped
		for (Element annotatedElement : roundEnv.getElementsAnnotatedWith(JSONMapped.class)) {
			// if the annotation is not on a class, report an error !
			if (annotatedElement.getKind() != ElementKind.CLASS) {
				messager.printMessage(Diagnostic.Kind.WARNING, "Only class can be annotated with JSONMapped",
						annotatedElement);
				continue;
			}
		
			// getting access to the annotation
			JSONMapped jsonMapped = annotatedElement.getAnnotation(JSONMapped.class);
			TypeElement typeElement = (TypeElement) annotatedElement;
			// deriving the name of the class containing the annotation
			ClassName className = ClassName.get(typeElement);
	
			messager.printMessage(Diagnostic.Kind.NOTE, "Class " + className.canonicalName());
	
			if (!result.containsKey(className))
				result.put(className, new ArrayList<ElementInfo>());
	
			if (result.containsKey(className)) {
	
				List<VariableElement> fields = ElementFilter.fieldsIn(typeElement.getEnclosedElements());
	
				result.get(className).add(ElementInfo.builder().className(className.simpleName()) // the name of the class
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
						.useLombok(jsonMapped.useLombok())
						.build());
			}

//		List<? extends TypeMirror> list = typeElement.getInterfaces();
//		for (TypeMirror typeMirror : list) {
//			ClassName typeName = getName(typeMirror);
//			messager.printNote("Inteface: " + typeName.canonicalName());
//			
//		}
		}

	}

}
