/**
 * json-mapper
 * 
 * Published under Apache-2.0 license (https://github.com/CodeWeazle/json-mapper/blob/main/LICENSE)
 * 
 * Code: https://github.com/CodeWeazle/json-mapper
 * 
 * @author CodeWeazle (2023)
 * 
 * Filename: AbstractClassGenerator.java
 */
package net.magiccode.json.generator;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
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
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

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
import net.magiccode.json.util.ReflectionUtil;
import net.magiccode.json.util.StringUtil;

// 
/**
 * Abstract class for dto generation
 */
public abstract class AbstractClassGenerator implements ClassGenerator {

	protected Map<ClassName, List<ElementInfo>> input;
	
	protected Filer filer;
	protected Messager messager;
	protected ProcessingEnvironment procEnv;
	protected final Types typeUtils;

	/**
	 * The purpose of this class is to generate Java code using the
	 * JavaPoet framework. See documentation for more details about
	 * <i>JSONMapped</i>
	 * 
	 * @param procEnv  - the processing environment
	 * @param filer    - the filer
	 * @param messager - used to output messages
	 * @param input    - information collected beforehand based on the annotation
	 */
	public AbstractClassGenerator(ProcessingEnvironment procEnv, Filer filer, Messager messager,
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
					Map<String, MethodSpec> methods = new HashMap<>();

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
					createSpecificFieldsAndMethods(key, packageName, className, annotationInfo, fields, methods);

					// create to method
					String sourcePackageName = ClassName.get(annotationInfo.element()).packageName();
					String sourceClassName = ClassName.get(annotationInfo.element()).simpleName();
					createTo(sourcePackageName, sourceClassName, annotationInfo, methods);

					// generate and write class
					TypeSpec generatedClass = generateClass(annotationInfo, className, packageName, fields, methods);
					JavaFile javaFile = JavaFile.builder(packageName, generatedClass).indent("    ").build();
					javaFile.writeTo(filer);
				} catch (IOException e) {
					messager.printMessage(Diagnostic.Kind.ERROR,
							"Error occured while generating class " + key + ". " + e.getLocalizedMessage());
				}
			});
		}
	}

	
	/**
	 * to be implemented by extending classes to add fields and methods specific to the type 
	 * of mapper being created 
	 * 
	 * @param incomingObjectClass - the annotated class 
	 * @param packageName - name of the package of the class which is being create by this method belongs to.
	 * @param className - name of the class which is being create by this method belongs to.
	 * @param annotationInfo - {@code ElementInfo} instance of the annotated class
	 * @param methods - {@code Map} containing {@code MethodSpec} instances for the methods to be generated contained in the class which is being processed 
	 * @param fields - {@code List} of {@code FieldSpec} instance for the fields to be generated in the class which is being processed
	 */
	abstract public void createSpecificFieldsAndMethods(final ClassName incomingObjectClass, 
												 String packageName, 
												 String className, 
												 final ElementInfo annotationInfo, 
												 final List<FieldSpec> fields, 
												 final Map<String, MethodSpec> methods);
	
	/**
	 * creates XXXMappedBy annotation for generated class. Needs to be implemented indifidually.
	 * 
	 * @param annotationInfo - {@code ElementInfo} instance of the annotated class
	 * @return an instance of an {@code AnnotationSpec} for the MappedBy annotation in the generated class
	 */
	abstract public AnnotationSpec createMappedByAnnotation(ElementInfo annotationInfo);
	
	/**
	 * allows to add type specific annotations to the generated class
	 * 
	 * @param annotationInfo - {@code ElementInfo} instance of the annotated class
	 * @return a list of {@code AnnotationSpec} instances for annotations to be generated for the class being processed.
	 */
	abstract public List<AnnotationSpec>  getAdditionalAnnotationsForClass(final ElementInfo annotationInfo);
	
	/**
	 * creates gields, getters and setters for the given fields.
	 * 
	 * @param annotationInfo - information about the annotation arguments
	 * @param fields         - list of fields to be created.
	 * @param methods        - list of methods to be created
	 */
	protected void createFieldsGettersAndSetters(ElementInfo annotationInfo, List<FieldSpec> fields,
			Map<String, MethodSpec> methods) {
		// Generate fields, getters and setters
		annotationInfo.fields().stream().filter(field -> !isMethodFinalPrivateStatic(field)).forEach(field -> {

			TypeMirror fieldType = field.asType();
			TypeName fieldTypeName = TypeName.get(fieldType);
			boolean fieldIsMapped = fieldIsMapped(field);
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
			createGetterMethodSpec(field, annotationInfo, fieldTypeName, methods);
			createSetterMethodSpec(field, annotationInfo, fieldTypeName, methods);
		});
	}

	/**
	 * create field only. This is useful for useLombok=true cases.
	 * 
	 * @param annotationInfo - information about the annotation arguments
	 * @param fields         - list of fields to be created.
	 */
	protected void createFields(ElementInfo annotationInfo, List<FieldSpec> fields) {
		// Generate fields
		annotationInfo.fields().stream().filter(field -> !isMethodFinalPrivateStatic(field)).forEach(field -> {
			TypeMirror fieldType = field.asType();
			TypeName fieldClass = TypeName.get(fieldType);
			boolean fieldIsMapped = fieldIsMapped(field);
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
			List<FieldSpec> fields, Map<String, MethodSpec> methods) {
		DateTimeFormatter pattern = DateTimeFormatter.ofPattern("dd/MM/yyyy");
		TypeSpec.Builder generateClassBuilder = TypeSpec.classBuilder(className).addModifiers(Modifier.PUBLIC)
				.addJavadoc(CodeBlock.builder()
						// TODO: generate correct comment
						.add(packageName + "." + className + " generated by JsonMapper. (@JsonMapper)\n\n")
						.add("@created " + LocalDateTime.now().format(pattern) + "\n").build())
				.addSuperinterface(Serializable.class)
				.addField(FieldSpec.builder(TypeName.LONG, "serialVersionUID", Modifier.FINAL, Modifier.STATIC)
						.initializer("-1L").build())
				.addFields(fields).addMethods(methods.values())
				
				.addAnnotation(createMappedByAnnotation(annotationInfo));
				List<AnnotationSpec> additionalAnnotations = getAdditionalAnnotationsForClass(annotationInfo);
				if (additionalAnnotations != null && additionalAnnotations.size()>0) {
					additionalAnnotations.stream().forEach(annotationSpec -> generateClassBuilder.addAnnotation(annotationSpec));
				}
		// In case we use lombok, we can just generate some annotations
		if (annotationInfo.useLombok()) {
			generateClassBuilder.addAnnotation(NoArgsConstructor.class);
			if (fields.size() > 0)
				generateClassBuilder.addAnnotation(AllArgsConstructor.class);
			generateClassBuilder.addAnnotation(Getter.class).addAnnotation(Setter.class)
					.addAnnotation(ToString.class).addAnnotation(AnnotationSpec.builder(Accessors.class)
							.addMember("fluent", annotationInfo.fluentAccessors() ? "true" : "false").build());
		}

		// add superclass
		if (annotationInfo.superclass() != null) {
			generateClassBuilder.superclass(annotationInfo.superclass());
		}

		// add provided interface
		if (annotationInfo.interfaces() != null) {
			annotationInfo.interfaces().stream()
					// filter existing classes
					.forEach(intf -> {
						generateClassBuilder.addSuperinterface(intf);
					});
		}
		TypeSpec generatedClssType = generateClassBuilder.build();

		messager.printMessage(Diagnostic.Kind.NOTE, "Generated " + className);

		return generatedClssType;
	}

	/**
	 * generates an <i>of</i>-method with all fields as arguments. Acts basically
	 * like an
	 * 
	 * @see AllArgsConstructor * generates an <i>of</i>-method with all fields as
	 *      arguments. Acts basically like an
	 * 
	 * @param packageName - name of the package of the class which is being create by this method belongs to.
	 * @param className - name of the class which is being create by this method belongs to.
	 * @param annotationInfo - {@code ElementInfo} instance of the annotated class
	 * @param methods - {@code Map} of methods to be generated for the class which is being processed 
	 */
	private void createOfWithArguments(String packageName, String className, ElementInfo annotationInfo,
			Map<String, MethodSpec> methods) {

		// create of method
		MethodSpec.Builder of = MethodSpec.methodBuilder("of").addModifiers(Modifier.PUBLIC, Modifier.STATIC)
				.addStatement(className + " newMappedObject = new " + className + "();")
				.addException(IllegalAccessException.class)
				.addJavadoc(CodeBlock.builder()
						.add("Creates object with all given values, acts basically as a AllArgsConstructor.\n")
						.build());

		annotationInfo.fields().stream().filter(field -> !isMethodFinalPrivateStatic(field)).forEach(field -> {

			TypeMirror fieldType = field.asType();
			String fieldName = field.getSimpleName().toString();
			TypeName fieldClass = TypeName.get(fieldType);
			of.addParameter(fieldClass, field.getSimpleName().toString(), new Modifier[0]);

			String setterName = generateSetterName(annotationInfo, field.getSimpleName().toString());
			boolean fieldIsMapped = fieldIsMapped(field);
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
							argumentElement[i] = getElementUtils().getTypeElement(typeArguments.get(i).toString());							
							argumentIsMapped[i] = fieldIsMapped(argumentElement[i]);
						}
						// List & Set
						if (argumentIsMapped[0] && fieldType != null && 
							(getTypeUtils().isAssignable(
								getTypeUtils().erasure(fieldType), 
								getTypeUtils().erasure(collectionType )) || 
							 getTypeUtils().isAssignable(
									getTypeUtils().erasure(fieldType), 
									getTypeUtils().erasure(setType )))) {
							generateMappingStatementForCollectionForOfWithArguments(methods, 
																					 of, 
																					 fieldName, 
																					 setterName, 
																					 typeArguments, types);
						// Maps
						} else if (argumentElement.length > 1 && 
								  (argumentIsMapped[0] || argumentIsMapped[1]) &&
								  fieldType != null && getTypeUtils().isAssignable(
										getTypeUtils().erasure(fieldType), 
										getTypeUtils().erasure(mapType ))) {							
							 generateMappingStatementForMapForOfWithArguments(methods, 
																			  of,
																			  fieldName, 
																			  setterName, 
																			  typeArguments, 
																			  types);
							
							
						}			
					} else {
						of.addStatement("newMappedObject.$L($L)", setterName, field.getSimpleName().toString());				
					}
				} else {		of.addStatement("newMappedObject.$L($L)", setterName, field.getSimpleName().toString());  }
			} else {
				TypeMirror fieldTypeMirror = field.asType();
				Element fieldElement = typeUtils.asElement(fieldTypeMirror);
				if (fieldElement instanceof TypeElement) {
					ClassName fieldClassName = ClassName.get((TypeElement) fieldElement);
					String fcName = annotationInfo.prefix() + fieldClassName.simpleName();
					ClassName mappedFieldClassName = ClassName.get(generatePackageName(fieldClassName, annotationInfo),
							fcName);
					of.addStatement("newMappedObject.$L($T.of($L))", setterName, mappedFieldClassName,
							field.getSimpleName().toString());
				}
			}
		});
		of.addStatement("return newMappedObject")

				.returns(ClassName.get(packageName, className));
		methods.put("ofWithArguments", of.build());
	}

	/**
	 * generates an <i>of</i>-method which takes the annotated class as an argument
	 * and returns an instance of the generated class with copies of all fields
	 * 
	 * @param incomingObjectClass - {@code ClassName} object of the {annotated classe.
	 * @param packageName - name of the package of the class which is being create by this method belongs to.
	 * @param className - name of the class which is being create by this method belongs to.
	 * @param annotationInfo - {@code ElementInfo} instance of the annotated class
	 * @param methods - {@code Map} of methods to be generated for the class which is being processed 
	 */
	private void createOfWithClass(ClassName incomingObjectClass, String packageName, String className, ElementInfo annotationInfo, Map<String, MethodSpec> methods) {
		// create of method
		String incomingObjectName = "incoming"+incomingObjectClass.simpleName();
		
		MethodSpec.Builder of = MethodSpec.methodBuilder("of")
				.addModifiers(Modifier.PUBLIC, Modifier.STATIC)
				.addParameter(incomingObjectClass, incomingObjectName, new Modifier[0])
				.addStatement("if ($L == null) return null" , incomingObjectName)			
				.addStatement("$L newMappedObject = new $L()", className, className)
				.addException(IllegalAccessException.class)

				.addJavadoc(CodeBlock
					    .builder()
					    .add("Creates a populated instance of {@code $L} from the given instance of {@code $L},\n\n", ClassName.get(packageName, className),
					    																							incomingObjectClass.simpleName())
					    .add("@param $L - the incoming object of type $L to be mapped.\n", incomingObjectName, incomingObjectClass.simpleName())
					    .add("@return populated instance of {@code $L}.\n", ClassName.get(packageName, className))
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
					boolean fieldIsMapped = fieldIsMapped(field);
						
					if (!fieldIsMapped) {
						createStatementForMappedFieldOf(incomingObjectClass, annotationInfo, methods, incomingObjectName, of,
								needsSuppressWarnings, fieldType, fieldClass, fieldName, setterName, localFieldName);
					} else {
						createStatementForUnmappedFieldOf(incomingObjectClass, annotationInfo, incomingObjectName, of, field,
								fieldClass, fieldName, setterName, localFieldName);							
					}
				});
				of.addStatement("return newMappedObject")
				  .returns(ClassName.get(packageName, className));
				if (needsSuppressWarnings.get() == true) {
					// create @SuppressWarning("unchecked") annotation
					AnnotationSpec suppressWarningsAnnotation =  AnnotationSpec.builder(SuppressWarnings.class)
																				.addMember("value", "$S", "unchecked")
																				.build();
					of.addAnnotation(suppressWarningsAnnotation);
				}
				methods.put("of", of.build());
	}

	/**
	 * Generate mapping statement for a field of a class that does NOT have a mapping annotation. 
	 * 
	 * @param incomingObjectClass - {@code ClassName} object of the {@code @JSONMapped} annotated classe.
	 * @param annotationInfo - {@code ElementInfo} instance containing information about the XXXMapped annotation
	 * @param incomingObjectName - name of the object which is provided to the method to which the statement is to be added to
	 * @param of - {@code MethodSpec} instance of the method the created statement is to be added to
	 * @param field - the {@code VariableElement} of the field.
 	 * @param fieldClass - {@code TypeName} of the field to be processed
	 * @param fieldName - name of the field to be processed
	 * @param setterName - name of the setter method to be called in the statement
	 * @param localFieldName - generated name of the {@code Field} of the 'incoming' class
	 */
	private void createStatementForUnmappedFieldOf(final ClassName incomingObjectClass, 
												   final ElementInfo annotationInfo, 
												   String incomingObjectName,
												   final MethodSpec.Builder of, 
												   final VariableElement field, 
												   final TypeName fieldClass, 
												   String fieldName, 
												   String setterName,
												   String localFieldName) {
		TypeMirror fieldTypeMirror = field.asType();
		Element fieldElement = typeUtils.asElement(fieldTypeMirror);
		if (fieldElement instanceof TypeElement) {
			ClassName fieldClassName = ClassName.get((TypeElement) fieldElement); 
			String fcName = annotationInfo.prefix()+fieldClassName.simpleName();
			ClassName mappedFieldClassName = ClassName.get(generatePackageName(fieldClassName, annotationInfo), fcName);
			of
			  .addStatement("$T $L = $T.deepGetField($T.class, $S, true)", Field.class,
					  												 localFieldName,
					  												 ReflectionUtil.class,
					  												 incomingObjectClass,
					  												 fieldName)

			  .beginControlFlow("if ($L != null)" , localFieldName)
				.addStatement("newMappedObject.$L($T.of(($L)$T.invokeGetterMethod($L, $L)))",  
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
	 * Generate a statement to map a field of a class annotated with XXXMapped. This need to generate code
	 * to map the annotated class field into it's mapped class. Since we do not know wheter or not the annotated class
	 * uses fluent accessors, the generated code needs to call the setter method indirectly using reflection. 
	 * This way, we do not have to care about its name.
	 * 
	 * @param incomingObjectClass - {@code ClassName} object of the annotated classe.
	 * @param annotationInfo - {@code ElementInfo} instance containing information about the annotation
	 * @param methods - {@code Map} of methods to be generated for the class which is being processed 
	 * @param incomingObjectName - name of the object which is provided to the method to which the statement is to be added to
	 * @param of - {@code MethodSpec} instance of the method the created statement is to be added to
	 * @param needsSuppressWarnings - flag indicating whether or not a {@code @SuppressWarnings} annotation needs to be generated.
	 * @param fieldType - {@code TypeMirror} type of the field.
 	 * @param fieldClass - {@code TypeName} of the field to be processed
	 * @param fieldName - name of the field to be processed
	 * @param setterName - name of the setter method to be called in the statement
	 * @param localFieldName - generated name of the {@code Field} of the 'incoming' class
	 */
	private void createStatementForMappedFieldOf(final ClassName incomingObjectClass, 
												 final ElementInfo annotationInfo, 
												 final Map<String, MethodSpec> methods,
												 String incomingObjectName, 
												 final MethodSpec.Builder of, 
												 AtomicBoolean needsSuppressWarnings, 
												 final TypeMirror fieldType,
												 final TypeName fieldClass, 
												 String fieldName, 
												 String setterName, 
												 String localFieldName) {
		of
		.addStatement("$T $L = $T.deepGetField($T.class, $S, true)", Field.class,
																localFieldName,
																ReflectionUtil.class, 
																incomingObjectClass, 
																fieldName)
		.beginControlFlow("if ($L != null)" , localFieldName);
		// add suppresswarnings if necessary
		if (fieldType.getKind() == TypeKind.DECLARED) {
			List<TypeName> sourceTypeArguments = obtainTypeArguments(fieldType);							
			List<TypeName> destinationTypeArguments = collectTypes(annotationInfo, sourceTypeArguments);
			
			TypeMirror collectionType = getElementUtils().getTypeElement("java.util.Collection").asType();
			TypeMirror mapType = getElementUtils().getTypeElement("java.util.Map").asType();
			TypeMirror setType = getElementUtils().getTypeElement("java.util.Set").asType();

			if (sourceTypeArguments != null && sourceTypeArguments.size()>0) {
				Element[] argumentElement = new Element[sourceTypeArguments.size()];
				boolean[] argumentIsMapped = new boolean[sourceTypeArguments.size()];
				
				for (int i=0; i < sourceTypeArguments.size(); i++) {
					argumentElement[i] = getElementUtils().getTypeElement(sourceTypeArguments.get(i).toString());
					argumentIsMapped[i] = fieldIsMapped(argumentElement[i]);
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
					generateMappingStatementForCollectionForOf(methods, incomingObjectName, of, fieldClass, fieldName, setterName, localFieldName, sourceTypeArguments, destinationTypeArguments);
				} 
				else if (argumentElement.length > 1 && (argumentIsMapped[0] || argumentIsMapped[1]) &&
						fieldType != null && getTypeUtils().isAssignable(
												getTypeUtils().erasure(fieldType), 
												getTypeUtils().erasure(mapType ))) {
					
					
					generateMappingStatementForMapForOf(methods, 
														incomingObjectName, 
														of,
														fieldClass, 
														fieldName,
														setterName,
														localFieldName,
														sourceTypeArguments, 
														destinationTypeArguments);

					
				} else { // typeArguments present but not mapped
					of
					.addStatement("newMappedObject.$L(($L)$T.invokeGetterMethod($L, $L))",  
									  setterName,
							   		  fieldClass,
							   		  ReflectionUtil.class, 
							   		  incomingObjectName,
							   		  localFieldName);
				}									
			} else {
				of
				.addStatement("newMappedObject.$L(($L)$T.invokeGetterMethod($L, $L))",  
								  setterName,
						   		  fieldClass,
						   		  ReflectionUtil.class, 
						   		  incomingObjectName,
						   		  localFieldName);
			}
		} else {
			of
			.addStatement("newMappedObject.$L(($L)$T.invokeGetterMethod($L, $L))",  
							  setterName,
					   		  fieldClass,
					   		  ReflectionUtil.class, 
					   		  incomingObjectName,
					   		  localFieldName);
		}
		of.endControlFlow();
	}

	/**
	 * Generate a statement to map a {@code java.util.Collection} with type arguments which are mapped with XXXMapped
	 * for the of() method using the annotated class for initialisation.
	 *   
	 * @param methods - {@code Map} of methods to be generated for the class which is being processed 
	 * @param incomingObjectName - name of the object which is provided to the method to which the statement is to be added to
	 * @param of - {@code MethodSpec} instance of the method the created statement is to be added to
	 * @param fieldClass - {@code TypeName} of the field to be processed
	 * @param fieldName - name of the field to be processed
	 * @param setterName - name of the setter method to be called in the statement
	 * @param localFieldName - generated name of the {@code Field} of the 'incoming' class
	 * @param sourceTypeArguments - list of {@code TypeName} entries representing the 'incoming' argument types
	 * @param destinationTypeArguments - list of {@code TypeName} entries representing the mapped argument types
	 */
	private void generateMappingStatementForCollectionForOf(final Map<String, MethodSpec> methods, 
															String incomingObjectName, 
															final MethodSpec.Builder of,
															final TypeName fieldClass, 
															String fieldName, 
															String setterName, 
															String localFieldName,
															final List<TypeName> sourceTypeArguments, 
															final List<TypeName> destinationTypeArguments) {
		String methodName = createTypeElementMappingsOf(methods, sourceTypeArguments, destinationTypeArguments).get(sourceTypeArguments.get(0));
		of										
		.addStatement("newMappedObject.$L((($L)$T.invokeGetterMethod($L, $L)).stream().map(e -> $L(e)).collect($T.toList()))",
													 setterName,
										   		  	 fieldClass,
										   		  	 ReflectionUtil.class, 
										   		  	 incomingObjectName,
										   		  	 localFieldName,
										   		  	 methodName,
										   		  	 Collectors.class);
	}
	
	/**
	 * Generate a statement to map a {@code java.util.Map} with type arguments which are mapped with {@code @JSONMapped}
	 * for the of() method using the annotated class for initialisation.
	 * 
	 * @param methods - {@code Map} of methods to be generated for the class which is being processed 
	 * @param incomingObjectName - name of the object which is provided to the method to which the statement is to be added to
	 * @param of - {@code MethodSpec} instance of the method the created statement is to be added to
	 * @param fieldClass - {@code TypeName} of the field to be processed
	 * @param fieldName - name of the field to be processed
	 * @param setterName - name of the setter method to be called in the statement
	 * @param localFieldName - generated name of the {@code Field} of the 'incoming' class
	 * @param sourceTypeArguments - list of {@code TypeName} entries representing the 'incoming' argument types
	 * @param destinationTypeArguments - list of {@code TypeName} entries representing the mapped argument types
	 */
	private void generateMappingStatementForMapForOf(final Map<String, MethodSpec> methods, 
													 String incomingObjectName, 
													 final MethodSpec.Builder of,
													 final TypeName fieldClass, 
													 String fieldName, 
													 String setterName, 
													 String localFieldName,
													 final List<TypeName> sourceTypeArguments, 
													 final List<TypeName> destinationTypeArguments) {

		Map<TypeName, String> methodNames = createTypeElementMappingsOf(methods, sourceTypeArguments, destinationTypeArguments);
		
		String[] statements = new String[2];
		if (methodNames.containsKey(sourceTypeArguments.get(0))) {
			statements[0] = "e -> "+methodNames.get(sourceTypeArguments.get(0))+"(e.getKey())";
		} else {
			statements[0] = "e -> e.getKey()";
		}
		if (methodNames.containsKey(sourceTypeArguments.get(1))) {
			statements[1] = "e -> "+methodNames.get(sourceTypeArguments.get(1))+"(e.getValue())";
		} else {
			statements[1] = "e -> e.getValue()";
		}
		
		of			
		.addStatement("newMappedObject.$L((($L)$T.invokeGetterMethod($L, $L)).entrySet().stream().collect($T.toMap($L,$L)))",
													 setterName,
													 fieldClass,
													 ReflectionUtil.class,
													 incomingObjectName,										   		  	 
													 localFieldName,
										   		  	 Collectors.class,
										   		  	 statements[0],
										   		  	 statements[1]);
	}

	/**
	 * Generate a statement to map a {@code java.util.Collection} with type arguments which are mapped with XXXMapped
	 * for the of() method using the annotated class for initialisation.
	 *   
	 * @param methods - {@code Map} of methods to be generated for the class which is being processed 
	 * @param of - {@code MethodSpec} instance of the method the created statement is to be added to
	 * @param fieldName - name of the field to be processed
	 * @param setterName - name of the setter method to be called in the statement
	 * @param sourceTypeArguments - list of {@code TypeName} entries representing the 'incoming' argument types
	 * @param destinationTypeArguments - list of {@code TypeName} entries representing the mapped argument types
	 */
	private void generateMappingStatementForCollectionForOfWithArguments(final Map<String, MethodSpec> methods, 
															final MethodSpec.Builder of,
															String fieldName, 
															String setterName, 
															final List<TypeName> sourceTypeArguments, 
															final List<TypeName> destinationTypeArguments) {
		String methodName = createTypeElementMappingsOf(methods, sourceTypeArguments, destinationTypeArguments).get(sourceTypeArguments.get(0));
		of			
		.addStatement("newMappedObject.$L($L.stream().map(e -> $L(e)).collect($T.toList()))",
													 setterName,
													 fieldName,
										   		  	 methodName,
										   		  	 Collectors.class);
	}

	/**
	 * Generate a statement to map a {@code java.util.Map} with type arguments which are mapped with XXXMapped
	 * for the of() method using arguments for initialisation.  
	 *  
	 * @param methods - {@code Map} of methods to be generated for the class which is being processed 
	 * @param of - {@code MethodSpec} instance of the method the created statement is to be added to
	 * @param fieldName - name of the field to be processed
	 * @param setterName - name of the setter method to be called in the statement
	 * @param sourceTypeArguments - list of {@code TypeName} entries representing the 'incoming' argument types
	 * @param destinationTypeArguments - list of {@code TypeName} entries representing the mapped argument types
	 */
	private void generateMappingStatementForMapForOfWithArguments(final Map<String, MethodSpec> methods, 
																final MethodSpec.Builder of,
																String fieldName, 
																String setterName, 
																final List<TypeName> sourceTypeArguments, 
																final List<TypeName> destinationTypeArguments) {
		Map<TypeName, String> methodNames = createTypeElementMappingsOf(methods, sourceTypeArguments, destinationTypeArguments);
		
		String[] statements = new String[2];
		if (methodNames.containsKey(sourceTypeArguments.get(0))) {
			statements[0] = "e -> "+methodNames.get(sourceTypeArguments.get(0))+"(e.getKey())";
		} else {
			statements[0] = "e -> e.getKey()";
		}
		if (methodNames.containsKey(sourceTypeArguments.get(1))) {
			statements[1] = "e -> "+methodNames.get(sourceTypeArguments.get(1))+"(e.getValue())";
		} else {
			statements[1] = "e -> e.getValue()";
		}
		
		of			
		.addStatement("newMappedObject.$L($L.entrySet().stream().collect($T.toMap($L,$L)))",
													 setterName,
													 fieldName,										   		  	 
										   		  	 Collectors.class,
										   		  	 statements[0],
										   		  	 statements[1]);
	}
	
	
	
	/**
	 * create separate method to avoid try/catch within stream processing
	 * 
	 * @param methods - {@code Map} of methods to be generated for the class which is being processed 
	 * @param sourceTypeArguments - list of {@code TypeName} entries representing the '- {@code TypeName} of the field to be processed' argument types
	 * @param destinationTypeArguments - list of {@code TypeName} entries representing the mapped argument types
	 * @return map of method names for each {@code TypeName} that is actually mapped with XXXMapped
	 */
	private Map<TypeName, String> createTypeElementMappingsOf(final Map<String, MethodSpec> methods,
											   final List<TypeName> sourceTypeArguments, 
											   final List<TypeName> destinationTypeArguments) {
		
		Map<TypeName, String> methodNames = new HashMap<>();
		for (int typeIndex = 0; typeIndex < sourceTypeArguments.size(); typeIndex++) {
			
			TypeElement argumentElement =  getElementUtils().getTypeElement(sourceTypeArguments.get(typeIndex).toString());			
			if (typeIsMapped(argumentElement)) {
			
				String methodName = "map"+StringUtil.capitalise(((ClassName)sourceTypeArguments.get(typeIndex)).simpleName())+"To"+(
						((ClassName)destinationTypeArguments.get(typeIndex)).simpleName());
				methodNames.put(sourceTypeArguments.get(typeIndex), methodName);
				if (! methods.containsKey(methodName)) {
					MethodSpec.Builder mapMethodBuilder = MethodSpec.methodBuilder(methodName)
						.addJavadoc(CodeBlock
							    .builder()
							    .add("Method to map an instance of {@code $L} into instance of a generated class {@code $L}.\n", ((ClassName)sourceTypeArguments.get(typeIndex)).simpleName(),
							    																				 ((ClassName)destinationTypeArguments.get(typeIndex)).simpleName())
							    .add("@param methods - List of methods to be created\n")
							    .add("@param typeArguments - {@code TypeMirror}s of the arguments of the field to be mapped.\n")
							    .add("@param typeArguments - {@code TypeName}s of the arguments of the field to be mapped.\n")
							    .add("@return populated instance of {@code $L}.\n", ((ClassName)destinationTypeArguments.get(typeIndex)).simpleName())
							    .build())
						.addModifiers(Modifier.PRIVATE, Modifier.STATIC);
				
						mapMethodBuilder.addParameter(sourceTypeArguments.get(typeIndex), "e", Modifier.FINAL);
										
						mapMethodBuilder
							.returns(destinationTypeArguments.get(typeIndex))
							.addStatement("$T result = null", destinationTypeArguments.get(typeIndex))
							.beginControlFlow("try")
								.addStatement("result = $T.of(e)", destinationTypeArguments.get(typeIndex))
							.endControlFlow()
							.beginControlFlow("catch($T eIllAcc)", IllegalAccessException.class)
								.addStatement("eIllAcc.printStackTrace()")
							.endControlFlow()
							.addStatement("return result");
						MethodSpec mapMethod = mapMethodBuilder.build();
						methods.put(methodName, mapMethod);
				}
			} 
		}
		return methodNames;
	}
	
	

	/**
	 * create separate method to avoid try/catch within stream processing
	 * 
	 * @param methods - {@code Map} of methods to be generated for the class which is being processed 
	 * @param sourceTypeArguments - list of {@code TypeName} entries representing the 'incoming' argument types
	 * @param destinationTypeArguments - list of {@code TypeName} entries representing the mapped argument types
	 * @param argumentElement - array of Element types of the sourceTypeArguments
	 * @return map of method names for each {@code TypeName} that is actually mapped with XXXMapped
	 */
	private Map<TypeName, String> createTypeElementMappingTo(final Map<String, MethodSpec> methods, 
													 final List<TypeName> sourceTypeArguments,
													 final List<TypeName> destinationTypeArguments, 
													 Element[] argumentElement) {
		Map<TypeName, String> methodNames = new HashMap<>();
		
		for (int typeIndex = 0; typeIndex < sourceTypeArguments.size(); typeIndex++) {
			
			TypeElement argElement = (TypeElement) argumentElement[typeIndex];
			if (typeIsMapped(argElement)) {
			
				String methodName = "map"+StringUtil.capitalise(((ClassName) sourceTypeArguments.get(typeIndex)).simpleName())+"To"+(
						((ClassName)destinationTypeArguments.get(typeIndex)).simpleName());
				
				methodNames.put(sourceTypeArguments.get(typeIndex), methodName);
				
				if (! methods.containsKey(methodName)) {
					MethodSpec mapMethod = MethodSpec.methodBuilder(methodName)
						.addJavadoc(CodeBlock
							    .builder()
							    .add("Method to map an instance of {@code $L} back into an instance of the annotated class {@code $L}.\n", ((ClassName) sourceTypeArguments.get(typeIndex)).simpleName(),
							    																						   ((ClassName) destinationTypeArguments.get(typeIndex)).simpleName())
							    .add("@param methods - List of methods to be created\n")
							    .add("@param typeArguments - {@code TypeMirror}s of the arguments of the field to be mapped.\n")
							    .add("@param typeArguments - {@code TypeName}s of the arguments of the field to be mapped.\n")
							    .add("@return populated instance of  {@code $L}.\n",((ClassName) destinationTypeArguments.get(typeIndex)).simpleName())
							    .build())
						.addModifiers(Modifier.PRIVATE, Modifier.STATIC)
						.addParameter(sourceTypeArguments.get(typeIndex), "e", Modifier.FINAL)
						.returns(destinationTypeArguments.get(typeIndex))
						.addStatement("$T result = null", destinationTypeArguments.get(typeIndex))
						.beginControlFlow("try")
							.addStatement("result = e.to()")
						.endControlFlow()
						.beginControlFlow("catch($T eIllAcc)", IllegalAccessException.class)
							.addStatement("eIllAcc.printStackTrace()")
						.endControlFlow()
						.addStatement("return result")
						.build();
				
						methods.put(methodName, mapMethod);
				}
			}
		}
		return methodNames;
	}

//	/**
//	 * return mapped field class name
//	 * @param annotationInfo
//	 * @param fieldElementClass
//	 * @return mappedFieldClassName
//	 */
//	private TypeName getMappedTypeName(final ElementInfo annotationInfo, ClassName fieldElementClass) {
//		String fcName = annotationInfo.prefix() + fieldElementClass.simpleName();
//		ClassName mappedFieldClassName = ClassName.get(generatePackageName(fieldElementClass, annotationInfo), fcName);
//		return mappedFieldClassName;
//	}

	/**
	 * generates a <i>to</i>-method which returns an instance of the annotated class
	 * with copies of all field values.
	 * 
	 * @param packageName - name of the package of the class which is being create by this method belongs to.
	 * @param className - name of the class which is being create by this method belongs to.
	 * @param annotationInfo - {@code ElementInfo} instance of the annotated class
	 * @param methods - {@code Map} of methods to be generated for the class which is being processed 
	 */
	private void createTo(String packageName, 
						  String className, 
						  final ElementInfo annotationInfo, 
						  final Map<String, MethodSpec> methods) {
		// create of method
		final String objectName = StringUtil.uncapitalise(className);
		final ClassName externalClass = ClassName.get(packageName, className);

		MethodSpec.Builder to = MethodSpec.methodBuilder("to").addModifiers(Modifier.PUBLIC)
				.addException(IllegalAccessException.class)
				.addJavadoc(CodeBlock.builder().add("Recreates instance of {@code $L} object from the given object instance,\n", externalClass)
						.add("Calling the setters on the source would lead to an exception and is insecure,\n")
						.add("because we cannot predict if fluent accessors are being used.\n")
						.add("For this reason the getter call is wrapped by reflection.\n\n")
						.add("@return the recreated object instance of $L", externalClass).build())
				.addStatement("$T $L = new $T()", externalClass, objectName, externalClass);

		AtomicInteger fieldCount = new AtomicInteger(0);
		annotationInfo.fields().stream().filter(field -> !isMethodFinalPrivateStatic(field) &&
														 !field.getModifiers().contains(Modifier.FINAL))
					  .forEach(field -> {
			boolean fieldIsMapped = fieldIsMapped(field);
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
			
			
			if (fieldType.getKind() == TypeKind.DECLARED) {
				List<TypeName> typeArguments = obtainTypeArguments(fieldType);							
				List<TypeName> types = collectTypes(annotationInfo, typeArguments);

				if (typeArguments != null && typeArguments.size()>0) {
					Element[] argumentElement = new Element[typeArguments.size()];
					boolean[] argumentIsMapped = new boolean[typeArguments.size()];
					
					for (int i=0; i < typeArguments.size(); i++) {
						argumentElement[i] = getElementUtils().getTypeElement(typeArguments.get(i).toString());
						argumentIsMapped[i] = fieldIsMapped(argumentElement[i]);
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
						
						generateListTypeMappingStatementForCollectionForTo(methods,
														   objectName, 
														   to,
														   fieldClass, 
														   fieldName, 
														   localFieldName,
														   typeArguments, 
														   types, 
														   fieldIsMapped,
														   argumentElement); 
					} else if (argumentElement.length > 1 && (argumentIsMapped[0] || argumentIsMapped[1]) &&
							fieldType != null && getTypeUtils().isAssignable(
									getTypeUtils().erasure(fieldType), 
									getTypeUtils().erasure(mapType ))) {
		
		
							generateMappingStatementForMapForTo(methods, 
											objectName, 
											to,
											fieldClass, 
											fieldName,
											localFieldName,
											types, 
											typeArguments,
											argumentElement);
							
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
		methods.put("to", to.build());
	}

	
	
	/**
	 * generate a mapping statement and method for the {@code JASONMapped} argument type fo a liset, used
	 * in the to() method
	 * 
	 * @param methods - {@code Map} of methods to be generated for the class which is being processed 
	 * @param objectName - name of the object which is provided to the method to which the statement is to be added to
	 * @param to - {@code MethodSpec} instance of the method the created statement is to be added to
	 * @param destinationFieldClass - {@code TypeName} of the field to be processed
	 * @param classFieldName - name of the field in the generated class for which the statement is being created.
	 * @param localFieldName - generated name of the {@code Field} of the 'incoming' class
	 * @param destinationTypes - list of {@code TypeName} entries representing the mapped argument types
	 * @param sourceTypes - list of {@code TypeName} entries representing the 'incoming' argument types
	 * @param fieldIsMapped - flag to indicate whether or not field has a class annotated with XXXMapped
	 * @param argumentElement - array of Element types of the sourceTypeArguments
	 */
	private void generateListTypeMappingStatementForCollectionForTo(final Map<String, MethodSpec> methods, 
											String objectName, 
											MethodSpec.Builder to,
											final TypeName destinationFieldClass, 
											String classFieldName, 
											String localFieldName,
											final List<TypeName> destinationTypes, 
											final List<TypeName> sourceTypes, 
											boolean fieldIsMapped, 
											Element[] argumentElement) {
		
		String methodName = createTypeElementMappingTo(methods, sourceTypes, destinationTypes, argumentElement).get(sourceTypes.get(0));
		to.addStatement("$T.invokeSetterMethod($L, $L, $L.stream().map(e -> $L(e)).collect($T.toList()))",
					ReflectionUtil.class, 
					objectName,
					localFieldName,
					classFieldName,
					methodName,
					Collectors.class);
	}	
	
	
	/**
	 * generate a mapping statement and method for the {@code JASONMapped} argument type fo a map, used
	 * in the to() method
	 * 
	 * @param methods - {@code Map} of methods to be generated for the class which is being processed 
	 * @param incomingObjectName - name of the object which is provided to the method to which the statement is to be added to
	 * @param of - {@code MethodSpec} instance of the method the created statement is to be added to
	 * @param fieldClass - {@code TypeName} of the field to be processed
	 * @param fieldName - name of the field in the generated class for which the statement is being created.
	 * @param localFieldName - generated name of the {@code Field} of the 'incoming' class
	 * @param sourceTypeArguments - list of {@code TypeName} entries representing the 'incoming' argument types
	 * @param destinationTypeArguments - list of {@code TypeName} entries representing the mapped argument types
	 * @param argumentElement - array of Element types of the sourceTypeArguments
	 */
	private void generateMappingStatementForMapForTo(final Map<String, MethodSpec> methods, 
													 String incomingObjectName, 
													 final MethodSpec.Builder of,
													 final TypeName fieldClass, 
													 String fieldName, 
													 String localFieldName,
													 final List<TypeName> sourceTypeArguments, 
													 final List<TypeName> destinationTypeArguments,
													 Element[] argumentElement) {

		Map<TypeName, String> methodNames = createTypeElementMappingTo(methods, sourceTypeArguments, destinationTypeArguments, argumentElement);
		
		String[] statements = new String[2];
		if (methodNames.containsKey(sourceTypeArguments.get(0))) {
			statements[0] = "e -> "+methodNames.get(sourceTypeArguments.get(0))+"(e.getKey())";
		} else {
			statements[0] = "e -> e.getKey()";
		}
		if (methodNames.containsKey(sourceTypeArguments.get(1))) {
			statements[1] = "e -> "+methodNames.get(sourceTypeArguments.get(1))+"(e.getValue())";
		} else {
			statements[1] = "e -> e.getValue()";
		}
		
		of			
		.addStatement("$T.invokeSetterMethod($L, $L, $L.entrySet().stream().collect($T.toMap($L,$L)))",
													ReflectionUtil.class,
													incomingObjectName,
													localFieldName,
													fieldName,
													Collectors.class,
										   		  	statements[0],
										   		  	statements[1]);
		
	}
	
	/**
	 * Create a statement to map a field of a class which is not annotated with XXXMapped.  
	 * 
	 * @param objectName - name of the object which is provided to the method to which the statement is to be added to
	 * @param to - {@code MethodSpec} instance of the method the created statement is to be added to
	 * @param fieldIsMapped - indicates whether or not the given field is annotated  with XXXMapped
	 * @param fieldName - name of the field in the generated class for which the statement is being created.
	 * @param localFieldName - generated name of the {@code Field} of the 'incoming' class
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
	 * @param annotationInfo - {@code ElementInfo} instance containing information about the annotation
	 * @param fieldClass    - TypeName for class field shall be created in.
	 * @param fieldIsMapped - indicates whether or not the given field is annotated  with XXXMapped.
	 * @return field specification for the create field.
	 */
	@Override
	abstract public FieldSpec createFieldSpec(final VariableElement field, 
									 		   final ElementInfo annotationInfo, 
									 		   final TypeName fieldClass,				
									 		   boolean fieldIsMapped);
	@Override
	public Types getTypeUtils() {
		return procEnv.getTypeUtils();
	}

	@Override
	public Elements getElementUtils() {
		return procEnv.getElementUtils();
	}

}
