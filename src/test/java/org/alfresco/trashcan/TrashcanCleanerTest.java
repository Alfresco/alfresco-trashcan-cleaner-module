/*
 * #%L
 * Alfresco Trash Can Cleaner
 * %%
 * Copyright (C) 2005 - 2016 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software.
 * If the software was purchased under a paid Alfresco license, the terms of
 * the paid license agreement will prevail.  Otherwise, the software is
 * provided under the following open source license terms:
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.alfresco.trashcan;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.transaction.UserTransaction;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.lock.JobLockService;
import org.alfresco.repo.model.Repository;
import org.alfresco.repo.security.authentication.AuthenticationComponent;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.BaseSpringTest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * Test class for {@link org.alfresco.trashcan.TrashcanCleaner TrashcanCleaner}.
 *
 * @author Rui Fernandes
 *
 */
public class TrashcanCleanerTest extends BaseSpringTest
{
    private static final int BATCH_SIZE = 1000;

    private static final Log logger = LogFactory.getLog(TrashcanCleanerTest.class);

    protected NodeService nodeService;
    protected TransactionService transactionService;
    protected Repository repository;
    protected JobLockService jobLockService;
    protected AuthenticationComponent authenticationComponent;

    /**
     *
     * Sets services and current user as system.
     *
     */
    @Before
    public void setUp()
    {
        nodeService = (NodeService) applicationContext.getBean("nodeService");
        authenticationComponent = (AuthenticationComponent) applicationContext.getBean("authenticationComponent");
        transactionService = (TransactionService) applicationContext.getBean("transactionComponent");
        jobLockService = (JobLockService) applicationContext.getBean("jobLockService");
        repository = (Repository) applicationContext.getBean("repositoryHelper");

        // Authenticate as the system user
        authenticationComponent.setSystemUserAsCurrentUser();
    }

    /**
     *
     * Clears security context.
     *
     */
    @After
    public void tearDown()
    {
        authenticationComponent.clearCurrentSecurityContext();
    }

    /**
     *
     * Generic method that asserts that for the <b>nodesCreate</b> existing on
     * archive store the execution of trashcan clean will leave remaining
     * undeleted <b>nodesRemain</b>.
     *
     * @param nodesCreate
     * @param nodesRemain
     * @throws Throwable
     */
    private void cleanBatchTest(int nodesCreate, int nodesRemain) throws Exception
    {
        UserTransaction userTransaction1 = transactionService.getUserTransaction();
        try
        {
            userTransaction1.begin();
            TrashcanCleaner cleaner = new TrashcanCleaner(nodeService, transactionService,
                    BATCH_SIZE, "PT1S"); // 1s
            createAndDeleteNodes(nodesCreate);

            Thread.sleep(1000);

            long nodesToDelete = cleaner.getNumberOfNodesInTrashcan();
            logger.info(String.format("Existing nodes to delete: %s", nodesToDelete));
            cleaner.clean();
            nodesToDelete = cleaner.getNumberOfNodesInTrashcan();
            logger.info(String.format("Existing nodes to delete after: %s", nodesToDelete));
            assertEquals(nodesRemain, nodesToDelete);
            logger.info("Clean trashcan...");
            cleaner.clean();
            userTransaction1.commit();
        }
        catch (Exception e)
        {
            userTransaction1.rollback();
            throw e;
        }
    }

    /**
     *
     * Creates and deletes the specified number of nodes.
     *
     * @param n
     */
    private void createAndDeleteNodes(int n)
    {
        for (int i = n; i > 0; i--)
        {
            createAndDeleteNode();
        }
    }

    /**
     *
     * Creates and delete a single node whose name is based on the current time
     * in milliseconds.
     *
     */
    private void createAndDeleteNode()
    {
        NodeRef companyHome = repository.getCompanyHome();
        String name = "Sample (" + System.currentTimeMillis() + ")";
        Map<QName, Serializable> contentProps = new HashMap<QName, Serializable>();
        contentProps.put(ContentModel.PROP_NAME, name);
        ChildAssociationRef association = nodeService.createNode(companyHome, ContentModel.ASSOC_CONTAINS,
                QName.createQName(NamespaceService.CONTENT_MODEL_PREFIX, name), ContentModel.TYPE_CONTENT,
                contentProps);
        nodeService.deleteNode(association.getChildRef());
    }

    /**
     *
     * Tests that after creating just one node and deleting it, the cleaning of the trashcan
     * will delete it using the default configuration.
     *
     * @throws Exception
     */
    @Test
    public void testCleanSimple() throws Exception
    {
        cleanBatchTest(1, 0);
    }

    /**
     *
     * Tests that after creating 1 more node than the trashcan cleaner batch size, the cleaning of the trashcan
     * will leave just a single node in archive.
     *
     * @throws Exception
     */
    @Test
    public void testCleanBatch() throws Exception
    {
        cleanBatchTest(BATCH_SIZE + 1, 1);
    }
}
