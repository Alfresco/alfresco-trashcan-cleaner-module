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

import com.google.common.collect.ImmutableMap;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.lock.JobLockService;
import org.alfresco.repo.model.Repository;
import org.alfresco.repo.security.authentication.AuthenticationComponent;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.BaseSpringTest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * Test class for {@link org.alfresco.trashcan.TrashcanCleaner TrashcanCleaner}.
 *
 * @author Rui Fernandes
 *
 */
public class TrashcanCleanerIT extends BaseSpringTest
{
    private static final int BATCH_SIZE = 1000;

    private static final Log logger = LogFactory.getLog(TrashcanCleanerIT.class);

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
    }

    /**
     *
     * Generic method that asserts that for the <b>archivedNodes</b> existing on
     * archive store the execution of trashcan clean will leave remaining
     * undeleted <b>remainingNodes</b>.
     *
     * @param archivedNodes Number of nodes to be created and added to trashcan
     * @param childrenPerNode Number of children for each archived node
     * @param remainingNodes Number of nodes expected after trashcan cleanup
     * @throws Throwable
     */
    private void cleanBatchTest(int archivedNodes, int childrenPerNode, int remainingNodes) throws InterruptedException
    {
        createAndDeleteNodes(archivedNodes, childrenPerNode);

        TrashcanCleaner cleaner = new TrashcanCleaner(nodeService, transactionService,
                BATCH_SIZE, "PT1S"); // 1s

        Thread.sleep(1500);

        long nodesInTrashcan = cleaner.getNumberOfNodesInTrashcan();
        logger.info(String.format("Existing nodes to delete: %s", nodesInTrashcan));
        assertEquals(archivedNodes * childrenPerNode + archivedNodes, nodesInTrashcan);

        cleaner.clean();

        nodesInTrashcan = cleaner.getNumberOfNodesInTrashcan();
        logger.info(String.format("Existing nodes to delete after: %s", nodesInTrashcan));
        assertEquals(remainingNodes, nodesInTrashcan);

        logger.info("Clean trashcan...");
        cleaner = new TrashcanCleaner(nodeService, transactionService,
                remainingNodes, "PT1S");
        cleaner.clean();
        assertEquals(0, cleaner.getNumberOfNodesInTrashcan());
    }

    /**
     *
     * Creates and deletes the specified number of nodes.
     *
     * @param archivedNodes Number of nodes to be created and added to trashcan
     * @param childrenPerNode Number of children for each archived node
     */
    private void createAndDeleteNodes(int archivedNodes, int childrenPerNode)
    {
        AuthenticationUtil.runAsSystem(() ->
        {
            RetryingTransactionHelper.RetryingTransactionCallback<Void> txnWork = () ->
            {
                for (int i = 0; i < archivedNodes; i++)
                {
                    addNodeWithChildrenToTrashcan(childrenPerNode);
                }
                return null;
            };
            return transactionService.getRetryingTransactionHelper().doInTransaction(txnWork);
        });
    }

    /**
     *
     * Creates and deletes an hierarchy of nodes - one parent whose name is based on the current time
     * in milliseconds, with given number of children.
     *
     * @param children Number of children for the archived node
     *
     */
    private void addNodeWithChildrenToTrashcan(int children)
    {
        NodeRef companyHome = repository.getCompanyHome();
        String name = "Sample (" + System.currentTimeMillis() + ")";
        ChildAssociationRef association = nodeService.createNode(companyHome, ContentModel.ASSOC_CONTAINS,
                QName.createQName(NamespaceService.CONTENT_MODEL_PREFIX, name), ContentModel.TYPE_CONTENT,
                ImmutableMap.of(ContentModel.PROP_NAME, name));

        //create children for association.getChildRef() node
        NodeRef parent = association.getChildRef();
        for (int i = 0; i < children; i++)
        {
            name = "child (" + i + ")";
            ChildAssociationRef child = nodeService.createNode(parent, ContentModel.ASSOC_CONTAINS,
                    QName.createQName(NamespaceService.CONTENT_MODEL_PREFIX, name),
                    ContentModel.TYPE_CONTENT, ImmutableMap.of(ContentModel.PROP_NAME, name));
            // archive child
            nodeService.deleteNode(child.getChildRef());
        }

        // archive node
        nodeService.deleteNode(parent);
    }

    /**
     *
     * Tests that after creating just one node and deleting it, the cleaning of the trashcan
     * will delete it using the default configuration.
     *
     */
    @Test
    public void testCleanSimple() throws InterruptedException
    {
        cleanBatchTest(1, 0, 0);
    }

    /**
     *
     * Tests that after creating 1 more node than the trashcan cleaner batch size, the cleaning of the trashcan
     * will leave just a single node in archive.
     *
     */
    @Test
    public void testCleanBatch() throws InterruptedException
    {
        cleanBatchTest(BATCH_SIZE + 1, 0, 1);
    }

    @Test
    public void testCleanNodesOlderThanKeepPeriod() throws InterruptedException
    {
        final int deleteBatchCount = 15;
        final int noOfRemainingNodes = 16;

        // archived nodes older than 'keepPeriod' (less than deleteBatchCount) - will be deleted by the trashcan cleaner
        createAndDeleteNodes(4, 2); // total of 12 archived nodes

        Thread.sleep(10000);

        // within 'keepPeriod' - trashcan cleaner won't delete them
        createAndDeleteNodes(8, 1); // total of 16 archived nodes

        TrashcanCleaner cleaner = new TrashcanCleaner(nodeService, transactionService,
                deleteBatchCount, "PT10S"); // 10s

        assertEquals(28, cleaner.getNumberOfNodesInTrashcan());

        // run trashcan cleaner
        cleaner.clean();
        assertEquals(noOfRemainingNodes, cleaner.getNumberOfNodesInTrashcan());

        logger.info("Clean up trashcan...");
        // wait 2s so all archived nodes can be deleted
        Thread.sleep(2000);
        cleaner = new TrashcanCleaner(nodeService, transactionService,
                BATCH_SIZE, "PT1S"); // 1s
        cleaner.clean();
        assertEquals(0, cleaner.getNumberOfNodesInTrashcan());
    }
}
