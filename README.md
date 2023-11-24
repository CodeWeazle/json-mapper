# kilauea

*kilauea* implements an annotation processor to generate Java classes with Jackson annotations with some convenient creation/mapping functions.

## Usage

### Maven

Add the annotation processor class to your pom.xml and rebuild your project. (The version shown is related to the *release* branch, which is also available on the [maven-central repository](https://central.sonatype.com/).)

```

    <dependencies>
	  ...
      <dependency>
	 	<groupId>net.magiccode</groupId>
		<artifactId>kilauea</artifactId>
		<version>0.0.9</version>
      </dependency>
      ...
    </dependencies>
    ...
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
							<groupId>net.magiccode</groupId>
							<artifactId>kilauea</artifactId>
							<version>0.0.9</version>
						</path>
						...
					</annotationProcessorPaths>
				</configuration>
			</plugin>

		</plugins>

	</build>
```

### Gradle
```
implementation group: 'net.magiccode.kilauea', name: 'kilauea', version: '0.0.9'

...
dependencies {
  compile("net.magiccode.kilauea:kilauea:0.0.9")
  annotationProcessor("net.magiccode.kilauea:kilauea:0.0.9")
  ...
}
```

### Others

For other build systems please consult the current [maven-central site](https://central.sonatype.com/artifact/net.magiccode.kilauea/kilauea)


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
|useLombok |true, **false**|Setting useLombok to true generates much less code, because getters and setters can be replaced by lombok annotations, just as the constructor(s), toString etc.|
|fluentAccessors |true, **false**|Creates getters and setters that do not start with *get*, *is* or *set* rather than the actual name of the field. If useLombok is *true*, this setting is passed on to @Accessors(fluent=true&#124;false).|
|chainedSetters |**true**, false|Generates setters which return *this*. |
|prefix | JSON |Adds a prefix to the name of the generated class. Defaults to "JSON"|
|packageName| |Defines the name of the packacke for the generated class. If no *packageName* is given, this defaults to the package of the annotated class.|
|subpackageName| json |Defines the name for a subpackage added to the default if *packageName* is not specified. The default value is *json*|
|jsonInclude |**ALWAYS**, NON_NULL, NON_ABSENT, NON_EMPTY, NON_DEFAULT, CUSTOM, USE_DEFAULTS|Generated classes are annotated with *@JsonInclude*. This defaults to ALWAYS, but can be specified otherwise by using the *jsonInclude* argument.|
|superClass| |Fully qualified name of the superclass that the generated class will extend.|
|interfaces| |Comma separated list of fully qualified name of the interfaces that the generated class will implement.|
|inheritFields|**true**, false|Defines whether or not fields from the super-class hierarchy of the annotated class should be generated. Default is **true**|
|datePattern| "yyyy-MM-dd" |Pattern for *@JsonFormat* generated for *LocalDate* fields in the generated class |
|dateTimePattern| "yyyy-MM-dd HH:mm:ss" |Pattern for *@JsonFormat* generated for *LocalDateTime* fields in the generated class |

### @JSONTransient

This annotation works on field level and is used to mark fields that will be annotated with *@JsonIgnore* in the generated code.
(Fields already annotated with *@JsonIgnore* can omit this annotation, because *@JsonIgnore* is picked up as well.)  

```
@JSONTransient
private String ignoredValue;
```
in the annotated class becomes
```
@JsonIgnore
private String ignoredValue;
```
in the generated code.


### @JSONRequired

Every field in the generated class will be annotated with *@JsonProperty* unless marked with *@JSONTransient*. Setting *@JSONRequired* on a field 
additionally adds the *required = true* argument.

```
@JSONRequired
private Double requiredValue;
```
becomes
```
@JsonProperty(
       value = "double_value",
       required = true
)
private Double requiredValue;
```
in the generated class.


## Use of generated classes

### of/to methods

To create a mapped class of an instance 0f *Person*, apply the *of()* method like so

```
	JSONPerson personMapped;
	try {
		personMapped = JSONPerson.of(person);
		// print the object 
		System.out.println(personMapped.toJSONString());
	} catch (IllegalAccessException e) {
		e.printStackTrace();
	}
```

The *of()* method of the generated class can possibly throw an *IllegalAccessException*, because the setters need to be called indirectly by the use of reflection. This is, because we cannot know if the class our code is generated from uses fluent accessors or not, so generating a mapping method calling setters could easily fail. Calling a (setter) method via reflection needs proper handling of the *IllegalAccessException* (which is quite unlikely to be thrown), which we leave to the implementation of the class that calls this code, because we believe the author of that can deal with it according to the context the code is running in.

To map the instance back into the original class, the *to()* method should be employed, like in this example

```
	try {
		Person person = personMapped.to();
	} catch (IllegalAccessException e) {
		e.printStackTrace();
	}
```

### Getters and setters

The generated classes will have getters and setters for all fields, generated according to the parameters given in the *@JSONMapped* annotation. This means that if *fluentAccessors=true* was specified, the getter and setter methods will be generated without *get*(*is* resp.) and *set* rather than using the name of the field (always starting with a lowercase letter). If *chainedSetters=true* all setter methods will return *this*, so that setter method calls can be chained like so

```
	Person p = new Person();
	p.setAddresses(addresses)
         .setContacts(contacts)
         .setDateOfBirth(LocalDate.of(1967, 10, 25))
	 .setFirstName("Jeremy")
	 .setMiddleName("H.")
	 .setSurName("Bromley");
```

## Generated code

The code generated by the annotation processor depends on the options specified on the *@JSONMapped* annotation. If *useLombok* is set to true, a class will be generated
using Lombok annotations and no getters, setters or constructors will be created. 
Fields, of course, will be generated with (at least) @JsonProperty or @JsonIgnore annotations in any case.

### Field mapping

Assuming we have a class that looks as follows:

```
public class Example01 {

	private String field01;
	
	private Integer field02;
	
	private Double field03;
	
	private Map<String, Integer> stringToIntMap;	
}
```

When we add the annotation @JSONMapped (and assume we did not set a different prefix but stick to 'JSON' for now), the generated class would look like this:
```
 @JSONMappedBy(
        mappedClass = Example01.class
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JSONExample01 implements Serializable {

    @JsonProperty("field01")
    private String field01;

    @JsonProperty("field02")
    private Integer field02;

    @JsonProperty("field03")
    private Double field03;

    @JsonProperty("string_to_int_map")
    private Map<String, Integer> stringToIntMap;

    ...

```
Getter and setter methods are being generated as necessary.


### Fields that are @JSONMapped

Fields of classes which are annotated by @JSONMapped themselves will be mapped into the appropriate (generated) mapping class of this field. Mapping of values happens automatically.

```
@JSONMapped(fluentAccessors = false)
public class Person {

	private String firstName;
	private String middleName;
	private String surName; 
	
	Address address;	
	Contact contact;
	
}
```
if *Address* and *Contact* are annotated with *@JSONMapped*, the mapping class would be generated as follows:
 
```
@JSONMappedBy(
        mappedClass = Person.class
)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class JSONPerson implements Serializable {
    static final long serialVersionUID = -1L;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("middle_name")
    private String middleName;

    @JsonProperty("sur_name")
    private String surName;

    @JsonProperty("address")
    private JSONAddress address;

    @JsonProperty("contact")
    private JSONContact contact;
    ...
}
```


### Collections/Sets/Maps with type arguments that are @JSONMapped

All type arguments of Collections/Sets/Maps are mapped automatically into their mapping counterpart if these classes are annotated with *@JSONMapped* by themself.

Take an annotated class *Person* as an example:

```
@Getter
@Setter
@JSONMapped(fluentAccessors = false)
public class Person {

	private String firstName;

	private String middleName;
	
	private String surName; 
	
	private LocalDate dateOfBirth;
	
	List<Address> addresses;
	
	List<Contact> contacts;

}
```
Assumed the prefix is *JSON* as default and the classes *Address* and *Contact* are annotated with @JSONMapped as well, the generated code would result in the following class (methods ommitted)
```
@JSONMappedBy(
        mappedClass = Person.class
)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class JSONPerson implements Serializable {
    static final long serialVersionUID = -1L;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("middle_name")
    private String middleName;

    @JsonProperty("sur_name")
    private String surName;

    @JsonFormat(
            pattern = "yyyy-MM-dd"
    )
    @JsonProperty("date_of_birth")
    private LocalDate dateOfBirth;

    @JsonProperty("addresses")
    private List<JSONAddress> addresses;

    @JsonProperty("contacts")
    private List<JSONContact> contacts;
    ...
}
```
As it can be seen, *Contacts* has been mapped by *JSONContact* and Address has been mapped by *JSONAddress*. The mapping of the contents of these types is handled by the code in the generated class
when the *of()* or *to()* methods are being called.


### LocalDate and LocalDateTime

Support for jsr-310 compatible handling of *java.time.LocalDate* and *java.time.LocalDateTime* has been added. The output format of dates and timestamps can be modified by the provision of patterns in *datePattern* or *dateTimePattern* resp.


### toJSONString()

Additionally to the usual *toString()* method and additional *toJSONString()* method is generated that prints the name and contents of the class as formatted JSON.

```
net.magiccode.lazy.json.JSONExampleCode04{
  "another_field" : 90,
  "value_list" : [ "abc", "def", "ghi", "xyz" ],
  "third_field" : -23.4567,
  "boolean_field" : true,
  "example02" : null
}
```


## Libraries

At the time being, the dependencies used by *kilauea* are

```
	<properties>
		<java.version>17</java.version>
		<jackson.version>2.15.0</jackson.version>
		<log4j.version>2.21.1</log4j.version>
		<lombok.version>1.18.30</lombok.version>
		<javapoet.version>1.13.0</javapoet.version>
		<auto-service.version>1.1.1</auto-service.version>
	</properties>

	...

	<dependency>
		<groupId>com.fasterxml.jackson.core</groupId>
		<artifactId>jackson-core</artifactId>
		<version>${jackson.version}</version>
	</dependency>

	<dependency>
		<groupId>com.fasterxml.jackson.core</groupId>
		<artifactId>jackson-databind</artifactId>
		<version>${jackson.version}</version>
	</dependency>

	<dependency>
		<groupId>com.fasterxml.jackson.core</groupId>
		<artifactId>jackson-annotations</artifactId>
		<version>${jackson.version}</version>
	</dependency>

	<dependency>
		<groupId>com.fasterxml.jackson.datatype</groupId>
		<artifactId>jackson-datatype-jsr310</artifactId>
		<version>${jackson.version}</version>
	</dependency>
		
	<dependency>
	    <groupId>com.fasterxml.jackson.datatype</groupId>
	    <artifactId>jackson-datatype-jdk8</artifactId>
	    <version>${jackson.version}</version>
	</dependency>

	<dependency>
		<groupId>org.projectlombok</groupId>
		<artifactId>lombok</artifactId>
		<version>${lombok.version}</version>
	</dependency>

	<dependency>
		<groupId>com.squareup</groupId>
		<artifactId>javapoet</artifactId>
		<version>${javapoet.version}</version>
	</dependency>

	<dependency>
		<groupId>com.google.auto.service</groupId>
		<artifactId>auto-service</artifactId>
		<version>${auto-service.version}</version>
	</dependency>

	<dependency>
		<groupId>org.apache.logging.log4j</groupId>
		<artifactId>log4j-core</artifactId>
		<version>${log4j.version}</version>
	</dependency>
```

