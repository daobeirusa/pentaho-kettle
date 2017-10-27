/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2017 by Hitachi Vantara : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.pentaho.di.core.plugins;

import org.junit.Test;
import org.pentaho.di.core.exception.KettlePluginClassMapException;
import org.pentaho.di.core.exception.KettlePluginException;
import org.pentaho.di.core.extension.PluginMockInterface;
import org.pentaho.di.core.logging.LoggingPluginType;
import org.pentaho.di.core.row.RowBuffer;
import org.pentaho.di.core.row.ValueMetaInterface;

import java.util.HashMap;
import java.util.UUID;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PluginRegistryUnitTest {

  @Test
  public void getGetPluginInformation() throws KettlePluginException {
    RowBuffer result = PluginRegistry.getInstance().getPluginInformation( BasePluginType.class );
    assertNotNull( result );
    assertEquals( 8, result.getRowMeta().size() );

    for ( ValueMetaInterface vmi : result.getRowMeta().getValueMetaList() ) {
      assertEquals( ValueMetaInterface.TYPE_STRING, vmi.getType() );
    }
  }

  /**
   * Test that additional plugin mappings can be added via the PluginRegistry.
   */
  @Test
  public void testSupplementalPluginMappings() throws Exception {
    PluginRegistry registry = PluginRegistry.getInstance();
    PluginInterface mockPlugin = mock( PluginInterface.class );
    when( mockPlugin.getIds() ).thenReturn( new String[] { "mockPlugin"} );
    when( mockPlugin.matches( "mockPlugin" ) ).thenReturn( true );
    when( mockPlugin.getName() ).thenReturn( "mockPlugin" );
    doReturn( LoggingPluginType.class ).when( mockPlugin ).getPluginType();
    registry.registerPlugin( LoggingPluginType.class, mockPlugin );


    registry.addClassFactory( LoggingPluginType.class, String.class, "mockPlugin", () -> "Foo" );
    String result = registry.loadClass( LoggingPluginType.class, "mockPlugin", String.class );
    assertEquals( "Foo", result );
    assertEquals( 2, registry.getPlugins( LoggingPluginType.class ).size() );


    // Now add another mapping and verify that it works and the existing supplementalPlugin was reused.
    UUID uuid = UUID.randomUUID();
    registry.addClassFactory( LoggingPluginType.class, UUID.class, "mockPlugin", () -> uuid );
    UUID out = registry.loadClass( LoggingPluginType.class, "mockPlugin", UUID.class );
    assertEquals( uuid, out );
    assertEquals( 2, registry.getPlugins( LoggingPluginType.class ).size() );

    // cleanup
    registry.removePlugin( LoggingPluginType.class, mockPlugin );
  }

  /**
   * Test that several plugin jar can share the same classloader.
   */
  @Test
  public void testPluginClassloaderGroup() throws Exception {
    PluginRegistry registry = PluginRegistry.getInstance();
    PluginInterface mockPlugin1 = mock( PluginInterface.class );
    when( mockPlugin1.getIds() ).thenReturn( new String[] { "mockPlugin"} );
    when( mockPlugin1.matches( "mockPlugin" ) ).thenReturn( true );
    when( mockPlugin1.getName() ).thenReturn( "mockPlugin" );
    when( mockPlugin1.getClassMap() ).thenReturn( new HashMap<Class<?>, String>() {{
      put( PluginTypeInterface.class, String.class.getName() );
    }} );
    when( mockPlugin1.getClassLoaderGroup() ).thenReturn( "groupPlugin" );
    doReturn( BasePluginType.class ).when( mockPlugin1 ).getPluginType();

    PluginInterface mockPlugin2 = mock( PluginInterface.class );
    when( mockPlugin2.getIds() ).thenReturn( new String[] { "mockPlugin2"} );
    when( mockPlugin2.matches( "mockPlugin2" ) ).thenReturn( true );
    when( mockPlugin2.getName() ).thenReturn( "mockPlugin2" );
    when( mockPlugin2.getClassMap() ).thenReturn( new HashMap<Class<?>, String>() {{
      put( PluginTypeInterface.class, Integer.class.getName() );
    }} );
    when( mockPlugin2.getClassLoaderGroup() ).thenReturn( "groupPlugin" );
    doReturn( BasePluginType.class ).when( mockPlugin2 ).getPluginType();

    registry.registerPlugin( BasePluginType.class, mockPlugin1 );
    registry.registerPlugin( BasePluginType.class, mockPlugin2 );

    // test they share the same classloader
    ClassLoader ucl = registry.getClassLoader( mockPlugin1 );
    assertEquals( ucl, registry.getClassLoader( mockPlugin2 ) );

    // test removing a shared plugin creates a new classloader
    registry.removePlugin( BasePluginType.class, mockPlugin2 );
    assertNotEquals( ucl, registry.getClassLoader( mockPlugin1 ) );

    // cleanup
    registry.removePlugin( BasePluginType.class, mockPlugin1 );
  }

  @Test
  public void testClassloadingPluginNoClassRegistered() {
    PluginRegistry registry = PluginRegistry.getInstance();
    PluginMockInterface plugin = mock( PluginMockInterface.class );
    when( plugin.loadClass( any() ) ).thenReturn( null );
    try {
      registry.loadClass( plugin, Class.class );
    } catch ( KettlePluginClassMapException e ) {
      // Expected exception
    } catch ( KettlePluginException e ) {
      fail();
    }
  }
}
