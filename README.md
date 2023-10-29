# json-mapper
Generate Java classes with Jackson annotations with some convenient creation/mapping functions.

## Annotations

### @JSONMapped

@JSONMapped initiates the generation of a class that contains the same fields as the annotated class
but annotates these with Jackson annotations for JSON generation.

Example:
```
@JSONMapped(fluentAccessors = false)
```


