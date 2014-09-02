ph-buildinfo-maven-plugin
=========================

A Maven 2/3 plugin that adds build information to the resulting artefacts.
Tested with Maven 2.2.1 and 3.x

## Maven configuration
```xml
      <plugin>
        <groupId>com.helger.maven</groupId>
        <artifactId>ph-buildinfo-maven-plugin</artifactId>
        <version>1.2.1</version>
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

  * `File` **tempDirectory**  
     The directory where the temporary buildinfo files will be saved.  
     Defaults to `${project.build.directory}/buildinfo-maven-plugin`
  * `boolean` **withAllSystemProperties**  
     Should all system properties be emitted into the build info? 
     If this flag is set, the **selectedSystemProperties** are cleared, so either this flag or
     the **selectedSystemProperties** should be used. All contained system properties are prefixed with
     `systemproperty.` in the generated file.  
     Defaults to `false`
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
     The created file is always `META-INF/buildinfo.xml`.  
     The generated file has the following layout:
```xml     
<mapping>
  <map key="buildinfo.version" value="2" />
  <map key="project.groupid" value="com.helger.maven" />
  ...
</mapping>
```

  * `boolean` **formatProperties**  
     Generate build info in .properties format? It is safe to generate multiple formats in one run!  
     Defaults to `false`.  
     The created file is always `META-INF/buildinfo.properties`.
