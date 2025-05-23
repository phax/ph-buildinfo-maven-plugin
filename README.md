# ph-buildinfo-maven-plugin

[![javadoc](https://javadoc.io/badge2/com.helger.maven/ph-buildinfo-maven-plugin/javadoc.svg)](https://javadoc.io/doc/com.helger.maven/ph-buildinfo-maven-plugin)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.helger.maven/ph-buildinfo-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.helger.maven/ph-buildinfo-maven-plugin) 

A Maven 3 plugin that adds build information to the resulting artefacts.
It allows to add an XML and/or a Properties file to the resulting artefact.

## Maven configuration

```xml
<plugin>
  <groupId>com.helger.maven</groupId>
  <artifactId>ph-buildinfo-maven-plugin</artifactId>
  <version>4.0.1</version>
  <executions>
    <execution>
      <goals>
        <goal>generate-buildinfo</goal>
      </goals>
    </execution>
  </executions>
  <configuration>
    <formatProperties>false</formatProperties>
    <formatXML>true</formatXML>
    <formatJson>false</formatJson>
    <withAllSystemProperties>true</withAllSystemProperties>
    <selectedEnvVars>
      <param>JAVA_.*</param>
      <param>M2_.*</param>
      <param>MAVEN_.*</param>
      <param>NUMBER_OF_PROCESSORS</param>
      <param>OS</param>
      <param>PROCESSOR_.*</param>
    </selectedEnvVars>
  </configuration>
</plugin>
```

Configuration items are:

* `HashSet <String>` **ignoredPackagings**  
  A set of ignored packagings for which the buildinfo plugin is not executed.
  Defaults to `pom`.
  Since v2.1.0 
* `File` **tempDirectory**  
  The directory where the temporary buildinfo files will be saved.  
  Defaults to `${project.build.directory}/buildinfo-maven-plugin`
* `boolean` **withAllSystemProperties**  
  Should all system properties be emitted into the build info? 
  If this flag is set, the **selectedSystemProperties** are cleared, so either this flag or
  the **selectedSystemProperties** should be used. All contained system properties are prefixed with
  `systemproperty.` in the generated file.  
  Defaults to `false`.
* `HashSet <String>` **selectedSystemProperties**  
   A selected subset of system property names 
   to be emitted. Each element can be a regular expression to match more than one potential 
   system property. If this set is not empty, the **withSystemProperties** property should not 
   need to be enabled. All contained system properties are prefixed with `systemproperty.`
   in the generated file.
* `HashSet<String>` **ignoredSystemProperties**  
   A selected subset of system property names 
   to be ignored. Each element can be a regular expression to match more than one potential system
   property. Ignored system properties take precedence over selected system properties. 
   They are also ignored if **withAllSystemProperties** is set to `true`.
* `boolean` **withAllEnvVars**  
   Should all environment variables be emitted into the build info? If this flag is set, 
   the selectedEnvVars are cleared, so either this flag or the **selectedEnvVars** should be used.
   All contained environment variables are prefixed with `envvar.` in the generated file.  
   Defaults to `false`.
* `HashSet <String>` **selectedEnvVars**  
   A selected subset of environment variables names to be emitted. Each element can be 
   a regular expression to match more than one potential environment variables. 
   If this set is not empty, the **withEnvVars** property does not need to be enabled.
   All contained environment variables are prefixed with `envvar.` in the generated file.
* `HashSet <String>` **ignoredEnvVars**  
   A selected subset of environment variables names to be ignored. Each element can be a 
   regular expression to match more than one potential environment variables. Ignored 
   environment variables take precedence over selected environment variables.
   They are also ignored if withAllEnvVars is set to `true`.
* `boolean` **formatXML**  
   Generate build info in .XML format? It is safe to generate multiple formats in one run!  
   Defaults to `true`.  
   The created file is always **targetPath** + `buildinfo.xml`.  
   The generated file has the following layout:
```xml     
<mapping>
  <map key="buildinfo.version" value="3" />
  <map key="project.groupid" value="com.helger.maven" />
  ...
</mapping>
```

* `boolean` **formatProperties**  
  Generate build info in .properties format? It is safe to generate multiple formats in one run!  
  Defaults to `false`.
  The created file is always **targetPath** + `buildinfo.properties`.
* `boolean` **formatJson**  
  Generate build info in .json format? It is safe to generate multiple formats in one run!  
  Defaults to `false`.
  The created file is always **targetPath** + `buildinfo.json`.
  Since v2.1.0.
* `String` **targetPath**  
  Set the target path inside the final artefact where the files should be located.
  Defaults to `META-INF`.
  Since v2.1.0.

# News and noteworthy

* v4.0.1 - 2023-07-01
    * Make sure all Maven dependencies are in scope `provided`
    * Removed the `maven-compat` dependencies for Maven 4 compatibility
* v4.0.0 - 2023-01-08
    * Using Java 11 as the baseline
    * Updated to ph-commons 11
* v3.0.2 - 2021-03-22
    * Updated to ph-commons 10
* v3.0.1 - 2020-03-11
    * Release with recent library versions
* v3.0.0 - 2018-08-06
    * Updated to ph-commons 9.0.0
* v2.1.0 - 2017-04-11
    * Updated buildinfo version number to `3`
        * List of active profiles were added to build info output
        * Changed property name `build.datetime` to `build.datetime.text`
        * Changed property name `build.datetime.timezone` to `build.datetime.timezone.id`
    * Switched to Maven plugin annotations
    * Marked as thread-safe
    * Timezone is now considered
    * Added new property `ignoredPackagings` to define Maven packagings to be ignored for this plugin.
    * Added new property `targetPath` to define the path in the final artefact
    * Added support for writing JSON buildinfo files
* v2.0.0 - 2016-07-01
    * Updated to Java 8
* v1.3.0 - 2015-08-31
    * First version to require Maven 3.0
    * Removed manual SLF4J integration
    * Added support for Eclipse m2e plugin
* v1.2.2 - 2015-03-11
    * Last version to support Maven 2.x
* v1.2.1 - 2014-09-02
    * Tried to make compatible with Maven 2.2.1
* v1.2.0 - 2014-08-26

---

My personal [Coding Styleguide](https://github.com/phax/meta/blob/master/CodingStyleguide.md) |
It is appreciated if you star the GitHub project if you like it.