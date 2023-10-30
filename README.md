# json-mapper
Generate Java classes with Jackson annotations with some convenient creation/mapping functions.

## Usage

### Maven

Add the annotation processor class to your pom.xml and rebuild your project.

```
	<build>
		<plugins>
			...
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-compiler-plugin</artifactId>
				...
				<configuration>
					...
					<annotationProcessorPaths>
						...
						<path>
							<groupId>net.magiccode.json</groupId>
							<artifactId>JsonMapper</artifactId>
							<version>0.0.1</version>
						</path>
						...
					</annotationProcessorPaths>
				</configuration>
			</plugin>

		</plugins>

	</build>
```

## Annotations

### @JSONMapped

@JSONMapped can be used on class level and initiates the generation of a class that contains the same fields as the annotated class
but annotates these with Jackson annotations for JSON generation.

Example:
```
@JSONMapped(fluentAccessors = false)
```

Some arguments can be provided to have some influence on the code generation.

| argument | values | Description |
| --- | --- | -- |
|useLombok |true, **false**|Setting useLombok to true generates much less code, because getters and setters can be replace by lombok annotations, just as the constructor(s), toString etc.|
|fluentAccessors |true, **false**|Creates getters and setters that do not start with **get**, **is** or **set** rather than the actual name of the field. If useLombok is **true**, this setting is passed on to @Accessors(fluent=true|false).|
|chainedSetters |**true**, false|Generates setters which return **this**. |
|prefix | JSON |Adds a prefix to the name of the generated class. Defaults to "JSON"|
|packageName| |Defines the name of the packacke for the generated class. If no **packageName** is given, this defaults to the package of the annotated class.|
|subpackageName| |Defines the name for a subpackage added to the default if **packageName** is not specified.|
|jsonInclude |**ALWAYS**,** NON_NULL, NON_ABSENT, NON_EMPTY, NON_DEFAULT, CUSTOM, USE_DEFAULTS|Generated classes are annotated with **@JsonInclude**. This defaults to ALWAYS, but can be specified otherwise by using the **jsonInclude** argument.|
|superClass| |The superclass that the generated class will extend.|
|superInterface| |The superinterface that the generated class will implement.|


### @JSONTransient

This annotation works on field level and is used to mark fields that will be annotated with **@JsonIgnore** in the generated code.

```
@JSONTransient
private String someField;
```
in the annotated class becomes
```
@JsonIgnore
private String ignoredValue;
```
in the generated code.


### @JSONRequired

Every field in the generated class will be annotated with **@JsonProperty** unless marked with **@JSONTransient**. Setting **@JSONRequired** on a field 
additionally adds the **required = true** argument.

```
@JSONRequired
private Double doubleValue;
```
becomes
```
@JsonProperty(
       value = "double_value",
       required = true
)
private Double doubleValue;
```
in the generated class.


## Generated code

The code generated by the annotation processor depends on the options specified on the **@JSONMapped** annotation. If **useLombok** is set to true, a class will be generated
using Lombok annotations and no getters, setters or constructors will be created. 
Fields, of course, will be generated with (at least) @JsonProperty or @JsonIgnore annotations in any case.

### Constructors

---- Constructor explanation an fromSource/of methods ----


### toJSONString()

Additionally to the usual **toString()** method and additional **toJSONString()** method is generated that prints the name and contents of the class as formatted JSON.

```
net.magiccode.lazy.json.JSONExampleCode04{
  "another_field" : 90,
  "value_list" : [ "abc", "def", "ghi", "xyz" ],
  "third_field" : -23.4567,
  "boolean_field" : true,
  "example02" : null
}
```








