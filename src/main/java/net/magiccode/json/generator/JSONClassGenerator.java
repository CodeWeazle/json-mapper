package net.magiccode.json.generator;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import net.magiccode.json.annotation.JSONMapped;
import net.magiccode.json.annotation.JSONMappedBy;
import net.magiccode.json.annotation.JSONRequired;
import net.magiccode.json.annotation.JSONTransient;
import net.magiccode.json.util.ReflectionUtil;
import net.magiccode.json.util.StringUtil;


// import com.sun.tools.javac.code.Attribute;

/**
 * Generate a copy 
 */
public class JSONClassGenerator implements ClassGenerator {

	Map<ClassName, List<ElementInfo>> input;
	Filer filer;
	Messager messager;
	ProcessingEnvironment procEnv;
	private final Types typeUtils;


	/**
	 * The purpose of this class is to generate JSON annotated Java code
	 * using the JavaPoet framework. See documentation for more details
	 * about <i>JSONMapped</i>
	 * 
	 * @param procEnv - the processing environment 
	 * @param filer - the filer
	 * @param messager - used to output messages
	 * @param input - information collected beforehand based on the annotation
	 */
	public JSONClassGenerator(ProcessingEnvironment procEnv,
							  Filer filer,
							  Messager messager,
							  Map<ClassName, List<ElementInfo>> input) {
		this.filer = filer;
		this.input = input;
		this.messager = messager;
		this.procEnv = procEnv;
		this.typeUtils = procEnv.getTypeUtils();
	}

	/**
	 * start the generation process
	 * 
	 * @throws IOException if file cannot be written
	 */
	public void generate() throws IOException {

		for (ClassName key : input.keySet()) {

			input.get(key).stream().forEach(annotationInfo -> {

				try {
					String className = annotationInfo.prefix() + annotationInfo.className();

					messager.printMessage(Diagnostic.Kind.NOTE,"Generating " + className);

					String packageName = generatePackageName(key, annotationInfo);
					
						messager.printMessage(Diagnostic.Kind.NOTE,"annotated class "+key.canonicalName()+
																   ", generated class "+packageName+"."+className);

					List<FieldSpec> fields = new ArrayList<>();
					List<MethodSpec> methods = new ArrayList<>();

					// when using lombok, we only need to generate the fields
					if (annotationInfo.useLombok()) {
						createFields(annotationInfo, fields);						
					} else { // otherwise, we also need getters and setters
						createNoArgsConstructor(methods);
						createFieldsGettersAndSetters(annotationInfo, fields, methods);
						createToString(annotationInfo, methods);
					}
					createOfWithArguments(packageName, className, annotationInfo, methods);
					createOfWithClass(key, packageName, className, annotationInfo, methods);
					createToJSONString(methods);	

					// create to method
					String sourcePackageName = ClassName.get(annotationInfo.element()).packageName();
					String sourceClassName = ClassName.get(annotationInfo.element()).simpleName();
					createTo(sourcePackageName, sourceClassName, annotationInfo, methods);
					
					// generate and write class
					TypeSpec generatedJSONClass = generateClass(annotationInfo, className, packageName, fields, methods);
					JavaFile javaFile = JavaFile.builder(packageName, generatedJSONClass)
												.indent("    ")
												.build();
					javaFile.writeTo(filer);
				} catch (IOException e) {
					messager.printMessage(Diagnostic.Kind.ERROR,"Error occured while generating class "+key+". "+e.getLocalizedMessage());
				}
			});
		}
	}

	/**
	 * creates gields, getters and setters for the given fields.
	 * 
	 * @param annotationInfo - information about the annotation arguments
	 * @param fields - list of fields to be created.
	 * @param methods - list of methods to be created
	 */
	private void createFieldsGettersAndSetters(ElementInfo annotationInfo, List<FieldSpec> fields,
			List<MethodSpec> methods) {
		// Generate fields, getters and setters
		annotationInfo.fields().stream()
					  .filter(field -> ! isMethodFinalPrivateStatic(field))
					  .forEach(field -> {

				TypeMirror fieldType = field.asType();
				
				TypeName fieldTypeName = TypeName.get(fieldType);
				boolean fieldIsMapped = fieldIsAnnotedWith(field, JSONMapped.class);
				if (fieldIsMapped) {
					TypeMirror fieldTypeMirror = field.asType();
					Element fieldElement = typeUtils.asElement(fieldTypeMirror);
					if (fieldElement instanceof TypeElement) {
						ClassName fieldClassName = ClassName.get((TypeElement) fieldElement);
						String fcName = annotationInfo.prefix()+fieldClassName.simpleName();
						fieldTypeName = ClassName.get(generatePackageName(fieldClassName, annotationInfo), fcName);
					}
				}
				
				fields.add(createFieldSpec(field, annotationInfo, fieldTypeName, fieldIsMapped));
				methods.add(createGetterMethodSpec(field, annotationInfo, fieldTypeName));
				methods.add(createSetterMethodSpec(field, annotationInfo, fieldTypeName));
			});
	}

	
	/**
	 * create field only. This is useful for useLombok=true cases. 
	 * 
	 * @param annotationInfo - information about the annotation arguments
	 * @param fields - list of fields to be created.
	 */
	private void createFields(ElementInfo annotationInfo, List<FieldSpec> fields) {
		// Generate fields
		annotationInfo.fields().stream()
		  .filter(field -> ! isMethodFinalPrivateStatic(field))
		  .forEach(field -> {
				TypeMirror fieldType = field.asType();
				TypeName fieldClass = TypeName.get(fieldType);
				TypeElement fieldClassElement = procEnv.getElementUtils().getTypeElement(ClassName.get(fieldType).toString());
				boolean fieldIsMapped = fieldIsAnnotedWith(field, JSONMapped.class);
				messager.printMessage(Diagnostic.Kind.NOTE,"Generating field " + field.getSimpleName().toString());
				fields.add(createFieldSpec(field, annotationInfo, fieldClass, fieldIsMapped));
		});		
	}

	/**
	 * Generate the class code with given fields and methods
	 * 
	 * @param annotationInfo - information about the annotation arguments
	 * @param className - class name of the class to be created
	 * @param packageName - the package the class to be created shall be located in.
	 * @param fields - list of fields to be created
	 * @param methods - list of methods to be created
	 * @return typeSpec - object containing the newly generated class
	 */
	public TypeSpec generateClass(ElementInfo annotationInfo, 
								   String className, 
								   String packageName,
								   List<FieldSpec> fields, 
								   List<MethodSpec> methods) {
		DateTimeFormatter pattern = DateTimeFormatter.ofPattern("dd/MM/yyyy");
		TypeSpec.Builder generatedJSONClassBuilder = TypeSpec.classBuilder(className).addModifiers(Modifier.PUBLIC)
						.addJavadoc(CodeBlock.builder()
											.add(packageName+"."+className+" generated by JsonMapper. (@JsonMapper)\n\n")
											.add("@created "+LocalDateTime.now().format(pattern)+"\n")
										    .build())
						.addSuperinterface(Serializable.class)
						.addField(
								FieldSpec.builder(TypeName.LONG, "serialVersionUID", Modifier.FINAL, Modifier.STATIC)
										 .initializer("-1L")
										 .build())
						.addFields(fields)
						.addMethods(methods)
						.addAnnotation(
								AnnotationSpec.builder(JSONMappedBy.class)
								.addMember("mappedClass", "$T.class", ClassName.get(annotationInfo.element()) )
								.build())
						.addAnnotation(
								AnnotationSpec.builder(JsonInclude.class)
											.addMember("value", "$T.$L", Include.class, annotationInfo.jsonInclude().name())
											.build());
		// In case we use lombok, we can just generate some annotations
		if (annotationInfo.useLombok()) {
			generatedJSONClassBuilder.addAnnotation(NoArgsConstructor.class);
			if (fields.size()>0)
				generatedJSONClassBuilder.addAnnotation(AllArgsConstructor.class);
			generatedJSONClassBuilder.addAnnotation(Getter.class)
									 .addAnnotation(Setter.class)	
									 .addAnnotation(ToString.class)
									 .addAnnotation(AnnotationSpec.builder(Accessors.class)
												.addMember("fluent", annotationInfo.fluentAccessors()?"true":"false" )
												.build());
		}
		
		// add superclass
		if (annotationInfo.superclass() != null) {
			generatedJSONClassBuilder.superclass(annotationInfo.superclass());
		}
		
		// add provided interface
		if (annotationInfo.interfaces() != null) {
			annotationInfo.interfaces().stream()
				// filter existing classes 
				 .forEach( intf -> {
					 generatedJSONClassBuilder.addSuperinterface(intf);
				 });			 
		}
		TypeSpec generatedJSONClass = generatedJSONClassBuilder.build();

		messager.printMessage(Diagnostic.Kind.NOTE,"Generated " + className);

		return generatedJSONClass;
	}
	
	/**
	 * generate toString method
	 * 
	 * @param methods - list of methods to be creatd.
	 */
	private void createToJSONString(List<MethodSpec> methods) {
		// create toJSONString method
		MethodSpec.Builder toStringBuilder = MethodSpec.methodBuilder("toJSONString")
				.addModifiers(Modifier.PUBLIC)
				.addJavadoc(CodeBlock.builder()
						.add("provides a formatted JSON string with all fields\n")
						.add("and their current values.\n")
					    .build())
				.addStatement("$T mapper = new $T()", ObjectMapper.class, ObjectMapper.class)
				.addStatement("String value = this.getClass().getName()")
				.beginControlFlow("try")
					.addStatement("value += mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this)")
				.endControlFlow()
				.beginControlFlow("catch ($T e)",JsonProcessingException.class)
						.addStatement("e.printStackTrace()")
				.endControlFlow()
				.addStatement("return value")
				.returns(ClassName.get(String.class));
				methods.add(toStringBuilder.build());
	}
	
	
	/**
	 * generates an <i>of</i>-method with all fields as arguments. Acts basically like an 
	 * @see AllArgsConstructor	 * generates an <i>of</i>-method with all fields as arguments. Acts basically like an 
	 * 
	 * @param packageName
	 * @param className
	 * @param annotationInfo
	 * @param methods
	 */
	private void createOfWithArguments(String packageName, 
									   String className, 
									   ElementInfo annotationInfo, 
									   List<MethodSpec> methods) {
		// create of method
		MethodSpec.Builder of = MethodSpec.methodBuilder("of")
				.addModifiers(Modifier.PUBLIC, Modifier.STATIC)
				.addStatement(className+" newJsonObect = new "+className+"();")
				.addException(IllegalAccessException.class)
				.addJavadoc(CodeBlock
					    .builder()
					    .add("Creates object with all given values, acts basically as a AllArgsConstructor.\n")
					    .build());
				annotationInfo.fields().stream()
							  .filter(field -> ! isMethodFinalPrivateStatic(field))
							  .forEach(field -> {

						TypeMirror fieldType = field.asType();
						TypeName fieldClass = TypeName.get(fieldType);
						of.addParameter(fieldClass ,field.getSimpleName().toString(), new Modifier[0]);
				});
				annotationInfo.fields().stream()
					  .filter(field -> ! isMethodFinalPrivateStatic(field))
					  .forEach(field -> {
						  
					  String setterName = generateSetterName(annotationInfo, field.getSimpleName().toString());
					  boolean fieldIsMapped = fieldIsAnnotedWith(field, JSONMapped.class);
					  if (!fieldIsMapped) {
					  	  of.addStatement("newJsonObect.$L($L)", setterName, field.getSimpleName().toString());
					  } else {
							TypeMirror fieldTypeMirror = field.asType();
							Element fieldElement = typeUtils.asElement(fieldTypeMirror);
							if (fieldElement instanceof TypeElement) {
								ClassName fieldClassName = ClassName.get((TypeElement) fieldElement); 
								String fcName = annotationInfo.prefix()+fieldClassName.simpleName();
								ClassName mappedFieldClassName = ClassName.get(generatePackageName(fieldClassName, annotationInfo), fcName);
								of.addStatement("newJsonObect.$L($T.of($L))", setterName, 
																			  mappedFieldClassName,
																			  field.getSimpleName().toString());
							}
					  }
			    });
				of.addStatement("return newJsonObect")
				
				.returns(ClassName.get(packageName, className));
				methods.add(of.build());
	}
	
	/**
	 * generates an <i>of</i>-method which takes the annotated class as an argument
	 * and returns an instance of the generated class with copies of all fields
	 * 
	 * @param key
	 * @param packageName
	 * @param className
	 * @param annotationInfo
	 * @param methods
	 */
	private void createOfWithClass(ClassName key, String packageName, String className, ElementInfo annotationInfo, List<MethodSpec> methods) {
		// create of method
		String incomingObjectName = "incoming"+key.simpleName();
		
		MethodSpec.Builder of = MethodSpec.methodBuilder("of")
				.addModifiers(Modifier.PUBLIC, Modifier.STATIC)
				.addParameter(key, incomingObjectName, new Modifier[0])
				
				.addStatement(className +" newJsonObect = new "+className+"()")
				.addException(IllegalAccessException.class)

				.addJavadoc(CodeBlock
					    .builder()
					    .add("Creates object from source class. Since source class has not been compiled at this point,\n")
					    .add("calling the setters on the source would lead to an exception.\n")
					    .add("For this reason the getter call is wrapped by reflection.\n\n")
					    .add("@param $L - the incoming object of type $L to be mapped.", incomingObjectName, key.simpleName())
					    .build());
		
				AtomicInteger fieldCount = new AtomicInteger(0);
				annotationInfo.fields().stream().filter(field -> ! isMethodFinalPrivateStatic(field))
												.forEach(field -> {
						TypeMirror fieldType = field.asType();
						TypeName fieldClass = TypeName.get(fieldType);
						String fieldName = field.getSimpleName().toString();
						String setterName = generateSetterName(annotationInfo, field.getSimpleName().toString());
						String localFieldName = "field"+fieldCount.getAndIncrement();
						boolean fieldIsMapped = fieldIsAnnotedWith(field, JSONMapped.class);
						of
						 	.addStatement("if ($L == null) return null" , incomingObjectName);
						
						if (!fieldIsMapped) {
							of
								  .addStatement("$T $L = $T.deepGetField($L, $S, true)", Field.class,
										  												 localFieldName,
										  												 ReflectionUtil.class, 
										  												 className+".class", 
										  												 fieldName)
						
								  .beginControlFlow("if ($L != null)" , localFieldName)
									.addStatement("newJsonObect.$L(($L)$T.invokeGetterMethod($L, $L))",  
													  setterName,
											   		  fieldClass,
											   		  ReflectionUtil.class, 
											   		  incomingObjectName,
											   		  localFieldName)
								  .endControlFlow();
						} else {
							TypeMirror fieldTypeMirror = field.asType();
							Element fieldElement = typeUtils.asElement(fieldTypeMirror);
							if (fieldElement instanceof TypeElement) {
								ClassName fieldClassName = ClassName.get((TypeElement) fieldElement); 
								String fcName = annotationInfo.prefix()+fieldClassName.simpleName();
								ClassName mappedFieldClassName = ClassName.get(generatePackageName(fieldClassName, annotationInfo), fcName);
								of
								  .addStatement("$T $L = $T.deepGetField($L, $S, true)", Field.class,
										  												 localFieldName,
										  												 ReflectionUtil.class, 
										  												 className+".class", 
										  												 fieldName)
						
								  .beginControlFlow("if ($L != null)" , localFieldName)
									.addStatement("newJsonObect.$L($T.of(($L)$T.invokeGetterMethod($L, $L)))",  
													  setterName,
													  mappedFieldClassName,
											   		  fieldClass,
											   		  ReflectionUtil.class, 
											   		  incomingObjectName,
											   		  localFieldName)
								  .endControlFlow();
							}							
						}
				});
				of.addStatement("return newJsonObect")
				.returns(ClassName.get(packageName, className));
				methods.add(of.build());
	}

	/**
	 * generates a <i>to</i>-method which returns an instance of the annotated class with
	 * copies of all field values. 
	 * 
	 * @param packageName
	 * @param className
	 * @param annotationInfo
	 * @param methods
	 */
	private void createTo(String packageName, String className, ElementInfo annotationInfo, List<MethodSpec> methods) {
		// create of method
		final String objectName = StringUtil.uncapitalise(className);
		final ClassName externalClass = ClassName.get(packageName, className);
		
		MethodSpec.Builder to = MethodSpec.methodBuilder("to")
				.addModifiers(Modifier.PUBLIC)
				.addException(IllegalAccessException.class)
				.addJavadoc(CodeBlock
					    .builder()
					    .add("Recreates original object from json object instance,\n")
					    .add("Calling the setters on the source would lead to an exception and is insecure,\n")
					    .add("because we cannot predict if fluent accessors are being used.\n")
					    .add("For this reason the getter call is wrapped by reflection.\n\n")
					    .add("@return the recreated object instance of $L", objectName)
					    .build())		
				.addStatement("$T $L = new $T()", externalClass, objectName, externalClass);
		
				AtomicInteger fieldCount = new AtomicInteger(0);
				annotationInfo.fields().stream().filter(field -> ! isMethodFinalPrivateStatic(field))
												.forEach(field -> {
						boolean fieldIsMapped = fieldIsAnnotedWith(field, JSONMapped.class);
						String fieldName = field.getSimpleName().toString();
						String localFieldName = "field"+fieldCount.getAndIncrement();
						to
							  .addStatement("$T $L = $T.deepGetField($L, $S, true)", Field.class,
									  												 localFieldName,
									  												 ReflectionUtil.class, 
									  												 className+".class", 
									  												 fieldName)					
							  .beginControlFlow("if ($L != null && $L != null)" , localFieldName, fieldName)
								.addStatement("$T.invokeSetterMethod($L, $L, $L"+(fieldIsMapped?".to()":"")+")",
										   		  ReflectionUtil.class, 
										   		  objectName,
										   		  localFieldName,
										   		  fieldName)
							  .endControlFlow();
				});
				to.addStatement("return $L", objectName)
				.returns(ClassName.get(packageName, className));
				methods.add(to.build());
	}

		
	/**
	 * create field
	 * 
	 * @param field - VariableElement representation of field to be created 
	 * @param fieldClass - TypeName for class field shall be created in.
	 * @param fieldIsMapped - indicates whether or not the given field is annotated
	 * 						  with {@code JSONMapped}. 
	 * @return field specification for the create field.
	 */
	@Override
	public FieldSpec createFieldSpec(VariableElement field, ElementInfo annotationInfo, TypeName fieldClass, boolean fieldIsMapped) {

		FieldSpec fieldspec = null;
		String fieldName = field.getSimpleName().toString();
		if (field.getAnnotation(JSONTransient.class) == null && 
			field.getAnnotation(JsonIgnore.class) == null) {			
			AnnotationSpec.Builder jsonPropertyAnnotationBuilder = AnnotationSpec.builder(JsonProperty.class)
					.addMember("value", StringUtil.quote(StringUtil.camelToSnake(field.getSimpleName().toString()), '"'));
			if(field.getAnnotation(JSONRequired.class) != null) {
				jsonPropertyAnnotationBuilder.addMember("required", "true");
			}
			AnnotationSpec jsonPropertyAnnotation = jsonPropertyAnnotationBuilder.build();
			
			TypeName fieldType = fieldClass;
			if (fieldIsMapped) {
				TypeMirror fieldTypeMirror = field.asType();
				Element fieldElement = typeUtils.asElement(fieldTypeMirror);
				if (fieldElement instanceof TypeElement) {
					ClassName fieldClassName = ClassName.get((TypeElement) fieldElement);
					String fcName = annotationInfo.prefix()+fieldClassName.simpleName();
					fieldType = ClassName.get(generatePackageName(fieldClassName, annotationInfo), fcName);
				}
			}
				
			fieldspec = FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE)
					.addAnnotation(jsonPropertyAnnotation).build();
			
		} else {
			fieldspec = FieldSpec.builder(fieldClass, fieldName, Modifier.PRIVATE)
					.addAnnotation(JsonIgnore.class)
					.build();
			
		} 		
		return fieldspec;
	}

	/**
	 * generates the package name base on the given annotation arguments
	 * 
	 * @param key
	 * @param annotationInfo
	 * @return
	 */
	final protected String generatePackageName(ClassName key, ElementInfo annotationInfo) {
		String packageName = annotationInfo.packageName();
		if (StringUtil.isBlank(packageName)) {
			packageName = key.packageName();
			if (StringUtil.isNotBlank(annotationInfo.subpackageName())) {
				packageName += "." + annotationInfo.subpackageName();
			}
		}
		return packageName;
	}

	/**
	 * Checks for annotation on provided field element.
	 * @param field - Element for the field
	 * @param annotationClazz - Class of the annotation
	 * @return true if present
	 */
	private boolean fieldIsAnnotedWith(final Element field,
									   Class<?> annotationClazz) {
		TypeMirror fieldType = field.asType();
		
		TypeElement fieldClassElement = procEnv.getElementUtils().getTypeElement(ClassName.get(fieldType).toString());
		return (fieldClassElement != null &&
				fieldClassElement.getAnnotationMirrors().stream()
								 .anyMatch(annotation -> annotation.getAnnotationType().toString()
										 						    .equals(JSONMapped.class.getCanonicalName())));
	}
	
}

