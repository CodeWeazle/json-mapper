package net.magiccode.json.generator;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
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
	 * The purpose of this class is to generate JSON annotated Java code using the
	 * JavaPoet framework. See documentation for more details about
	 * <i>JSONMapped</i>
	 * 
	 * @param procEnv  - the processing environment
	 * @param filer    - the filer
	 * @param messager - used to output messages
	 * @param input    - information collected beforehand based on the annotation
	 */
	public JSONClassGenerator(ProcessingEnvironment procEnv, Filer filer, Messager messager,
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

					messager.printMessage(Diagnostic.Kind.NOTE, "Generating " + className);

					String packageName = generatePackageName(key, annotationInfo);

					messager.printMessage(Diagnostic.Kind.NOTE, "annotated class " + key.canonicalName()
							+ ", generated class " + packageName + "." + className);

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
					TypeSpec generatedJSONClass = generateClass(annotationInfo, className, packageName, fields,
							methods);
					JavaFile javaFile = JavaFile.builder(packageName, generatedJSONClass).indent("    ").build();
					javaFile.writeTo(filer);
				} catch (IOException e) {
					messager.printMessage(Diagnostic.Kind.ERROR,
							"Error occured while generating class " + key + ". " + e.getLocalizedMessage());
				}
			});
		}
	}

	/**
	 * creates gields, getters and setters for the given fields.
	 * 
	 * @param annotationInfo - information about the annotation arguments
	 * @param fields         - list of fields to be created.
	 * @param methods        - list of methods to be created
	 */
	private void createFieldsGettersAndSetters(ElementInfo annotationInfo, List<FieldSpec> fields,
			List<MethodSpec> methods) {
		// Generate fields, getters and setters
		annotationInfo.fields().stream().filter(field -> !isMethodFinalPrivateStatic(field)).forEach(field -> {

			TypeMirror fieldType = field.asType();

			TypeName fieldTypeName = TypeName.get(fieldType);
			boolean fieldIsMapped = fieldIsAnnotedWith(field, JSONMapped.class);
			if (fieldIsMapped) {
				TypeMirror fieldTypeMirror = field.asType();
				Element fieldElement = typeUtils.asElement(fieldTypeMirror);
				if (fieldElement instanceof TypeElement) {
					ClassName fieldClassName = ClassName.get((TypeElement) fieldElement);
					String fcName = annotationInfo.prefix() + fieldClassName.simpleName();
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
	 * @param fields         - list of fields to be created.
	 */
	private void createFields(ElementInfo annotationInfo, List<FieldSpec> fields) {
		// Generate fields
		annotationInfo.fields().stream().filter(field -> !isMethodFinalPrivateStatic(field)).forEach(field -> {
			TypeMirror fieldType = field.asType();
			TypeName fieldClass = TypeName.get(fieldType);
			boolean fieldIsMapped = fieldIsAnnotedWith(field, JSONMapped.class);
			messager.printMessage(Diagnostic.Kind.NOTE, "Generating field " + field.getSimpleName().toString());
			fields.add(createFieldSpec(field, annotationInfo, fieldClass, fieldIsMapped));
		});
	}

	/**
	 * Generate the class code with given fields and methods
	 * 
	 * @param annotationInfo - information about the annotation arguments
	 * @param className      - class name of the class to be created
	 * @param packageName    - the package the class to be created shall be located
	 *                       in.
	 * @param fields         - list of fields to be created
	 * @param methods        - list of methods to be created
	 * @return typeSpec - object containing the newly generated class
	 */
	public TypeSpec generateClass(ElementInfo annotationInfo, String className, String packageName,
			List<FieldSpec> fields, List<MethodSpec> methods) {
		DateTimeFormatter pattern = DateTimeFormatter.ofPattern("dd/MM/yyyy");
		TypeSpec.Builder generatedJSONClassBuilder = TypeSpec.classBuilder(className).addModifiers(Modifier.PUBLIC)
				.addJavadoc(CodeBlock.builder()
						.add(packageName + "." + className + " generated by JsonMapper. (@JsonMapper)\n\n")
						.add("@created " + LocalDateTime.now().format(pattern) + "\n").build())
				.addSuperinterface(Serializable.class)
				.addField(FieldSpec.builder(TypeName.LONG, "serialVersionUID", Modifier.FINAL, Modifier.STATIC)
						.initializer("-1L").build())
				.addFields(fields).addMethods(methods)
				.addAnnotation(AnnotationSpec.builder(JSONMappedBy.class)
						.addMember("mappedClass", "$T.class", ClassName.get(annotationInfo.element())).build())
				.addAnnotation(AnnotationSpec.builder(JsonInclude.class)
						.addMember("value", "$T.$L", Include.class, annotationInfo.jsonInclude().name()).build());
		// In case we use lombok, we can just generate some annotations
		if (annotationInfo.useLombok()) {
			generatedJSONClassBuilder.addAnnotation(NoArgsConstructor.class);
			if (fields.size() > 0)
				generatedJSONClassBuilder.addAnnotation(AllArgsConstructor.class);
			generatedJSONClassBuilder.addAnnotation(Getter.class).addAnnotation(Setter.class)
					.addAnnotation(ToString.class).addAnnotation(AnnotationSpec.builder(Accessors.class)
							.addMember("fluent", annotationInfo.fluentAccessors() ? "true" : "false").build());
		}

		// add superclass
		if (annotationInfo.superclass() != null) {
			generatedJSONClassBuilder.superclass(annotationInfo.superclass());
		}

		// add provided interface
		if (annotationInfo.interfaces() != null) {
			annotationInfo.interfaces().stream()
					// filter existing classes
					.forEach(intf -> {
						generatedJSONClassBuilder.addSuperinterface(intf);
					});
		}
		TypeSpec generatedJSONClass = generatedJSONClassBuilder.build();

		messager.printMessage(Diagnostic.Kind.NOTE, "Generated " + className);

		return generatedJSONClass;
	}

	/**
	 * generate toString method
	 * 
	 * @param methods - list of methods to be creatd.
	 */
	private void createToJSONString(List<MethodSpec> methods) {
		// create toJSONString method
		MethodSpec.Builder toStringBuilder = MethodSpec.methodBuilder("toJSONString").addModifiers(Modifier.PUBLIC)
				.addJavadoc(CodeBlock.builder().add("provides a formatted JSON string with all fields\n")
						.add("and their current values.\n").build())
				.addStatement("$T mapper = new $T()", ObjectMapper.class, ObjectMapper.class)
				.addStatement("String value = this.getClass().getName()").beginControlFlow("try")
				.addStatement("value += mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this)")
				.endControlFlow().beginControlFlow("catch ($T e)", JsonProcessingException.class)
				.addStatement("e.printStackTrace()").endControlFlow().addStatement("return value")
				.returns(ClassName.get(String.class));
		methods.add(toStringBuilder.build());
	}

	/**
	 * generates an <i>of</i>-method with all fields as arguments. Acts basically
	 * like an
	 * 
	 * @see AllArgsConstructor * generates an <i>of</i>-method with all fields as
	 *      arguments. Acts basically like an
	 * 
	 * @param packageName
	 * @param className
	 * @param annotationInfo
	 * @param methods
	 */
	private void createOfWithArguments(String packageName, String className, ElementInfo annotationInfo,
			List<MethodSpec> methods) {
		// create of method
		String incomingObjectName = "incoming"+className;
		
		MethodSpec.Builder of = MethodSpec.methodBuilder("of").addModifiers(Modifier.PUBLIC, Modifier.STATIC)
				.addStatement(className + " newJsonObect = new " + className + "();")
				.addException(IllegalAccessException.class)
				.addJavadoc(CodeBlock.builder()
						.add("Creates object with all given values, acts basically as a AllArgsConstructor.\n")
						.build());

		AtomicInteger fieldCount = new AtomicInteger(0);
		annotationInfo.fields().stream().filter(field -> !isMethodFinalPrivateStatic(field)).forEach(field -> {

			TypeMirror fieldType = field.asType();
			String fieldName = field.getSimpleName().toString();
			String localFieldName = "field"+fieldCount.getAndIncrement();
			TypeName fieldClass = TypeName.get(fieldType);
			of.addParameter(fieldClass, field.getSimpleName().toString(), new Modifier[0]);

			String setterName = generateSetterName(annotationInfo, field.getSimpleName().toString());
			boolean fieldIsMapped = fieldIsAnnotedWith(field, JSONMapped.class);
			if (!fieldIsMapped) {
								
				if (fieldType.getKind() == TypeKind.DECLARED) {
					List<TypeName> typeArguments = obtainTypeArguments(fieldType);
					List<TypeName> types = collectTypes(annotationInfo, typeArguments);
					
					TypeMirror collectionType = getElementUtils().getTypeElement("java.util.Collection").asType();
					TypeMirror mapType = getElementUtils().getTypeElement("java.util.Map").asType();
					TypeMirror setType = getElementUtils().getTypeElement("java.util.Set").asType();

					if (typeArguments != null && typeArguments.size()>0) {
						Element[] argumentElement = new Element[typeArguments.size()];
						boolean[] argumentIsMapped = new boolean[typeArguments.size()];
						
						for (int i=0; i < typeArguments.size(); i++) {
//							argumentElement[i] = getElementUtils().getTypeElement(getTypeUtils().erasure(typeArguments.get(i)).toString());
							argumentElement[i] = getElementUtils().getTypeElement(typeArguments.get(i).toString());							
							argumentIsMapped[i] = fieldIsAnnotedWith(argumentElement[i], JSONMapped.class);
						}
						// List & Set
						if (argumentIsMapped[0] && fieldType != null && 
							(getTypeUtils().isAssignable(
								getTypeUtils().erasure(fieldType), 
								getTypeUtils().erasure(collectionType )) || 
							 getTypeUtils().isAssignable(
									getTypeUtils().erasure(fieldType), 
									getTypeUtils().erasure(setType )))) {
							generateMappingStatementForOfWithArguments(methods, incomingObjectName, of, fieldClass, fieldName, setterName, localFieldName, typeArguments, types);
						} 				
					} else {
						of.addStatement("newJsonObect.$L($L)", setterName, field.getSimpleName().toString());				
					}
				} else {		of.addStatement("newJsonObect.$L($L)", setterName, field.getSimpleName().toString());  }
			} else {
				TypeMirror fieldTypeMirror = field.asType();
				Element fieldElement = typeUtils.asElement(fieldTypeMirror);
				if (fieldElement instanceof TypeElement) {
					ClassName fieldClassName = ClassName.get((TypeElement) fieldElement);
					String fcName = annotationInfo.prefix() + fieldClassName.simpleName();
					ClassName mappedFieldClassName = ClassName.get(generatePackageName(fieldClassName, annotationInfo),
							fcName);
					of.addStatement("newJsonObect.$L($T.of($L))", setterName, mappedFieldClassName,
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
				.addStatement("if ($L == null) return null" , incomingObjectName)			
				.addStatement("$L newJsonObect = new $L()", className, className)
				.addException(IllegalAccessException.class)

				.addJavadoc(CodeBlock
					    .builder()
					    .add("Creates object from source class. Since source class has not been compiled at this point,\n")
					    .add("calling the setters on the source would lead to an exception.\n")
					    .add("For this reason the getter call is wrapped by reflection.\n\n")
					    .add("@param $L - the incoming object of type $L to be mapped.", incomingObjectName, key.simpleName())
					    .build());
		
				AtomicInteger fieldCount = new AtomicInteger(0);
				AtomicBoolean needsSuppressWarnings = new AtomicBoolean(false); 
				annotationInfo.fields().stream().filter(field -> ! isMethodFinalPrivateStatic(field))
												.forEach(field -> {
													
					TypeMirror fieldType = field.asType();
					
					TypeName fieldClass = TypeName.get(fieldType);
					String fieldName = field.getSimpleName().toString();
					String setterName = generateSetterName(annotationInfo, field.getSimpleName().toString());
					String localFieldName = "field"+fieldCount.getAndIncrement();
					boolean fieldIsMapped = fieldIsAnnotedWith(field, JSONMapped.class);
						
					if (!fieldIsMapped) {
						createStatementForMappedFieldOf(className, annotationInfo, methods, incomingObjectName, of,
								needsSuppressWarnings, fieldType, fieldClass, fieldName, setterName, localFieldName);
					} else {
						createStatementForUnmappedFieldOf(className, annotationInfo, incomingObjectName, of, field,
								fieldClass, fieldName, setterName, localFieldName);							
					}
				});
				of.addStatement("return newJsonObect")
				  .returns(ClassName.get(packageName, className));
				if (needsSuppressWarnings.get() == true) {
					// create @SuppressWarning("unchecked") annotation
					AnnotationSpec suppressWarningsAnnotation =  AnnotationSpec.builder(SuppressWarnings.class)
																				.addMember("value", "$S", "unchecked")
																				.build();
					of.addAnnotation(suppressWarningsAnnotation);
				}
				methods.add(of.build());
	}

	/**
	 * @param className
	 * @param annotationInfo
	 * @param incomingObjectName
	 * @param of
	 * @param field
	 * @param fieldClass
	 * @param fieldName
	 * @param setterName
	 * @param localFieldName
	 */
	private void createStatementForUnmappedFieldOf(String className, ElementInfo annotationInfo, String incomingObjectName,
			MethodSpec.Builder of, VariableElement field, TypeName fieldClass, String fieldName, String setterName,
			String localFieldName) {
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

	/**
	 * @param className
	 * @param annotationInfo
	 * @param methods
	 * @param incomingObjectName
	 * @param of
	 * @param needsSuppressWarnings
	 * @param fieldType
	 * @param fieldClass
	 * @param fieldName
	 * @param setterName
	 * @param localFieldName
	 */
	private void createStatementForMappedFieldOf(String className, ElementInfo annotationInfo, List<MethodSpec> methods,
			String incomingObjectName, MethodSpec.Builder of, AtomicBoolean needsSuppressWarnings, TypeMirror fieldType,
			TypeName fieldClass, String fieldName, String setterName, String localFieldName) {
		of
		.addStatement("$T $L = $T.deepGetField($L, $S, true)", Field.class,
				localFieldName,
				ReflectionUtil.class, 
				className+".class", 
				fieldName)
		.beginControlFlow("if ($L != null)" , localFieldName);
		// add suppresswarnings if necessary
		if (fieldType.getKind() == TypeKind.DECLARED) {
			List<TypeName> typeArguments = obtainTypeArguments(fieldType);							
			List<TypeName> types = collectTypes(annotationInfo, typeArguments);
			
			TypeMirror collectionType = getElementUtils().getTypeElement("java.util.Collection").asType();
			TypeMirror mapType = getElementUtils().getTypeElement("java.util.Map").asType();
			TypeMirror setType = getElementUtils().getTypeElement("java.util.Set").asType();

			if (typeArguments != null && typeArguments.size()>0) {
				Element[] argumentElement = new Element[typeArguments.size()];
				boolean[] argumentIsMapped = new boolean[typeArguments.size()];
				
				for (int i=0; i < typeArguments.size(); i++) {
					argumentElement[i] = getElementUtils().getTypeElement(typeArguments.get(i).toString());
					argumentIsMapped[i] = fieldIsAnnotedWith(argumentElement[i], JSONMapped.class);
					needsSuppressWarnings.set(true);
				}
				// List & Set
				if (argumentIsMapped[0] && fieldType != null && 
					(getTypeUtils().isAssignable(
						getTypeUtils().erasure(fieldType), 
						getTypeUtils().erasure(collectionType )) || 
					 getTypeUtils().isAssignable(
							getTypeUtils().erasure(fieldType), 
							getTypeUtils().erasure(setType )))) {
					generateMappingStatementOf(methods, incomingObjectName, of, fieldClass, fieldName, setterName, localFieldName, typeArguments, types);
				} 
				else if (argumentElement.length > 1 && (argumentIsMapped[0] || argumentIsMapped[1]) &&
						fieldType != null && getTypeUtils().isAssignable(
												getTypeUtils().erasure(fieldType), 
												getTypeUtils().erasure(mapType ))) {
					
//									TypeName[] mappedTypeNames = {fieldIsAnnotedWith(field, JSONMapped.class)? 
//																		getMappedTypeName(annotationInfo, types.get(0)): 
//																		types.get(0),
//																  fieldIsAnnotedWith(field, JSONMapped.class)? 
//																		getMappedTypeName(annotationInfo, types.get(1)): 
//																		types.get(1)};
						
						
//										of
//										.addStatement("newJsonObect.$L( ($L)$T.invokeGetterMethod($L, $L).stream().map(e -> {")
//										
//										
//										
//										.addStatement(types.get(0)=".of("+fieldName+")}).collect($T.toList()) )",  
//													setterName,
//										   		  	fieldClass,
//										   		  	ReflectionUtil.class, 
//										   		  	incomingObjectName,
//										   		  	localFieldName,
//										   		  	Collectors.class)
						
						
//										.endControlFlow()
//										.beginControlFlow("catch($T eIllArg)", IllegalArgumentException.class)
//										.addStatement("eIllArg.printStackTrace()")
//										.endControlFlow();
					
				} else { // typeArguments present but not mapped
					of
					.addStatement("newJsonObect.$L(($L)$T.invokeGetterMethod($L, $L))",  
									  setterName,
							   		  fieldClass,
							   		  ReflectionUtil.class, 
							   		  incomingObjectName,
							   		  localFieldName);
				}									
			} else {
				of
				.addStatement("newJsonObect.$L(($L)$T.invokeGetterMethod($L, $L))",  
								  setterName,
						   		  fieldClass,
						   		  ReflectionUtil.class, 
						   		  incomingObjectName,
						   		  localFieldName);
			}
		} else {
			of
			.addStatement("newJsonObect.$L(($L)$T.invokeGetterMethod($L, $L))",  
							  setterName,
					   		  fieldClass,
					   		  ReflectionUtil.class, 
					   		  incomingObjectName,
					   		  localFieldName);
		}
		of.endControlFlow();
	}

	/**
	 * @param methods
	 * @param incomingObjectName
	 * @param of
	 * @param fieldClass
	 * @param fieldName
	 * @param setterName
	 * @param localFieldName
	 * @param typeArguments
	 * @param types
	 */
	private void generateMappingStatementOf(List<MethodSpec> methods, String incomingObjectName, MethodSpec.Builder of,
			TypeName fieldClass, String fieldName, String setterName, String localFieldName,
			List<TypeName> typeArguments, List<TypeName> types) {
		String methodName = createListeElementMappingOf(methods, fieldName, typeArguments, types);
		of										
		.addStatement("newJsonObect.$L((($L)$T.invokeGetterMethod($L, $L)).stream().map(e -> $L(e)).collect($T.toList()))",
													 setterName,
										   		  	 fieldClass,
										   		  	 ReflectionUtil.class, 
										   		  	 incomingObjectName,
										   		  	 localFieldName,
										   		  	 methodName,
										   		  	 Collectors.class);
	}

	/**
	 * @param methods
	 * @param incomingObjectName
	 * @param of
	 * @param fieldClass
	 * @param fieldName
	 * @param setterName
	 * @param localFieldName
	 * @param typeArguments
	 * @param types
	 */
	private void generateMappingStatementForOfWithArguments(final List<MethodSpec> methods, 
															String incomingObjectName, 
															final MethodSpec.Builder of,
															final TypeName fieldClass, 
															String fieldName, 
															String setterName, 
															String localFieldName,
			List<TypeName> typeArguments, List<TypeName> types) {
		String methodName = createListeElementMappingOf(methods, fieldName, typeArguments, types);
		of			
		.addStatement("newJsonObect.$L($L.stream().map(e -> $L(e)).collect($T.toList()))",
													 setterName,
													 fieldName,
										   		  	 methodName,
										   		  	 Collectors.class);
	}

	
	/**
	 * create separate method to avoid try/catch within stream processing
	 * 
	 * @param methods
	 * @param fieldName
	 * @param typeArguments
	 * @param types
	 * @return
	 */
	private String createListeElementMappingOf(final List<MethodSpec> methods,
											   String fieldName,
											   final List<TypeName> typeArguments, 
											   final List<TypeName> types) {
		String methodName = "map"+StringUtil.capitalise(fieldName)+"To"+(
				((ClassName)types.get(0)).simpleName());
		MethodSpec.Builder mapMethodBuilder = MethodSpec.methodBuilder(methodName)
				.addJavadoc(CodeBlock
					    .builder()
					    .add("Method to map field $L into instance of a generated class.\n", fieldName)
					    .add("@param methods - List of methods to be created\n")
					    .add("@param fieldName - Name of the field of which the content is to be mapped.\n")
					    .add("@param typeArguments - {@code TypeMirror}s of the arguments of the field to be mapped.\n")
					    .add("@param typeArguments - {@code TypeName}s of the arguments of the field to be mapped.\n")
					    .add("@return name of the method to be generated.\n")
					    .build())
				.addModifiers(Modifier.PRIVATE, Modifier.STATIC);
		
				mapMethodBuilder.addParameter(typeArguments.get(0), "e", Modifier.FINAL);
								
				mapMethodBuilder
					.returns(types.get(0))
					.addStatement("$T result = null", types.get(0))
					.beginControlFlow("try")
						.addStatement("result = $T.of(e)", types.get(0))
					.endControlFlow()
					.beginControlFlow("catch($T eIllAcc)", IllegalAccessException.class)
						.addStatement("eIllAcc.printStackTrace()")
					.endControlFlow()
					.addStatement("return result");
				MethodSpec mapMethod = mapMethodBuilder.build();
		if (! methods.contains(mapMethod))
				methods.add(mapMethod);
		return methodName;
	}
	
	

	/**
	 * create separate method to avoid try/catch within stream processing
	 * 
	 * @param methods
	 * @param fieldName
	 * @param typeArguments
	 * @param types
	 * @return
	 */
	private String createListeElementMappingTo(final List<MethodSpec> methods, 
											   String fieldName,
											   final List<TypeName> typeArguments, 
											   final List<TypeName> types) {
		String methodName = "map"+StringUtil.capitalise(fieldName)+"To"+(
				((ClassName)types.get(0)).simpleName());
		MethodSpec mapMethod = MethodSpec.methodBuilder(methodName)
				.addJavadoc(CodeBlock
					    .builder()
					    .add("Method to map field $L back into instance of the annotated class.\n", fieldName)
					    .add("@param methods - List of methods to be created\n")
					    .add("@param fieldName - Name of the field of which the content is to be mapped.\n")
					    .add("@param typeArguments - {@code TypeMirror}s of the arguments of the field to be mapped.\n")
					    .add("@param typeArguments - {@code TypeName}s of the arguments of the field to be mapped.\n")
					    .add("@return name of the method to be generated.\n")
					    .build())
				.addModifiers(Modifier.PRIVATE, Modifier.STATIC)
				.addParameter(types.get(0), "e", Modifier.FINAL)
				.returns(typeArguments.get(0))
				.addStatement("$T result = null", typeArguments.get(0))
				.beginControlFlow("try")
					.addStatement("result = e.to()")
				.endControlFlow()
				.beginControlFlow("catch($T eIllAcc)", IllegalAccessException.class)
					.addStatement("eIllAcc.printStackTrace()")
				.endControlFlow()
				.addStatement("return result")
				.build();
		if (! methods.contains(mapMethod))
				methods.add(mapMethod);
		return methodName;
	}

	/**
	 * @param annotationInfo
	 * @param fieldElementClass
	 * @return
	 */
	private TypeName getMappedTypeName(final ElementInfo annotationInfo, ClassName fieldElementClass) {
		String fcName = annotationInfo.prefix() + fieldElementClass.simpleName();
		ClassName mappedFieldClassName = ClassName.get(generatePackageName(fieldElementClass, annotationInfo), fcName);
		return mappedFieldClassName;
	}

	/**
	 * generates a <i>to</i>-method which returns an instance of the annotated class
	 * with copies of all field values.
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

		MethodSpec.Builder to = MethodSpec.methodBuilder("to").addModifiers(Modifier.PUBLIC)
				.addException(IllegalAccessException.class)
				.addJavadoc(CodeBlock.builder().add("Recreates original object from json object instance,\n")
						.add("Calling the setters on the source would lead to an exception and is insecure,\n")
						.add("because we cannot predict if fluent accessors are being used.\n")
						.add("For this reason the getter call is wrapped by reflection.\n\n")
						.add("@return the recreated object instance of $L", objectName).build())
				.addStatement("$T $L = new $T()", externalClass, objectName, externalClass);

		AtomicInteger fieldCount = new AtomicInteger(0);
		annotationInfo.fields().stream().filter(field -> !isMethodFinalPrivateStatic(field)).forEach(field -> {
			boolean fieldIsMapped = fieldIsAnnotedWith(field, JSONMapped.class);
			String fieldName = field.getSimpleName().toString();
			String localFieldName = "field" + fieldCount.getAndIncrement();

			TypeMirror fieldType = field.asType();
			TypeName fieldClass = TypeName.get(fieldType);

			to.addStatement("$T $L = $T.deepGetField($L, $S, true)", Field.class, localFieldName, ReflectionUtil.class,
					className + ".class", fieldName);

			if (field.asType().getKind().isPrimitive()) {
				to.beginControlFlow("if ($L != null)", localFieldName);

			} else {
				to.beginControlFlow("if ($L != null && $L != null)", localFieldName, fieldName);
			}
			
			
			//------------------------------------
			
			if (fieldType.getKind() == TypeKind.DECLARED) {
				List<TypeName> typeArguments = obtainTypeArguments(fieldType);							
				List<TypeName> types = collectTypes(annotationInfo, typeArguments);

				if (typeArguments != null && typeArguments.size()>0) {
					Element[] argumentElement = new Element[typeArguments.size()];
					boolean[] argumentIsMapped = new boolean[typeArguments.size()];
					
					for (int i=0; i < typeArguments.size(); i++) {
						argumentElement[i] = getElementUtils().getTypeElement(typeArguments.get(i).toString());
						argumentIsMapped[i] = fieldIsAnnotedWith(argumentElement[i], JSONMapped.class);
	//					needsSuppressWarnings.set(true);
					}
					
					
					TypeMirror collectionType = getElementUtils().getTypeElement("java.util.Collection").asType();
					TypeMirror mapType = getElementUtils().getTypeElement("java.util.Map").asType();
					TypeMirror setType = getElementUtils().getTypeElement("java.util.Set").asType();
	
					// List & Set
					if (argumentIsMapped[0] && fieldType != null && 
						(getTypeUtils().isAssignable(
							getTypeUtils().erasure(fieldType), 
							getTypeUtils().erasure(collectionType )) || 
						 getTypeUtils().isAssignable(
							getTypeUtils().erasure(fieldType), 
							getTypeUtils().erasure(setType)))) {
						
						generateListTypeMappingStatementTo(methods,
														   objectName, 
														   to,
														   fieldClass, 
														   fieldName, 
														   localFieldName,
														   typeArguments, 
														   types, 
														   fieldIsMapped); 
					} else {
						createStatementForUnmappedFieldTo(objectName, to, fieldIsMapped, fieldName, localFieldName);
					}
				} else {
					createStatementForUnmappedFieldTo(objectName, to, fieldIsMapped, fieldName, localFieldName);
				}
			} else {
			//------------------------------------			
				createStatementForUnmappedFieldTo(objectName, to, fieldIsMapped, fieldName, localFieldName);
			}
			to.endControlFlow();
		});
		to.addStatement("return $L", objectName).returns(ClassName.get(packageName, className));
		methods.add(to.build());
	}

	
	
	/**
	 * @param methods
	 * @param incomingObjectName
	 * @param of
	 * @param fieldClass
	 * @param fieldName
	 * @param setterName
	 * @param localFieldName
	 * @param typeArguments
	 * @param types
	 */
	private void generateListTypeMappingStatementTo(final List<MethodSpec> methods, 
											String objectName, 
											MethodSpec.Builder to,
											final TypeName destinationFieldClass, 
											String classFieldName, 
											String localFieldName,
											final List<TypeName> destinationTypes, 
											final List<TypeName> sourceTypes, 
											boolean fieldIsMapped) {
		
		String methodName = createListeElementMappingTo(methods, classFieldName, destinationTypes, sourceTypes);
//		int i=0;
		to.addStatement("$T.invokeSetterMethod($L, $L, $L.stream().map(e -> $L(e)).collect($T.toList()))",
					ReflectionUtil.class, 
					objectName,
					localFieldName,
					classFieldName,
					methodName,
					Collectors.class);
	}	
	/**
	 * @param objectName
	 * @param to
	 * @param fieldIsMapped
	 * @param fieldName
	 * @param localFieldName
	 */
	private void createStatementForUnmappedFieldTo(final String objectName, 
												   final MethodSpec.Builder to, 
												   boolean fieldIsMapped, 
												   String fieldName,
												   String localFieldName) {
			to.addStatement("$T.invokeSetterMethod($L, $L, $L" + (fieldIsMapped ? ".to()" : "") + ")",
							ReflectionUtil.class, objectName, localFieldName, fieldName);
	}

	/**
	 * create field
	 * 
	 * @param field         - VariableElement representation of field to be created
	 * @param fieldClass    - TypeName for class field shall be created in.
	 * @param fieldIsMapped - indicates whether or not the given field is annotated
	 *                      with {@code JSONMapped}.
	 * @return field specification for the create field.
	 */
	@Override
	public FieldSpec createFieldSpec(VariableElement field, ElementInfo annotationInfo, TypeName fieldClass,
			boolean fieldIsMapped) {

		FieldSpec fieldspec = null;
		String fieldName = field.getSimpleName().toString();
		if (field.getAnnotation(JSONTransient.class) == null && field.getAnnotation(JsonIgnore.class) == null) {
			AnnotationSpec.Builder jsonPropertyAnnotationBuilder = AnnotationSpec.builder(JsonProperty.class).addMember(
					"value", StringUtil.quote(StringUtil.camelToSnake(field.getSimpleName().toString()), '"'));
			if (field.getAnnotation(JSONRequired.class) != null) {
				jsonPropertyAnnotationBuilder.addMember("required", "true");
			}
			AnnotationSpec jsonPropertyAnnotation = jsonPropertyAnnotationBuilder.build();
			TypeMirror type = field.asType();

			TypeName fieldType = fieldClass;
			if (fieldIsMapped) {
				TypeMirror fieldTypeMirror = field.asType();
				Element fieldElement = typeUtils.asElement(fieldTypeMirror);
				if (fieldElement instanceof TypeElement) {
					ClassName fieldClassName = ClassName.get((TypeElement) fieldElement);
					String fcName = annotationInfo.prefix() + fieldClassName.simpleName();
					fieldType = ClassName.get(generatePackageName(fieldClassName, annotationInfo), fcName);
				}
			}
			fieldType = checkFieldTypeForCollections(annotationInfo, type, fieldType);

			fieldspec = FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE).addAnnotation(jsonPropertyAnnotation)
					.build();

		} else {
			fieldspec = FieldSpec.builder(fieldClass, fieldName, Modifier.PRIVATE).addAnnotation(JsonIgnore.class)
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

}
