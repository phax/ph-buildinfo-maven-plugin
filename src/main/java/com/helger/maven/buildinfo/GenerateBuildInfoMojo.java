/**
 * Copyright (C) 2014 Philip Helger (www.helger.com)
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.joda.time.DateTime;
import org.slf4j.impl.StaticLoggerBinder;

import com.helger.commons.CGlobal;
import com.helger.commons.SystemProperties;
import com.helger.commons.collections.ContainerHelper;
import com.helger.commons.io.file.FileIOError;
import com.helger.commons.io.file.FileOperations;
import com.helger.commons.io.file.FileUtils;
import com.helger.commons.io.resource.FileSystemResource;
import com.helger.commons.microdom.reader.XMLMapHandler;
import com.helger.commons.regex.RegExHelper;
import com.helger.commons.string.StringHelper;
import com.helger.datetime.PDTFactory;
import com.helger.datetime.config.PDTConfig;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author Philip Helger
 * @goal generate-buildinfo
 * @phase generate-resources
 * @description Create build information at compile time. The information will
 *              be part of the created JAR/WAR/... file. The resulting file will
 *              reside in the <code>META-INF</code> directory of the created
 *              artifact.
 */
@SuppressFBWarnings (value = { "UWF_UNWRITTEN_FIELD", "NP_UNWRITTEN_FIELD" }, justification = "set via maven property")
public final class GenerateBuildInfoMojo extends AbstractMojo
{
  /** The name of the XML file */
  private static final String DEFAULT_FILENAME_BUILDINFO_XML = "buildinfo.xml";
  /** The name of the properties file */
  private static final String DEFAULT_FILENAME_BUILDINFO_PROPERTIES = "buildinfo.properties";

  /**
   * The Maven Project.
   *
   * @parameter property=project
   * @required
   * @readonly
   */
  private MavenProject project;

  /**
   * @parameter property=reactorProjects
   * @required
   * @readonly
   */
  private List <MavenProject> reactorProjects;

  /**
   * The directory where the temporary buildinfo files will be saved.
   *
   * @required
   * @parameter property=tempDirectory
   *            default-value="${project.build.directory}/buildinfo-maven-plugin"
   */
  private File tempDirectory;

  /**
   * Set the time zone to be used. Use "UTC" for the universal timezone.
   * Otherwise Strings like "Europe/Vienna" should be used.
   *
   * @parameter property="timeZone"
   */
  @SuppressWarnings ("unused")
  private String timeZone;

  /**
   * Should all system properties be emitted into the build info? If this flag
   * is set, the selectedSystemProperties are cleared, so either this flag or
   * the selectedSystemProperties should be used.<br>
   * All contained system properties are prefixed with
   * <code>systemproperty.</code> in the generated file.
   *
   * @parameter property="withAllSystemProperties" default-value="false"
   */
  private boolean withAllSystemProperties = false;

  /**
   * A selected subset of system property names to be emitted. Each element can
   * be a regular expression to match more than one potential system property.
   * If this set is not empty, the withSystemProperties property should not need
   * to be enabled.<br>
   * All contained system properties are prefixed with
   * <code>systemproperty.</code> in the generated file.
   *
   * @parameter property="selectedSystemProperties"
   */
  private HashSet <String> selectedSystemProperties;

  /**
   * A selected subset of system property names to be ignored. Each element can
   * be a regular expression to match more than one potential system property.
   * Ignored system properties take precedence over selected system properties.
   * They are also ignored if withAllSystemProperties is set to
   * <code>true</code>.
   *
   * @parameter property="ignoredSystemProperties"
   */
  private HashSet <String> ignoredSystemProperties;

  /**
   * Should all environment variables be emitted into the build info? If this
   * flag is set, the selectedEnvVars are cleared, so either this flag or the
   * selectedEnvVars should be used.<br>
   * All contained environment variables are prefixed with <code>envvar.</code>
   * in the generated file.
   *
   * @parameter property="withAllEnvVars" default-value="false"
   */
  private boolean withAllEnvVars = false;

  /**
   * A selected subset of environment variables names to be emitted. Each
   * element can be a regular expression to match more than one potential
   * environment variables. If this set is not empty, the withEnvVars property
   * does not need to be enabled.<br>
   * All contained environment variables are prefixed with <code>envvar.</code>
   * in the generated file.
   *
   * @parameter property="selectedEnvVars"
   */
  private HashSet <String> selectedEnvVars;

  /**
   * A selected subset of environment variables names to be ignored. Each
   * element can be a regular expression to match more than one potential
   * environment variables. Ignored environment variables take precedence over
   * selected environment variables. They are also ignored if withAllEnvVars is
   * set to <code>true</code>.
   *
   * @parameter property="ignoredEnvVars"
   */
  private HashSet <String> ignoredEnvVars;

  /**
   * Generate build info in .XML format? It is safe to generate multiple formats
   * in one run!<br>
   * The generated file has the following layout:
   *
   * <pre>
   * &lt;mapping&gt;
   *   &lt;map key="buildinfo.version" value="2" /&gt;
   *   &lt;map key="project.groupid" value="com.helger.maven" /&gt;
   *   ...
   * &lt;/mapping&gt;
   * </pre>
   *
   * @parameter property="formatXML" default-value="true"
   */
  private boolean formatXML = true;

  /**
   * Generate build info in .properties format? It is safe to generate multiple
   * formats in one run!
   *
   * @parameter property="formatProperties" default-value="false"
   */
  private boolean formatProperties = false;

  public void setTempDirectory (@Nonnull final File aDir)
  {
    tempDirectory = aDir;
    if (!tempDirectory.isAbsolute ())
      tempDirectory = new File (project.getBasedir (), aDir.getPath ());
    final FileIOError aResult = FileOperations.createDirRecursiveIfNotExisting (tempDirectory);
    if (aResult.isFailure ())
      getLog ().error ("Failed to create temp directory " + aResult.toString ());
    else
      getLog ().info ("Successfully created temp directory " + aDir.toString ());
  }

  @SuppressFBWarnings (value = "URF_UNREAD_FIELD")
  public void setTimeZone (final String sTimeZone)
  {
    if (PDTConfig.setDefaultDateTimeZoneID (sTimeZone).isSuccess ())
      timeZone = sTimeZone;
    else
      getLog ().warn ("Unknown time zone '" + sTimeZone + "'");
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
    selectedSystemProperties = new HashSet <String> ();
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
    ignoredSystemProperties = new HashSet <String> ();
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
    selectedEnvVars = new HashSet <String> ();
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
    ignoredEnvVars = new HashSet <String> ();
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

  private Map <String, String> _determineBuildInfoProperties ()
  {
    // Get the current time, using the time zone specified in the settings
    final DateTime aDT = PDTFactory.getCurrentDateTime ();

    // Build the default properties
    final Map <String, String> aProps = new LinkedHashMap <String, String> ();
    // Version 1: initial
    // Version 2: added dependency information; added per build plugin the key
    // property
    aProps.put ("buildinfo.version", "2");

    // Project information
    aProps.put ("project.groupid", project.getGroupId ());
    aProps.put ("project.artifactid", project.getArtifactId ());
    aProps.put ("project.version", project.getVersion ());
    aProps.put ("project.name", project.getName ());
    aProps.put ("project.packaging", project.getPackaging ());

    // Parent project information (if available)
    final MavenProject aParentProject = project.getParent ();
    if (aParentProject != null)
    {
      aProps.put ("parentproject.groupid", aParentProject.getGroupId ());
      aProps.put ("parentproject.artifactid", aParentProject.getArtifactId ());
      aProps.put ("parentproject.version", aParentProject.getVersion ());
      aProps.put ("parentproject.name", aParentProject.getName ());
    }

    // All reactor projects (nested projects)
    // Don't emit this, if this is "1" as than only the current project would be
    // listed
    if (reactorProjects != null && reactorProjects.size () != 1)
    {
      final String sPrefix = "reactorproject.";

      // The number of reactor projects
      aProps.put (sPrefix + "count", Integer.toString (reactorProjects.size ()));

      // Show details of all reactor projects, index starting at 0
      int nIndex = 0;
      for (final MavenProject aReactorProject : reactorProjects)
      {
        aProps.put (sPrefix + nIndex + ".groupid", aReactorProject.getGroupId ());
        aProps.put (sPrefix + nIndex + ".artifactid", aReactorProject.getArtifactId ());
        aProps.put (sPrefix + nIndex + ".version", aReactorProject.getVersion ());
        aProps.put (sPrefix + nIndex + ".name", aReactorProject.getName ());
        ++nIndex;
      }
    }

    // Build Plugins
    final List <?> aBuildPlugins = project.getBuildPlugins ();
    if (aBuildPlugins != null)
    {
      final String sPrefix = "build.plugin.";
      // The number of build plugins
      aProps.put (sPrefix + "count", Integer.toString (aBuildPlugins.size ()));

      // Show details of all plugins, index starting at 0
      int nIndex = 0;
      for (final Object aObj : aBuildPlugins)
      {
        final Plugin aPlugin = (Plugin) aObj;
        aProps.put (sPrefix + nIndex + ".groupid", aPlugin.getGroupId ());
        aProps.put (sPrefix + nIndex + ".artifactid", aPlugin.getArtifactId ());
        aProps.put (sPrefix + nIndex + ".version", aPlugin.getVersion ());
        final Object aConfiguration = aPlugin.getConfiguration ();
        if (aConfiguration != null)
        {
          // Will emit an XML structure!
          aProps.put (sPrefix + nIndex + ".configuration", aConfiguration.toString ());
        }
        aProps.put (sPrefix + nIndex + ".key", aPlugin.getKey ());
        ++nIndex;
      }
    }

    // Build dependencies
    final List <?> aDependencies = project.getDependencies ();
    if (aDependencies != null)
    {
      final String sDepPrefix = "dependency.";
      // The number of build plugins
      aProps.put (sDepPrefix + "count", Integer.toString (aDependencies.size ()));

      // Show details of all dependencies, index starting at 0
      int nDepIndex = 0;
      for (final Object aDepObj : aDependencies)
      {
        final Dependency aDependency = (Dependency) aDepObj;
        aProps.put (sDepPrefix + nDepIndex + ".groupid", aDependency.getGroupId ());
        aProps.put (sDepPrefix + nDepIndex + ".artifactid", aDependency.getArtifactId ());
        aProps.put (sDepPrefix + nDepIndex + ".version", aDependency.getVersion ());
        aProps.put (sDepPrefix + nDepIndex + ".type", aDependency.getType ());
        if (aDependency.getClassifier () != null)
          aProps.put (sDepPrefix + nDepIndex + ".classifier", aDependency.getClassifier ());
        aProps.put (sDepPrefix + nDepIndex + ".scope", aDependency.getScope ());
        if (aDependency.getSystemPath () != null)
          aProps.put (sDepPrefix + nDepIndex + ".systempath", aDependency.getSystemPath ());
        aProps.put (sDepPrefix + nDepIndex + ".optional", Boolean.toString (aDependency.isOptional ()));
        aProps.put (sDepPrefix + nDepIndex + ".managementkey", aDependency.getManagementKey ());

        // Add all exclusions of the current dependency
        final List <?> aExclusions = aDependency.getExclusions ();
        if (aExclusions != null)
        {
          final String sExclusionPrefix = sDepPrefix + nDepIndex + ".exclusion.";
          // The number of build plugins
          aProps.put (sExclusionPrefix + "count", Integer.toString (aExclusions.size ()));

          // Show details of all dependencies, index starting at 0
          int nExclusionIndex = 0;
          for (final Object aExclusionObj : aExclusions)
          {
            final Exclusion aExclusion = (Exclusion) aExclusionObj;
            aProps.put (sExclusionPrefix + nExclusionIndex + ".groupid", aExclusion.getGroupId ());
            aProps.put (sExclusionPrefix + nExclusionIndex + ".artifactid", aExclusion.getArtifactId ());
            ++nExclusionIndex;
          }
        }

        ++nDepIndex;
      }
    }

    // Build date and time
    aProps.put ("build.datetime", aDT.toString ());
    aProps.put ("build.datetime.millis", Long.toString (aDT.getMillis ()));
    aProps.put ("build.datetime.date", aDT.toLocalDate ().toString ());
    aProps.put ("build.datetime.time", aDT.toLocalTime ().toString ());
    aProps.put ("build.datetime.timezone", aDT.getZone ().getID ());
    final int nOfsMilliSecs = aDT.getZone ().getOffset (aDT);
    aProps.put ("build.datetime.timezone.offsethours", Long.toString (nOfsMilliSecs / CGlobal.MILLISECONDS_PER_HOUR));
    aProps.put ("build.datetime.timezone.offsetmins", Long.toString (nOfsMilliSecs / CGlobal.MILLISECONDS_PER_MINUTE));
    aProps.put ("build.datetime.timezone.offsetsecs", Long.toString (nOfsMilliSecs / CGlobal.MILLISECONDS_PER_SECOND));
    aProps.put ("build.datetime.timezone.offsetmillisecs", Integer.toString (nOfsMilliSecs));

    // Emit system properties?
    if (withAllSystemProperties || ContainerHelper.isNotEmpty (selectedSystemProperties))
      for (final Map.Entry <String, String> aEntry : ContainerHelper.getSortedByKey (SystemProperties.getAllProperties ())
                                                                    .entrySet ())
      {
        final String sName = aEntry.getKey ();
        if (withAllSystemProperties || _matches (selectedSystemProperties, sName))
          if (!_matches (ignoredSystemProperties, sName))
            aProps.put ("systemproperty." + sName, aEntry.getValue ());
      }

    // Emit environment variable?
    if (withAllEnvVars || ContainerHelper.isNotEmpty (selectedEnvVars))
      for (final Map.Entry <String, String> aEntry : ContainerHelper.getSortedByKey (System.getenv ()).entrySet ())
      {
        final String sName = aEntry.getKey ();
        if (withAllEnvVars || _matches (selectedEnvVars, sName))
          if (!_matches (ignoredEnvVars, sName))
            aProps.put ("envvar." + sName, aEntry.getValue ());
      }

    return aProps;
  }

  private void _writeBuildinfoXML (final Map <String, String> aProps) throws MojoExecutionException
  {
    // Write the XML in the format that it can easily be read by the
    // com.helger.common.microdom.reader.XMLMapHandler class
    final File aFile = new File (tempDirectory, DEFAULT_FILENAME_BUILDINFO_XML);
    if (XMLMapHandler.writeMap (aProps, new FileSystemResource (aFile)).isFailure ())
      throw new MojoExecutionException ("Failed to write XML file to " + aFile);
    getLog ().debug ("Wrote buildinfo XML file to " + aFile);
  }

  private void _writeBuildinfoProperties (final Map <String, String> aProps) throws MojoExecutionException
  {
    // Write properties file
    final File aFile = new File (tempDirectory, DEFAULT_FILENAME_BUILDINFO_PROPERTIES);
    final Properties p = new Properties ();
    p.putAll (aProps);
    try
    {
      p.store (FileUtils.getOutputStream (aFile), "Generated - do not edit!");
    }
    catch (final IOException ex)
    {
      throw new MojoExecutionException ("Failed to write properties file to " + aFile, ex);
    }
    getLog ().debug ("Wrote buildinfo properties file to " + aFile);
  }

  public void execute () throws MojoExecutionException
  {
    StaticLoggerBinder.getSingleton ().setMavenLog (getLog ());
    if (tempDirectory == null)
      throw new MojoExecutionException ("No buildinfo temp directory specified!");
    if (tempDirectory.exists () && !tempDirectory.isDirectory ())
      throw new MojoExecutionException ("The specified buildinfo temp directory " +
                                        tempDirectory +
                                        " is not a directory!");
    if (!tempDirectory.exists ())
    {
      // Ensure that the directory exists
      if (!tempDirectory.mkdirs ())
        throw new MojoExecutionException ("Failed to create buildinfo temp directory " + tempDirectory);
      getLog ().info ("Created buildinfo temp directory " + tempDirectory);
    }

    if (!formatProperties && !formatXML)
      throw new MojoExecutionException ("No buildinfo output format was specified. Nothing will be generated!");

    final Map <String, String> aProps = _determineBuildInfoProperties ();

    if (formatXML)
      _writeBuildinfoXML (aProps);

    if (formatProperties)
      _writeBuildinfoProperties (aProps);

    // Add output directory as a resource-directory
    final Resource aResource = new Resource ();
    aResource.setDirectory (tempDirectory.getAbsolutePath ());
    aResource.addInclude ("**/*");
    aResource.setFiltering (false);
    aResource.setTargetPath ("META-INF");
    project.addResource (aResource);
  }
}
