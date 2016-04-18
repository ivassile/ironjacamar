/*
 *  IronJacamar, a Java EE Connector Architecture implementation
 *  Copyright 2016, Red Hat Inc, and individual contributors
 *  as indicated by the @author tags. See the copyright.txt file in the
 *  distribution for a full listing of individual contributors.
 *
 *  This is free software; you can redistribute it and/or modify it
 *  under the terms of the Eclipse Public License 1.0 as
 *  published by the Free Software Foundation.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the Eclipse
 *  Public License for more details.
 *
 *  You should have received a copy of the Eclipse Public License
 *  along with this software; if not, write to the Free
 *  Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *  02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.ironjacamar.core.connectionmanager.pool.dflt;

import org.ironjacamar.core.api.connectionmanager.pool.FlushMode;
import org.ironjacamar.core.api.deploymentrepository.DeploymentRepository;
import org.ironjacamar.core.connectionmanager.Credential;
import org.ironjacamar.core.connectionmanager.listener.ConnectionListener;
import org.ironjacamar.core.connectionmanager.pool.ManagedConnectionPool;
import org.ironjacamar.embedded.Configuration;
import org.ironjacamar.embedded.Deployment;
import org.ironjacamar.embedded.dsl.resourceadapters20.api.ResourceAdaptersDescriptor;
import org.ironjacamar.embedded.junit4.AllChecks;
import org.ironjacamar.embedded.junit4.IronJacamar;
import org.ironjacamar.embedded.junit4.PostCondition;
import org.ironjacamar.embedded.junit4.PreCondition;
import org.ironjacamar.rars.ResourceAdapterFactory;
import org.ironjacamar.rars.security.UnifiedSecurityConnection;
import org.ironjacamar.rars.security.UnifiedSecurityConnectionFactory;
import org.ironjacamar.util.TestUtils;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.resource.spi.TransactionSupport;

import org.jboss.shrinkwrap.api.spec.ResourceAdapterArchive;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * CRI test case w/ Prefill
 *
 * @author <a href="mailto:stefano.maestri@ironjacamar.org">Stefano Maestri</a>
 */
@RunWith(IronJacamar.class)
@Configuration(full = true)
@PreCondition(condition = AllChecks.class)
@PostCondition(condition = AllChecks.class)
public class FlushMethodCallCriTestCase
{
   /**
    * The connection factory w/o Tx
    */
   @Resource(mappedName = "java:/eis/UnifiedSecurityNoTxConnectionFactory")
   private UnifiedSecurityConnectionFactory noTxCf;

   /**
    * The noTxCf w/o Tx w/o Prefill
    */
   @Resource(mappedName = "java:/eis/UnifiedSecurityNoTxNoPrefillConnectionFactory")
   private UnifiedSecurityConnectionFactory noTxNoPrefillCf;


   /**
    * The deployment repository
    */
   @Inject
   private static DeploymentRepository dr;

   /**
    * The resource adapter
    *
    * @throws Throwable In case of an error
    */
   @Deployment(order = 1)
   private ResourceAdapterArchive createResourceAdapter() throws Throwable
   {
      return ResourceAdapterFactory.createUnifiedSecurityRar();
   }



   /**
    * The activation W/o Tx
    *
    * @throws Throwable In case of an error
    */
   @Deployment(order = 2)
   private ResourceAdaptersDescriptor createNoTxActivation() throws Throwable
   {
      return ResourceAdapterFactory.createUnifiedSecurityDeployment(null, null,
            TransactionSupport.TransactionSupportLevel.NoTransaction,
            "UnifiedSecurityNoTxConnectionFactory", true, 10);
   }


   /**
    * The activation w7o Tx w/o Prefill
    *
    * @throws Throwable In case of an error
    */
   @Deployment(order = 3)
   private ResourceAdaptersDescriptor createNoTxNoPrefillActivation() throws Throwable
   {
      return ResourceAdapterFactory.createUnifiedSecurityDeployment(null, null,
            TransactionSupport.TransactionSupportLevel.NoTransaction,
            "UnifiedSecurityNoTxNoPrefillConnectionFactory", false, 0);
   }

   /**
    * test w/o Tx, 2 calls w/ same credentials
    *
    * @throws Throwable In case of an error
    */
   @Test
   public void testPrefillInitialSize() throws Throwable
   {
      assertNotNull(noTxCf);
      assertNotNull(dr);

      assertEquals(2, dr.getDeployments().size());

      org.ironjacamar.core.api.deploymentrepository.Deployment d = dr
            .findByJndi("java:/eis/UnifiedSecurityNoTxConnectionFactory");
      assertNotNull(d);

      org.ironjacamar.core.api.deploymentrepository.ConnectionFactory dcf = d.getConnectionFactories().iterator()
            .next();
      assertNotNull(dcf);

      org.ironjacamar.core.api.deploymentrepository.Pool p = dcf.getPool();
      assertNotNull(p);

      DefaultPool defaultPool = (DefaultPool) p.getPool();

      ConcurrentHashMap<Credential, ManagedConnectionPool> mcps =
            (ConcurrentHashMap<Credential, ManagedConnectionPool>) TestUtils
                  .extract(defaultPool, "pools");
      assertNotNull(mcps);
      assertEquals(1, mcps.size());

      Iterator<ManagedConnectionPool> mcpsIter = mcps.values().iterator();
      ManagedConnectionPool mcp = mcpsIter.next();
      assertNotNull(mcp);
      ConcurrentLinkedDeque<ConnectionListener> listeners = (ConcurrentLinkedDeque<ConnectionListener>) TestUtils
            .extract(mcp, "listeners");
      assertNotNull(listeners);
      assertTrue(TestUtils.isCorrectCollectionSizeTenSecTimeout(listeners, 10));


      UnifiedSecurityConnection c =  noTxCf.getConnection("user", "pwd");
      assertNotNull(c);
      assertEquals("user", c.getUserName());
      assertEquals("pwd", c.getPassword());
      assertEquals(2, mcps.size());

      UnifiedSecurityConnection c1 = noTxCf.getConnection("user1", "pwd1");
      assertNotNull(c1);
      assertEquals("user1", c1.getUserName());
      assertEquals("pwd1", c1.getPassword());

      assertNotEquals(c, c1);

      assertEquals(3, mcps.size());

      defaultPool.flush();

      assertTrue(TestUtils.isCorrectCollectionSizeTenSecTimeout(listeners, 10));


      c1.close();

      c.close();

      // We cheat and shutdown the pool to clear out mcps
      defaultPool.shutdown();
   }

   /**
    * Deployment test w/o Tx
    *
    * @throws Throwable In case of an error
    */
   @Test
   public void testNoPrefillFlushIdle() throws Throwable
   {
      assertNotNull(noTxNoPrefillCf);
      assertNotNull(dr);

      assertEquals(2, dr.getDeployments().size());

      org.ironjacamar.core.api.deploymentrepository.Deployment d = dr
            .findByJndi("java:/eis/UnifiedSecurityNoTxNoPrefillConnectionFactory");
      assertNotNull(d);

      org.ironjacamar.core.api.deploymentrepository.ConnectionFactory dcf = d.getConnectionFactories().iterator()
            .next();
      assertNotNull(dcf);

      org.ironjacamar.core.api.deploymentrepository.Pool p = dcf.getPool();
      assertNotNull(p);

      DefaultPool defaultPool = (DefaultPool) p.getPool();


      ConcurrentHashMap<Credential, ManagedConnectionPool> mcps =
            (ConcurrentHashMap<Credential, ManagedConnectionPool>) TestUtils
                  .extract(defaultPool, "pools");
      assertNotNull(mcps);

      assertEquals(0, mcps.size());

      UnifiedSecurityConnection c = noTxNoPrefillCf.getConnection("user", "pwd");
      assertNotNull(c);
      assertEquals("user", c.getUserName());
      assertEquals("pwd", c.getPassword());

      assertEquals(1, mcps.size());

      ManagedConnectionPool mcp = mcps.values().iterator().next();
      assertNotNull(mcp);

      ConcurrentLinkedDeque<ConnectionListener> listeners = (ConcurrentLinkedDeque<ConnectionListener>) TestUtils
            .extract(mcp, "listeners");
      assertNotNull(listeners);

      assertTrue(TestUtils.isCorrectCollectionSizeTenSecTimeout(listeners, 1));

      defaultPool.flush();

      assertTrue(TestUtils.isCorrectCollectionSizeTenSecTimeout(listeners, 1));

      assertEquals(1, mcps.size());

      c.close();

      assertTrue(TestUtils.isCorrectCollectionSizeTenSecTimeout(listeners, 1));
      assertEquals(1, mcps.size());


      defaultPool.flush();

      assertTrue(TestUtils.isCorrectCollectionSizeTenSecTimeout(listeners, 0));

      // We cheat and shutdown the pool to clear out mcps
      defaultPool.shutdown();
   }


   /**
    * Deployment test w/o Tx
    *
    * @throws Throwable In case of an error
    */
   @Test
   public void testNoPrefillFlushAll() throws Throwable
   {
      assertNotNull(noTxNoPrefillCf);
      assertNotNull(dr);

      assertEquals(2, dr.getDeployments().size());

      org.ironjacamar.core.api.deploymentrepository.Deployment d = dr
            .findByJndi("java:/eis/UnifiedSecurityNoTxNoPrefillConnectionFactory");
      assertNotNull(d);

      org.ironjacamar.core.api.deploymentrepository.ConnectionFactory dcf = d.getConnectionFactories().iterator()
            .next();
      assertNotNull(dcf);

      org.ironjacamar.core.api.deploymentrepository.Pool p = dcf.getPool();
      assertNotNull(p);

      DefaultPool defaultPool = (DefaultPool) p.getPool();
      assertNotNull(defaultPool);

      ConcurrentHashMap<Credential, ManagedConnectionPool> mcps =
            (ConcurrentHashMap<Credential, ManagedConnectionPool>) TestUtils
                  .extract(defaultPool, "pools");
      assertNotNull(mcps);

      assertEquals(0, mcps.size());


      UnifiedSecurityConnection c = noTxNoPrefillCf.getConnection("user", "pwd");
      assertNotNull(c);
      assertEquals("user", c.getUserName());
      assertEquals("pwd", c.getPassword());

      assertEquals(1, mcps.size());

      ManagedConnectionPool mcp = mcps.values().iterator().next();
      assertNotNull(mcp);

      ConcurrentLinkedDeque<ConnectionListener> listeners = (ConcurrentLinkedDeque<ConnectionListener>) TestUtils
            .extract(mcp, "listeners");
      assertNotNull(listeners);

      assertTrue(TestUtils.isCorrectCollectionSizeTenSecTimeout(listeners, 1));
      assertNotNull(defaultPool);

      defaultPool.flush(FlushMode.ALL);

      assertTrue(TestUtils.isCorrectCollectionSizeTenSecTimeout(listeners, 0));

      c.close();

      // We cheat and shutdown the pool to clear out mcps
      defaultPool.shutdown();
   }

}