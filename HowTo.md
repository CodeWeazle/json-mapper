# kilauea

This short guide is intended to give you an introduction into the usage of *kilauea*.
Depending on the type of class you want to be generated, some options are applicable or not.


## Usage


## Annotations

### @Mapped

The most important annotation is *@Mapped*, since it's presence starts the class generation at compile time.

*@Mapped* can be used on class level and initiates the generation of a class that contains the same fields as the annotated class. Depending on the **type** argument provided, the generated classes can be generated with Jackson
annotations for JSON processing or Jaxb annotations for XML processing. 


Example:
```
@Mapped(type = GeneratorType.JSON, fluentAccessors = false)
```

Some arguments can be provided to have some influence on the code generation. Options applicable for all *GeneratorType* settings:

| argument | values | Description |
| --- | --- | -- |
|type |**POJO**, JSON, XML|Defines the type of mapped class to be generated. POJO creates a mapping without any annotations, JSON adds Jackson (version 2.16.0) annotations and XML adds JaxB annotations to fields of the generated class. Also, some options are only valid for a certain *GeneratorType*.|
|useLombok |true, **false**|Setting useLombok to true generates much less code, because getters and setters can be replaced by lombok annotations, just as the constructor(s), toString etc.|
|fluentAccessors |true, **false**|Creates getters and setters that do not start with *get*, *is* or *set* rather than the actual name of the field. If useLombok is *true*, this setting is passed on to @Accessors(fluent=true&#124;false).|
|chainedSetters |**true**, false|Generates setters which return *this*. |
|prefix | JSON |Adds a prefix to the name of the generated class. Defaults to "JSON"|
|packageName| |Defines the name of the packacke for the generated class. If no *packageName* is given, this defaults to the package of the annotated class.|
|subpackageName| json |Defines the name for a subpackage added to the default if *packageName* is not specified. The default value is *json*|
|superClass| |Fully qualified name of the superclass that the generated class will extend.|
|interfaces| |Comma separated list of fully qualified name of the interfaces that the generated class will implement.|
|inheritFields|**true**, false|Defines whether or not fields from the super-class hierarchy of the annotated class should be generated. Default is **true**|

Options only applicable for *GeneratorType.JSON*

| argument | values | Description |
| --- | --- | -- |
|jsonInclude |**ALWAYS**, NON_NULL, NON_ABSENT, NON_EMPTY, NON_DEFAULT, CUSTOM, USE_DEFAULTS|Generated classes are annotated with *@JsonInclude*. This defaults to ALWAYS, but can be specified otherwise by using the *jsonInclude* argument.|
|datePattern| "yyyy-MM-dd" |Pattern for *@JsonFormat* generated for *LocalDate* fields in the generated class |
|dateTimePattern| "yyyy-MM-dd HH:mm:ss" |Pattern for *@JsonFormat* generated for *LocalDateTime* fields in the generated class |

Options only applicable for *GeneratorType.XML*

| argument | values | Description |
| --- | --- | -- |
|xmlns|   |default namespace to be generated for this class. The namespace can be overwritten on field level using the  *XMLNamespace* annotation|

### @POJOTransient, @JSONTransien and @XMLTransient

These annotation work on field level and are used to mark fields that will be 

- for type=POJO: generate a final field 
- for type=JSON: generate a field annotated with *@JsonIgnore* in the generated code. (Fields already annotated with *@JsonIgnore* can omit this annotation, because *@JsonIgnore* is picked up as well.)
- for type=XML: generate a field annotated with *@XmlTransient* in the generated code. (Fields already annotated with *@XmlTransient* can omit this annotation, because *@XmlTransient* is picked up as well.)

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

### @JSONRequired, @XMLRequired

These annotation work on field level and are used to mark fields that will be 

- for type=POJO: N/A
- for type=JSON: Every field in the generated class will be annotated with *@JsonProperty* unless marked with *@JSONTransient*. Setting *@JSONRequired* on a field additionally adds the *required = true* argument.
- for type=XML: Every field in the generated class will be annotated with either *@XmlAttribute* or *@XmlElement* unless marked with *@XMLTransient*. Setting *@XMLRequired* on a field additionally adds the *required = true* argument.


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

### @Mappers

The *@Mapped* annotation is now repeatable. To generate multiple classes, use this wrapper annotation like so:

```
@Mappers ({
	@Mapped(type=GeneratorType.JSON,fluentAccessors = true, inheritFields = true, jsonInclude = Include.NON_NULL),
	@Mapped(type=GeneratorType.POJO,fluentAccessors = true, inheritFields = true),
	@Mapped(type=GeneratorType.XML,fluentAccessors = false, inheritFields = true,
			useLombok = false , 
			xmlns = "urn:net.magiccode.exampleNS")
})
```

### @XMLNamespace

On field level the namespace can be overwritten by the use of the **@XMLNamespace** annotation, providing the namespace as the value.
```
@NMLNamespace("urn:magiccode.net.exampleNS")
...
```

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

The generated classes will have getters and setters for all fields, generated according to the parameters given in the *@Mapped* annotation. This means that if *fluentAccessors=true* was specified, the getter and setter methods will be generated without *get*(*is* resp.) and *set* rather than using the name of the field (always starting with a lowercase letter). If *chainedSetters=true* all setter methods will return *this*, so that setter method calls can be chained like so

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

The code generated by the annotation processor depends on the options specified on the *@Mapped* annotation. If *useLombok* is set to true, a class will be generated
using Lombok annotations and no getters, setters or constructors will be created. 
Fields for classes of type JSON or XML, of course, will be generated with (at least) @JsonProperty/@JsonIgnore or @XmlAttribjute/@XmlElement/@XmlTransient annotations in any case.

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

When we add the annotation @Mapped (and assume using the *GeneratorType.JSON* without having set a different prefix but stick to 'JSON' for now), the generated class would look like this:
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


<<<<<<< HEAD
### Fields that are @Mapped with the same *type* option

Fields of classes which are annotated by @Mapped themselves will be mapped into the appropriate (generated) mapping class of this field. Mapping of values happens automatically.
=======
### Fields that are @Mapped (with the same type)

Fields of classes which are annotated by @Mapped themselves and have the same *type* set, will be mapped into the appropriate (generated) mapping class of this field. Mapping of values happens automatically.
>>>>>>> main

```
@Mapped(type=GeneratorType.JSON, fluentAccessors = false)
public class Person {

	private String firstName;
	private String middleName;
	private String surName; 
	
	Address address;	
	Contact contact;
	
}
```
if *Address* and *Contact* are annotated with *@Mapped(type=GeneratorType.JSON)*, the mapping class would be generated as follows:
 
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


### Collections/Sets/Maps with type arguments that are @Mapped

All type arguments of Collections/Sets/Maps are mapped automatically into their mapping counterpart if these classes are annotated with *@Mapped* (with the same type) by themselves.

Take an annotated class *Person* as an example:

```
@Getter
@Setter
@Mapped(type=GeneratorType.JSON, fluentAccessors = false)
public class Person {

	private String firstName;

	private String middleName;
	
	private String surName; 
	
	private LocalDate dateOfBirth;
	
	List<Address> addresses;
	
	List<Contact> contacts;

}
```
Assumed the prefix is *JSON* as default and the classes *Address* and *Contact* are annotated with @Mapped(type=GeneratorType.JSON) as well, the generated code would result in the following class (methods ommitted)
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

