package org.apache.maven.surefire.junit;
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

import org.apache.maven.surefire.util.ReflectionUtils;
import org.apache.maven.surefire.util.ScannerFilter;

/**
 * @author Kristian Rosenvold
 */
public class JUnit3TestChecker
    implements ScannerFilter
{
    private final Class junitClass;


    public JUnit3TestChecker( ClassLoader testClassLoader )
    {
        junitClass = ReflectionUtils.tryLoadClass( testClassLoader, "junit.framework.Test" );
    }

    public boolean accept( Class testClass )
    {
        return isValidJUnit3Test( testClass );
    }

    public boolean isValidJUnit3Test( Class testClass )
    {
        return junitClass != null && junitClass.isAssignableFrom( testClass ) ||
            classHasPublicNoArgConstructor( testClass );
    }

    private boolean classHasPublicNoArgConstructor( Class testClass )
    {
        try
        {
            testClass.getConstructor( new Class[0] );
            return true;
        }
        catch ( Exception e )
        {
            return false;
        }
    }
}
