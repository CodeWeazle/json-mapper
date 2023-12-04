# kilauea
<img src="./img/kilauea.png" alt="kilauea" style="width:400px;"/>

*kilauea* implements an annotation processor to generate Java classes with or without annotations, depending 
on the option set by type. Available are POJO, JSON and XML. 
POJO will create mapping classes without any annotations, JSON generates Jackson annotations and XML generates mapping classes with JaxB annotation.
All generated classes provide code to map an instance of an annotated class into an instance of the generated class populating all fields.

## Usage

### Maven

Add the annotation processor class to your pom.xml and rebuild your project. (The version shown is related to the *release* branch, which is also available on the [maven-central repository](https://central.sonatype.com/).)

```
    <dependencies>
	  ...
      <dependency>
	 	<groupId>net.magiccode</groupId>
		<artifactId>kilauea</artifactId>
		<version>0.1.4</version>
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
						<version>0.1.4</version>
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
implementation group: 'net.magiccode', name: 'kilauea', version: '0.1.4'

...
dependencies {
  compile("net.magiccode:kilauea:0.1.4")
  annotationProcessor("net.magiccode:kilauea:0.1.4")
  ...
}
```

### Others

For other build systems please consult the current [maven-central site](https://central.sonatype.com/artifact/net.magiccode/kilauea)


## Annotations

For more information about how to use the annotations and the generated classes, please consult [the howto document](./HowTo.md)


## Libraries

At the time being, the dependencies used by *kilauea* are

```
	<properties>
		<java.version>17</java.version>
		<jackson.version>2.16.0</jackson.version>
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
		<groupId>com.fasterxml.jackson.module</groupId>
		<artifactId>jackson-module-jaxb-annotations</artifactId>
		<version>${jackson.version}</version>
	</dependency>

	<dependency>
		<groupId>javax.xml.bind</groupId>
		<artifactId>jaxb-api</artifactId>
		<version>${jaxb-api.version}</version>
	</dependency>

	<dependency>
		<groupId>org.glassfish.jaxb</groupId>
		<artifactId>jaxb-runtime</artifactId>
		<version>${jaxb-runtime.version}</version>
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

