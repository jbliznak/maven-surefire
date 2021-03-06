package org.apache.maven.plugin.surefire;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.surefire.log.PluginConsoleLogger;
import org.apache.maven.surefire.booter.ClassLoaderConfiguration;
import org.apache.maven.surefire.booter.Classpath;
import org.apache.maven.surefire.booter.ModularClasspathConfiguration;
import org.apache.maven.surefire.booter.StartupConfiguration;
import org.apache.maven.surefire.suite.RunResult;
import org.apache.maven.surefire.util.DefaultScanResult;
import org.codehaus.plexus.languages.java.jpms.LocationManager;
import org.codehaus.plexus.languages.java.jpms.ResolvePathsRequest;
import org.codehaus.plexus.languages.java.jpms.ResolvePathsResult;
import org.codehaus.plexus.logging.Logger;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.io.File.separatorChar;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static org.apache.commons.lang3.JavaVersion.JAVA_1_7;
import static org.apache.commons.lang3.JavaVersion.JAVA_RECENT;
import static org.apache.maven.surefire.booter.SystemUtils.isBuiltInJava7AtLeast;
import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.doNothing;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.verifyPrivate;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import static org.powermock.reflect.Whitebox.invokeMethod;

/**
 * Test for {@link AbstractSurefireMojo}.
 */
@RunWith( PowerMockRunner.class )
@PrepareForTest( { AbstractSurefireMojo.class, ResolvePathsRequest.class } )
public class AbstractSurefireMojoJava7PlusTest
{
    @Mock
    private LocationManager locationManager;

    @BeforeClass
    public static void withJava7Plus()
    {
        assumeTrue( JAVA_RECENT.atLeast( JAVA_1_7 ) );
    }

    @Test
    public void shouldHaveStartupConfigForModularClasspath()
            throws Exception
    {
        AbstractSurefireMojo mojo = spy( new Mojo() );
        doReturn( locationManager )
                .when( mojo, "getLocationManager" );

        Classpath testClasspath = new Classpath( asList( "non-modular.jar", "modular.jar",
                "target" + separatorChar + "classes", "junit.jar", "hamcrest.jar" ) );

        doReturn( testClasspath ).when( mojo, "generateTestClasspath" );
        doReturn( 1 ).when( mojo, "getEffectiveForkCount" );
        doReturn( true ).when( mojo, "effectiveIsEnableAssertions" );
        when( mojo.isChildDelegation() ).thenReturn( false );
        when( mojo.getTestClassesDirectory() ).thenReturn( new File( "target" + separatorChar + "test-classes" ) );

        DefaultScanResult scanResult = mock( DefaultScanResult.class );
        when( scanResult.getClasses() ).thenReturn( asList( "org.apache.A", "org.apache.B" ) );

        ClassLoaderConfiguration classLoaderConfiguration = new ClassLoaderConfiguration( false, true );

        Classpath providerClasspath = new Classpath( singleton( "surefire-provider.jar" ) );

        File moduleInfo = new File( "target" + separatorChar + "classes" + separatorChar + "module-info.class" );

        @SuppressWarnings( "unchecked" )
        ResolvePathsRequest<String> req = mock( ResolvePathsRequest.class );
        mockStatic( ResolvePathsRequest.class );
        when( ResolvePathsRequest.withStrings( eq( testClasspath.getClassPath() ) ) ).thenReturn( req );
        when( req.setMainModuleDescriptor( eq( moduleInfo.getAbsolutePath() ) ) ).thenReturn( req );

        @SuppressWarnings( "unchecked" )
        ResolvePathsResult<String> res = mock( ResolvePathsResult.class );
        when( res.getClasspathElements() ).thenReturn( asList( "non-modular.jar", "junit.jar", "hamcrest.jar" ) );
        Map<String, ResolvePathsResult.ModuleNameSource> mod = new LinkedHashMap<String, ResolvePathsResult.ModuleNameSource>();
        mod.put( "modular.jar", null );
        mod.put( "target" + separatorChar + "classes", null );
        when( res.getModulepathElements() ).thenReturn( mod );
        when( locationManager.resolvePaths( eq( req ) ) ).thenReturn( res );

        Logger logger = mock( Logger.class );
        when( logger.isDebugEnabled() ).thenReturn( true );
        doNothing().when( logger ).debug( anyString() );
        when( mojo.getConsoleLogger() ).thenReturn( new PluginConsoleLogger( logger ) );

        StartupConfiguration conf = invokeMethod( mojo, "newStartupConfigForModularClasspath",
                classLoaderConfiguration, providerClasspath, "org.asf.Provider", moduleInfo, scanResult );

        verify( mojo, times( 1 ) ).effectiveIsEnableAssertions();
        verify( mojo, times( 1 ) ).isChildDelegation();
        verifyPrivate( mojo, times( 1 ) ).invoke( "generateTestClasspath" );
        verify( mojo, times( 1 ) ).getEffectiveForkCount();
        verify( mojo, times( 1 ) ).getTestClassesDirectory();
        verify( scanResult, times( 1 ) ).getClasses();
        verifyStatic( ResolvePathsRequest.class, times( 1 ) );
        ResolvePathsRequest.withStrings( eq( testClasspath.getClassPath() ) );
        verify( req, times( 1 ) ).setMainModuleDescriptor( eq( moduleInfo.getAbsolutePath() ) );
        verify( res, times( 1 ) ).getClasspathElements();
        verify( res, times( 1 ) ).getModulepathElements();
        verify( locationManager, times( 1 ) ).resolvePaths( eq( req ) );
        ArgumentCaptor<String> argument = ArgumentCaptor.forClass( String.class );
        verify( logger, times( 6 ) ).debug( argument.capture() );
        assertThat( argument.getAllValues() )
                .containsExactly( "test classpath:  non-modular.jar  junit.jar  hamcrest.jar",
                        "test modulepath:  modular.jar  target" + separatorChar + "classes",
                        "provider classpath:  surefire-provider.jar",
                        "test(compact) classpath:  non-modular.jar  junit.jar  hamcrest.jar",
                        "test(compact) modulepath:  modular.jar  classes",
                        "provider(compact) classpath:  surefire-provider.jar"
                );

        assertThat( conf ).isNotNull();
        assertThat( conf.isShadefire() ).isFalse();
        assertThat( conf.isProviderMainClass() ).isFalse();
        assertThat( conf.isManifestOnlyJarRequestedAndUsable() ).isFalse();
        assertThat( conf.getClassLoaderConfiguration() ).isSameAs( classLoaderConfiguration );
        assertThat( conf.getProviderClassName() ).isEqualTo( "org.asf.Provider" );
        assertThat( conf.getActualClassName() ).isEqualTo( "org.asf.Provider" );
        assertThat( conf.getClasspathConfiguration() ).isNotNull();
        assertThat( ( Object ) conf.getClasspathConfiguration().getTestClasspath() )
                .isEqualTo( new Classpath( res.getClasspathElements() ) );
        assertThat( ( Object ) conf.getClasspathConfiguration().getProviderClasspath() ).isSameAs( providerClasspath );
        assertThat( conf.getClasspathConfiguration() ).isInstanceOf( ModularClasspathConfiguration.class );
        ModularClasspathConfiguration mcc = ( ModularClasspathConfiguration ) conf.getClasspathConfiguration();
        assertThat( mcc.getModularClasspath().getModuleDescriptor() ).isEqualTo( moduleInfo );
        assertThat( mcc.getModularClasspath().getPackages() ).containsOnly( "org.apache" );
        assertThat( mcc.getModularClasspath().getPatchFile() )
                .isEqualTo( new File( "target" + separatorChar + "test-classes" ) );
        assertThat( mcc.getModularClasspath().getModulePath() )
                .containsExactly( "modular.jar", "target" + separatorChar + "classes" );
        assertThat( ( Object ) mcc.getTestClasspath() ).isEqualTo( new Classpath( res.getClasspathElements() ) );
    }

    @Test
    public void shouldHaveTmpDirectory() throws IOException
    {
        Path path = ( Path ) AbstractSurefireMojo.createTmpDirectoryWithJava7( "surefire" );

        assertThat( path )
                .isNotNull();

        assertThat( path.startsWith( System.getProperty( "java.io.tmpdir" ) ) )
                .isTrue();

        String dir = path.getName( path.getNameCount() - 1 ).toString();

        assertThat( dir )
                .startsWith( "surefire" );

        assertThat( dir )
                .matches( "^surefire[\\d]+$" );
    }

    @Test
    public void shouldHaveTmpDirectoryName() throws IOException
    {
        String dir = AbstractSurefireMojo.createTmpDirectoryNameWithJava7( "surefire" );

        assertThat( dir )
                .isNotNull();

        assertThat( dir )
                .startsWith( "surefire" );

        assertThat( dir )
                .matches( "^surefire[\\d]+$" );
    }

    @Test
    public void shouldTestIsJava7()
    {
        assertThat( isBuiltInJava7AtLeast() )
                .isTrue();
    }

    public static class Mojo
            extends AbstractSurefireMojo
    {

        @Override
        protected String getPluginName()
        {
            return null;
        }

        @Override
        protected int getRerunFailingTestsCount()
        {
            return 0;
        }

        @Override
        public boolean isSkipTests()
        {
            return false;
        }

        @Override
        public void setSkipTests( boolean skipTests )
        {

        }

        @Override
        public boolean isSkipExec()
        {
            return false;
        }

        @Override
        public void setSkipExec( boolean skipExec )
        {

        }

        @Override
        public boolean isSkip()
        {
            return false;
        }

        @Override
        public void setSkip( boolean skip )
        {

        }

        @Override
        public File getBasedir()
        {
            return null;
        }

        @Override
        public void setBasedir( File basedir )
        {

        }

        @Override
        public File getTestClassesDirectory()
        {
            return null;
        }

        @Override
        public void setTestClassesDirectory( File testClassesDirectory )
        {

        }

        @Override
        public File getClassesDirectory()
        {
            return null;
        }

        @Override
        public void setClassesDirectory( File classesDirectory )
        {

        }

        @Override
        public File getReportsDirectory()
        {
            return null;
        }

        @Override
        public void setReportsDirectory( File reportsDirectory )
        {

        }

        @Override
        public String getTest()
        {
            return null;
        }

        @Override
        public void setTest( String test )
        {

        }

        @Override
        public List<String> getIncludes()
        {
            return null;
        }

        @Override
        public File getIncludesFile()
        {
            return null;
        }

        @Override
        public void setIncludes( List<String> includes )
        {

        }

        @Override
        public boolean isPrintSummary()
        {
            return false;
        }

        @Override
        public void setPrintSummary( boolean printSummary )
        {

        }

        @Override
        public String getReportFormat()
        {
            return null;
        }

        @Override
        public void setReportFormat( String reportFormat )
        {

        }

        @Override
        public boolean isUseFile()
        {
            return false;
        }

        @Override
        public void setUseFile( boolean useFile )
        {

        }

        @Override
        public String getDebugForkedProcess()
        {
            return null;
        }

        @Override
        public void setDebugForkedProcess( String debugForkedProcess )
        {

        }

        @Override
        public int getForkedProcessTimeoutInSeconds()
        {
            return 0;
        }

        @Override
        public void setForkedProcessTimeoutInSeconds( int forkedProcessTimeoutInSeconds )
        {

        }

        @Override
        public int getForkedProcessExitTimeoutInSeconds()
        {
            return 0;
        }

        @Override
        public void setForkedProcessExitTimeoutInSeconds( int forkedProcessTerminationTimeoutInSeconds )
        {

        }

        @Override
        public double getParallelTestsTimeoutInSeconds()
        {
            return 0;
        }

        @Override
        public void setParallelTestsTimeoutInSeconds( double parallelTestsTimeoutInSeconds )
        {

        }

        @Override
        public double getParallelTestsTimeoutForcedInSeconds()
        {
            return 0;
        }

        @Override
        public void setParallelTestsTimeoutForcedInSeconds( double parallelTestsTimeoutForcedInSeconds )
        {

        }

        @Override
        public boolean isUseSystemClassLoader()
        {
            return false;
        }

        @Override
        public void setUseSystemClassLoader( boolean useSystemClassLoader )
        {

        }

        @Override
        public boolean isUseManifestOnlyJar()
        {
            return false;
        }

        @Override
        public void setUseManifestOnlyJar( boolean useManifestOnlyJar )
        {

        }

        @Override
        public Boolean getFailIfNoSpecifiedTests()
        {
            return null;
        }

        @Override
        public void setFailIfNoSpecifiedTests( boolean failIfNoSpecifiedTests )
        {

        }

        @Override
        public int getSkipAfterFailureCount()
        {
            return 0;
        }

        @Override
        public String getShutdown()
        {
            return null;
        }

        @Override
        public File getExcludesFile()
        {
            return null;
        }

        @Override
        protected List<File> suiteXmlFiles()
        {
            return null;
        }

        @Override
        protected boolean hasSuiteXmlFiles()
        {
            return false;
        }

        @Override
        public File[] getSuiteXmlFiles()
        {
            return new File[0];
        }

        @Override
        public void setSuiteXmlFiles( File[] suiteXmlFiles )
        {

        }

        @Override
        public String getRunOrder()
        {
            return null;
        }

        @Override
        public void setRunOrder( String runOrder )
        {

        }

        @Override
        protected void handleSummary( RunResult summary, Exception firstForkException )
                throws MojoExecutionException, MojoFailureException
        {

        }

        @Override
        protected boolean isSkipExecution()
        {
            return false;
        }

        @Override
        protected String[] getDefaultIncludes()
        {
            return new String[0];
        }

        @Override
        protected String getReportSchemaLocation()
        {
            return null;
        }

        @Override
        protected Artifact getMojoArtifact()
        {
            return null;
        }
    }
}
