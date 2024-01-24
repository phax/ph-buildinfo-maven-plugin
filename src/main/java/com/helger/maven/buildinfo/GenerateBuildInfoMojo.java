/*
 * Copyright (C) 2014-2024 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.maven.buildinfo;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import com.helger.commons.CGlobal;
import com.helger.commons.collection.CollectionHelper;
import com.helger.commons.datetime.PDTFactory;
import com.helger.commons.io.EAppend;
import com.helger.commons.io.file.FileHelper;
import com.helger.commons.io.file.FileIOError;
import com.helger.commons.io.file.FileOperations;
import com.helger.commons.io.resource.FileSystemResource;
import com.helger.commons.lang.NonBlockingProperties;
import com.helger.commons.regex.RegExHelper;
import com.helger.commons.string.StringHelper;
import com.helger.commons.system.SystemProperties;
import com.helger.json.IJsonArray;
import com.helger.json.JsonArray;
import com.helger.json.JsonObject;
import com.helger.json.serialize.JsonWriter;
import com.helger.json.serialize.JsonWriterSettings;
import com.helger.xml.microdom.util.XMLMapHandler;

/**
 * @author Philip Helger
 * @description Create build information at compile time. The information will
 *              be part of the created JAR/WAR/... file. The resulting file will
 *              reside in the <code>META-INF</code> directory of the created
 *              artifact.
 */
@Mojo (name = "generate-buildinfo", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public final class GenerateBuildInfoMojo extends AbstractMojo
{
  /** The name of the XML file */
  private static final String DEFAULT_FILENAME_BUILDINFO_XML = "buildinfo.xml";
  /** The name of the properties file */
  private static final String DEFAULT_FILENAME_BUILDINFO_PROPERTIES = "buildinfo.properties";
  /** The name of the JSON file */
  private static final String DEFAULT_FILENAME_BUILDINFO_JSON = "buildinfo.json";

  @Parameter (property = "project", required = true, readonly = true)
  private MavenProject project;

  @Parameter (property = "reactorProjects", required = true, readonly = true)
  private List <MavenProject> reactorProjects;

  /**
   * A set of ignored packagings for which the buildinfo plugin is not executed.
   * Note: to use more values as default values, use a comma separated list such
   * as "pom,pom2,pom3" etc.
   *
   * @since 2.1.0
   */
  @Parameter (property = "ignoredPackagings", defaultValue = "pom")
  private HashSet <String> ignoredPackagings;

  /**
   * The directory where the temporary buildinfo files will be saved.
   */
  @Parameter (property = "tempDirectory", defaultValue = "${project.build.directory}/buildinfo-maven-plugin", required = true)
  private File tempDirectory;

  /**
   * Set the time zone to be used. Use "UTC" for the universal timezone.
   * Otherwise Strings like "Europe/Vienna" should be used. If unspecified, the
   * system default time zone is used.
   */
  @Parameter (property = "timeZone")
  private String timeZone;

  /**
   * Should all system properties be emitted into the build info? If this flag
   * is set, the selectedSystemProperties are cleared, so either this flag or
   * the selectedSystemProperties should be used.<br>
   * All contained system properties are prefixed with
   * <code>systemproperty.</code> in the generated file.
   */
  @Parameter (property = "withAllSystemProperties", defaultValue = "false")
  private boolean withAllSystemProperties = false;

  /**
   * A selected subset of system property names to be emitted. Each element can
   * be a regular expression to match more than one potential system property.
   * If this set is not empty, the withSystemProperties property should not need
   * to be enabled.<br>
   * All contained system properties are prefixed with
   * <code>systemproperty.</code> in the generated file.
   */
  @Parameter (property = "selectedSystemProperties")
  private HashSet <String> selectedSystemProperties;

  /**
   * A selected subset of system property names to be ignored. Each element can
   * be a regular expression to match more than one potential system property.
   * Ignored system properties take precedence over selected system properties.
   * They are also ignored if withAllSystemProperties is set to
   * <code>true</code>.
   */
  @Parameter (property = "ignoredSystemProperties")
  private HashSet <String> ignoredSystemProperties;

  /**
   * Should all environment variables be emitted into the build info? If this
   * flag is set, the selectedEnvVars are cleared, so either this flag or the
   * selectedEnvVars should be used.<br>
   * All contained environment variables are prefixed with <code>envvar.</code>
   * in the generated file.
   */
  @Parameter (property = "withAllEnvVars", defaultValue = "false")
  private boolean withAllEnvVars = false;

  /**
   * A selected subset of environment variables names to be emitted. Each
   * element can be a regular expression to match more than one potential
   * environment variables. If this set is not empty, the withEnvVars property
   * does not need to be enabled.<br>
   * All contained environment variables are prefixed with <code>envvar.</code>
   * in the generated file.
   */
  @Parameter (property = "selectedEnvVars")
  private HashSet <String> selectedEnvVars;

  /**
   * A selected subset of environment variables names to be ignored. Each
   * element can be a regular expression to match more than one potential
   * environment variables. Ignored environment variables take precedence over
   * selected environment variables. They are also ignored if withAllEnvVars is
   * set to <code>true</code>.
   */
  @Parameter (property = "ignoredEnvVars")
  private HashSet <String> ignoredEnvVars;

  /**
   * Generate build info in .XML format? It is safe to generate multiple formats
   * in one run!<br>
   * The generated file has the following layout:
   *
   * <pre>
   * &lt;mapping&gt;
   *   &lt;map key="buildinfo.version" value="3" /&gt;
   *   &lt;map key="project.groupid" value="com.helger.maven" /&gt;
   *   ...
   * &lt;/mapping&gt;
   * </pre>
   */
  @Parameter (property = "formatXML", defaultValue = "true")
  private boolean formatXML = true;

  /**
   * Generate build info in .properties format? It is safe to generate multiple
   * formats in one run!
   */
  @Parameter (property = "formatProperties", defaultValue = "false")
  private boolean formatProperties = false;

  /**
   * Generate build info in .json format? It is safe to generate multiple
   * formats in one run!
   *
   * @since 2.1.0
   */
  @Parameter (property = "formatJson", defaultValue = "false")
  private boolean formatJson = false;

  /**
   * Set the target path inside the final artefact where the files should be
   * located.
   *
   * @since 2.1.0
   */
  @Parameter (property = "targetPath", defaultValue = "META-INF", required = true)
  private String targetPath;

  // Important: parameter type must match member type!
  public void setIgnoredPackagings (final Set <String> aCollection)
  {
    ignoredPackagings = new HashSet <> ();
    if (aCollection != null)
    {
      for (final String sName : aCollection)
        if (StringHelper.hasText (sName))
          if (!ignoredPackagings.add (sName))
            getLog ().warn ("The ignored packaging '" + sName + "' is contained more than once");
    }
  }

  public void setTempDirectory (@Nonnull final File aDir) throws MojoExecutionException
  {
    if (aDir == null)
      throw new MojoExecutionException ("No buildinfo temp directory specified!");
    if (aDir.exists () && !aDir.isDirectory ())
      throw new MojoExecutionException ("The specified buildinfo temp directory " + aDir + " is not a directory!");

    tempDirectory = aDir;
    if (!tempDirectory.isAbsolute ())
      tempDirectory = new File (project.getBasedir (), aDir.getPath ());
  }

  public void setTimeZone (final String sTimeZone)
  {
    try
    {
      // Try to resolve ID -> throws exception if unknown
      final ZoneId aDefaultZoneId = ZoneId.of (sTimeZone);

      // getTimeZone falls back to GMT if unknown
      final TimeZone aDefaultTimeZone = TimeZone.getTimeZone (aDefaultZoneId);
      TimeZone.setDefault (aDefaultTimeZone);
      timeZone = sTimeZone;
    }
    catch (final DateTimeException ex)
    {
      // time zone ID is unknown
      getLog ().warn ("Unknown time zone '" + sTimeZone + "'");
    }
  }

  public void setWithAllSystemProperties (final boolean bEnable)
  {
    withAllSystemProperties = bEnable;
    if (withAllSystemProperties)
    {
      // No selection if we have all system properties
      if (selectedSystemProperties != null && !selectedSystemProperties.isEmpty ())
      {
        getLog ().warn ("Clearing all selected system properties, because all system properties are enabled!");
        setSelectedSystemProperties (null);
      }
    }
  }

  // Important: parameter type must match member type!
  public void setSelectedSystemProperties (final Set <String> aCollection)
  {
    selectedSystemProperties = new HashSet <> ();
    if (aCollection != null)
    {
      for (final String sName : aCollection)
        if (StringHelper.hasText (sName))
          if (!selectedSystemProperties.add (sName))
            getLog ().warn ("The selected system property '" + sName + "' is contained more than once");
    }
    if (!selectedSystemProperties.isEmpty ())
    {
      // If we have a set of selected, don't use all system properties
      if (withAllSystemProperties)
      {
        getLog ().warn ("Disabling all system properties, because selected system properties are defined!");
        setWithAllSystemProperties (false);
      }
    }
  }

  // Important: parameter type must match member type!
  public void setIgnoredSystemProperties (final Set <String> aCollection)
  {
    ignoredSystemProperties = new HashSet <> ();
    if (aCollection != null)
    {
      for (final String sName : aCollection)
        if (StringHelper.hasText (sName))
          if (!ignoredSystemProperties.add (sName))
            getLog ().warn ("The ignored system property '" + sName + "' is contained more than once");
    }
  }

  public void setWithAllEnvVars (final boolean bEnable)
  {
    withAllEnvVars = bEnable;
    if (withAllEnvVars)
    {
      // No selection if we have all environment variables
      if (selectedEnvVars != null && !selectedEnvVars.isEmpty ())
      {
        getLog ().warn ("Clearing all environment variables, because all environment variables are enabled!");
        setSelectedEnvVars (null);
      }
    }
  }

  // Important: parameter type must match member type!
  public void setSelectedEnvVars (final Set <String> aCollection)
  {
    selectedEnvVars = new HashSet <> ();
    if (aCollection != null)
    {
      for (final String sName : aCollection)
        if (StringHelper.hasText (sName))
          if (!selectedEnvVars.add (sName))
            getLog ().warn ("The selected environment variable '" + sName + "' is contained more than once");
    }
    if (!selectedEnvVars.isEmpty ())
    {
      // If we have a set of selected, don't use all
      if (withAllEnvVars)
      {
        getLog ().warn ("Disabling all environment variables, because selected environment variables are defined!");
        setWithAllEnvVars (false);
      }
    }
  }

  // Important: parameter type must match member type!
  public void setIgnoredEnvVars (final Set <String> aCollection)
  {
    ignoredEnvVars = new HashSet <> ();
    if (aCollection != null)
    {
      for (final String sName : aCollection)
        if (StringHelper.hasText (sName))
          if (!ignoredEnvVars.add (sName))
            getLog ().warn ("The ignored environment variable '" + sName + "' is contained more than once");
    }
  }

  public void setFormatXML (final boolean bEnable)
  {
    formatXML = bEnable;
  }

  public void setFormatProperties (final boolean bEnable)
  {
    formatProperties = bEnable;
  }

  public void setFormatJson (final boolean bEnable)
  {
    formatJson = bEnable;
  }

  public void setTargetPath (final String sTargetPath) throws MojoExecutionException
  {
    if (sTargetPath == null)
      throw new MojoExecutionException ("targetPath may not be null");
    targetPath = sTargetPath;
  }

  private static boolean _matches (@Nullable final Set <String> aSet, @Nonnull final String sName)
  {
    if (aSet == null)
      return false;

    // Direct match?
    if (aSet.contains (sName))
      return true;

    // RegEx match?
    for (final String sSelected : aSet)
      if (RegExHelper.stringMatchesPattern (sSelected, sName))
        return true;

    // No match!
    return false;
  }

  @Nonnull
  private JsonProps _determineBuildInfoProperties ()
  {
    // Get the current time, using the time zone specified in the settings
    final LocalDateTime aDT = PDTFactory.getCurrentLocalDateTime ();

    // Build the default properties
    final JsonProps aProps = new JsonProps ();
    // Version 1: initial
    // Version 2: added dependency information; added per build plugin the key
    // property
    // Version 3: added active profiles
    aProps.getChild ("buildinfo").add ("version", "3");

    // Project information
    aProps.getChild ("project").add ("groupid", project.getGroupId ());
    aProps.getChild ("project").add ("artifactid", project.getArtifactId ());
    aProps.getChild ("project").add ("version", project.getVersion ());
    aProps.getChild ("project").add ("name", project.getName ());
    aProps.getChild ("project").add ("packaging", project.getPackaging ());

    // Parent project information (if available)
    final MavenProject aParentProject = project.getParent ();
    if (aParentProject != null)
    {
      aProps.getChild ("parentproject").add ("groupid", aParentProject.getGroupId ());
      aProps.getChild ("parentproject").add ("artifactid", aParentProject.getArtifactId ());
      aProps.getChild ("parentproject").add ("version", aParentProject.getVersion ());
      aProps.getChild ("parentproject").add ("name", aParentProject.getName ());
    }

    // All reactor projects (nested projects)
    // Don't emit this, if this is "1" as than only the current project would be
    // listed
    if (reactorProjects != null && reactorProjects.size () != 1)
    {
      final IJsonArray aList = new JsonArray ();

      // Show details of all reactor projects, index starting at 0
      for (final MavenProject aReactorProject : reactorProjects)
      {
        final JsonProps aSubProps = new JsonProps ();
        aSubProps.add ("groupid", aReactorProject.getGroupId ());
        aSubProps.add ("artifactid", aReactorProject.getArtifactId ());
        aSubProps.add ("version", aReactorProject.getVersion ());
        aSubProps.add ("name", aReactorProject.getName ());
        aList.add (aSubProps);
      }
      aProps.addJson ("reactorproject", aList);
    }

    // Build Plugins
    final List <Plugin> aBuildPlugins = project.getBuildPlugins ();
    if (aBuildPlugins != null)
    {
      final IJsonArray aList = new JsonArray ();

      // Show details of all plugins, index starting at 0
      for (final Plugin aPlugin : aBuildPlugins)
      {
        final JsonProps aSubProps = new JsonProps ();
        aSubProps.add ("groupid", aPlugin.getGroupId ());
        aSubProps.add ("artifactid", aPlugin.getArtifactId ());
        aSubProps.add ("version", aPlugin.getVersion ());
        final Object aConfiguration = aPlugin.getConfiguration ();
        if (aConfiguration != null)
        {
          // Will emit an XML structure!
          aSubProps.add ("configuration", aConfiguration.toString ());
        }
        aSubProps.add ("key", aPlugin.getKey ());
        aList.add (aSubProps);
      }

      aProps.getChild ("build").addJson ("plugin", aList);
    }

    // Build dependencies
    final List <Dependency> aDependencies = project.getDependencies ();
    if (aDependencies != null)
    {
      final IJsonArray aList = new JsonArray ();
      // Show details of all dependencies
      for (final Dependency aDependency : aDependencies)
      {
        final JsonProps aDepProps = new JsonProps ();
        aDepProps.add ("groupid", aDependency.getGroupId ());
        aDepProps.add ("artifactid", aDependency.getArtifactId ());
        aDepProps.add ("version", aDependency.getVersion ());
        aDepProps.add ("type", aDependency.getType ());
        if (aDependency.getClassifier () != null)
          aDepProps.add ("classifier", aDependency.getClassifier ());
        aDepProps.add ("scope", aDependency.getScope ());
        if (aDependency.getSystemPath () != null)
          aDepProps.add ("systempath", aDependency.getSystemPath ());
        aDepProps.add ("optional", aDependency.isOptional ());
        aDepProps.add ("managementkey", aDependency.getManagementKey ());

        // Add all exclusions of the current dependency
        final List <Exclusion> aExclusions = aDependency.getExclusions ();
        if (aExclusions != null)
        {
          final IJsonArray aExclusionList = new JsonArray ();
          for (final Exclusion aExclusion : aExclusions)
          {
            aExclusionList.add (new JsonObject ().add ("groupid", aExclusion.getGroupId ())
                                                 .add ("artifactid", aExclusion.getArtifactId ()));
          }
          aDepProps.addJson ("exclusion", aExclusionList);
        }
        aList.add (aDepProps);
      }
      aProps.addJson ("dependency", aList);
    }

    // Active profiles (V3)
    final List <Profile> aActiveProfiles = project.getActiveProfiles ();
    if (aActiveProfiles != null)
    {
      final IJsonArray aList = new JsonArray ();
      for (final Profile aProfile : aActiveProfiles)
      {
        aList.add (new JsonObject ().add ("id", aProfile.getId ()));
      }
      aProps.addJson ("profiles", aList);
    }

    // Build date and time
    final ZoneId aZoneID = timeZone != null ? ZoneId.of (timeZone) : ZoneId.systemDefault ();
    final ZonedDateTime aZonedDT = ZonedDateTime.of (aDT, aZoneID);
    final int nOfsSecs = aZonedDT.getOffset ().getTotalSeconds ();
    aProps.getChildren ("build", "datetime").add ("text", aDT.toString ());
    aProps.getChildren ("build", "datetime").add ("millis", PDTFactory.getMillis (aDT));
    aProps.getChildren ("build", "datetime").add ("date", aDT.toLocalDate ().toString ());
    aProps.getChildren ("build", "datetime").add ("time", aDT.toLocalTime ().toString ());
    aProps.getChildren ("build", "datetime", "timezone").add ("id", aZoneID.getId ());
    aProps.getChildren ("build", "datetime", "timezone").add ("offsethours", nOfsSecs / CGlobal.SECONDS_PER_HOUR);
    aProps.getChildren ("build", "datetime", "timezone").add ("offsetmins", nOfsSecs / CGlobal.SECONDS_PER_MINUTE);
    aProps.getChildren ("build", "datetime", "timezone").add ("offsetsecs", nOfsSecs);
    aProps.getChildren ("build", "datetime", "timezone").add ("offsetmillisecs", nOfsSecs * CGlobal.MILLISECONDS_PER_SECOND);

    // Emit system properties?
    if (withAllSystemProperties || CollectionHelper.isNotEmpty (selectedSystemProperties))
      for (final Map.Entry <String, String> aEntry : CollectionHelper.getSortedByKey (SystemProperties.getAllProperties ()).entrySet ())
      {
        final String sName = aEntry.getKey ();
        if (withAllSystemProperties || _matches (selectedSystemProperties, sName))
          if (!_matches (ignoredSystemProperties, sName))
            aProps.getChild ("systemproperty").add (sName, aEntry.getValue ());
      }

    // Emit environment variable?
    if (withAllEnvVars || CollectionHelper.isNotEmpty (selectedEnvVars))
      for (final Map.Entry <String, String> aEntry : CollectionHelper.getSortedByKey (System.getenv ()).entrySet ())
      {
        final String sName = aEntry.getKey ();
        if (withAllEnvVars || _matches (selectedEnvVars, sName))
          if (!_matches (ignoredEnvVars, sName))
            aProps.getChild ("envvar").add (sName, aEntry.getValue ());
      }

    return aProps;
  }

  private void _writeBuildinfoXMLv1 (@Nonnull final JsonProps aProps) throws MojoExecutionException
  {
    // Write the XML in the format that it can easily be read by the
    // com.helger.common.microdom.reader.XMLMapHandler class
    final File aFile = new File (tempDirectory, DEFAULT_FILENAME_BUILDINFO_XML);
    if (XMLMapHandler.writeMap (aProps.getAsFlatList (), new FileSystemResource (aFile)).isFailure ())
      throw new MojoExecutionException ("Failed to write XML file to " + aFile);
    getLog ().debug ("Wrote buildinfo XML file to " + aFile);
  }

  private void _writeBuildinfoProperties (@Nonnull final JsonProps aProps) throws MojoExecutionException
  {
    // Write properties file
    final File aFile = new File (tempDirectory, DEFAULT_FILENAME_BUILDINFO_PROPERTIES);
    final NonBlockingProperties p = new NonBlockingProperties ();
    p.putAll (aProps.getAsFlatList ());
    try
    {
      p.store (FileHelper.getOutputStream (aFile), "Generated - do not edit!");
    }
    catch (final IOException ex)
    {
      throw new MojoExecutionException ("Failed to write properties file to " + aFile, ex);
    }
    getLog ().debug ("Wrote buildinfo properties file to " + aFile);
  }

  private void _writeBuildinfoJson (@Nonnull final JsonProps aProps) throws MojoExecutionException
  {
    // Write properties file
    final File aFile = new File (tempDirectory, DEFAULT_FILENAME_BUILDINFO_JSON);
    try (final Writer aWriter = FileHelper.getBufferedWriter (aFile, EAppend.TRUNCATE, StandardCharsets.UTF_8))
    {
      new JsonWriter (JsonWriterSettings.DEFAULT_SETTINGS_FORMATTED).writeToWriter (aProps, aWriter);
    }
    catch (final IOException ex)
    {
      throw new MojoExecutionException ("Failed to write JSON file to " + aFile, ex);
    }
    getLog ().debug ("Wrote buildinfo JSON file to " + aFile);
  }

  public void execute () throws MojoExecutionException
  {
    if (ignoredPackagings != null && ignoredPackagings.contains (project.getPackaging ()))
    {
      // Do not execute for "POM" only projects
      getLog ().info ("Not executing buildinfo plugin because the packaging '" + project.getPackaging () + "' is ignored.");
      return;
    }

    final FileIOError aResult = FileOperations.createDirRecursiveIfNotExisting (tempDirectory);
    if (aResult.isFailure ())
      getLog ().error ("Failed to create temp directory " + tempDirectory.getName () + ": " + aResult.toString ());
    else
      getLog ().info ("Successfully created temp directory " + tempDirectory.getName ());

    if (!formatXML && !formatProperties && !formatJson)
      throw new MojoExecutionException ("No buildinfo output format was specified. Nothing will be generated!");

    final JsonProps aProps = _determineBuildInfoProperties ();

    if (formatXML)
      _writeBuildinfoXMLv1 (aProps);

    if (formatProperties)
      _writeBuildinfoProperties (aProps);

    if (formatJson)
      _writeBuildinfoJson (aProps);

    // Add output directory as a resource-directory
    final Resource aResource = new Resource ();
    aResource.setDirectory (tempDirectory.getAbsolutePath ());
    aResource.addInclude ("**/*");
    aResource.setFiltering (false);
    aResource.setTargetPath (targetPath);
    project.addResource (aResource);
  }
}
