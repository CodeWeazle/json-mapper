# json-mapper
Generate Java classes with Jackson annotations with some convenient creation/mapping functions.

## Annotations

### @JSONMapped

@JSONMapped can be used on class level and initiates the generation of a class that contains the same fields as the annotated class
but annotates these with Jackson annotations for JSON generation.

Example:
```
@JSONMapped(fluentAccessors = false)
```

Some arguments can be provided to have some influence on the code generation.

- useLombok (true|false)
  - Setting useLombok to true generates much less code, because getters and setters can be replace by lombok annotations, just as the constructor(s), toString etc. 

- fluentAccessors (true|false)
  - Creates getters and setters that do not start with **get**, **is** or **set**"** rather than the actual name of the field. If useLombok is **true**, this setting is passed on.

- chainedSetters (true|false)
  - Generates setters which return **this**. 

- prefix 
  - Adds a prefix to the name of the generated class. Defaults to **JSON**

- packageName
  - Defines the name of the packacke for the generated class. If no **packageName** is given, this defaults to the package of the annotated class.

- subpackageName
  - Defines the name for a subpackage added to the default if **packageName** is not specified.
	
- jsonInclude (ALWAYS|NON_NULL|NON_ABSENT|NON_EMPTY|NON_DEFAULT|CUSTOM|USE_DEFAULTS)
  - Generated classes are annotated with **@JsonInclude**. This defaults to ALWAYS, but can be specified otherwise by using the **jsonInclude** argument.

