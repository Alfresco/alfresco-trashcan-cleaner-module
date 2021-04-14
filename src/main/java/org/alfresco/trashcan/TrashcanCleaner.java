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

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.alfresco.service.transaction.TransactionService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static org.apache.commons.collections4.ListUtils.partition;

/**
 *
 * This class is capable of cleaning the trashcan without depending on searches
 * over the archive store. So the corresponding index core could be deactivated
 * with no impact on its execution. It will {@link #clean} the trashcan according
 * to defined {@link #deleteBatchCount} and {@link #keepPeriod} properties.
 * <p>
 * {@link #deleteBatchCount}: It will set how many nodes in trashcan to delete at
 * maximum during {@link #clean} execution. By default the value is 1000.
 * <p>
 * {@link #keepPeriod}: The time period (in {@link java.time.Duration} format) for which documents in
 * trashcan are kept. Any nodes archived less than the value specified won't be deleted during {@link #clean} execution.
 *
 * @author Rui Fernandes
 * @author sglover
 */
public class TrashcanCleaner
{
    private static final Log logger = LogFactory.getLog(TrashcanCleaner.class);

    private static final int batchSize = 100;

    private final NodeService nodeService;
    private final TransactionService transactionService;

    private final String archiveStoreUrl = "archive://SpacesStore";
    private final int deleteBatchCount; // total nodes to delete
    private final Duration keepPeriod;
    private List<NodeRef> trashcanNodes;

    /**
     *
     * @param nodeService
     * @param transactionService
     * @param deleteBatchCount
     * @param keepPeriod
     */
    public TrashcanCleaner(NodeService nodeService, TransactionService transactionService,
            int deleteBatchCount, String keepPeriod)
    {
        this.nodeService = nodeService;
        this.transactionService = transactionService;
        this.deleteBatchCount = deleteBatchCount;
        this.keepPeriod = Duration.parse(keepPeriod);
    }

    /**
     *
     * It splits and deletes the {@link java.util.List} of
     * {@link org.alfresco.service.cmr.repository.NodeRef}s received as
     * argument in batches of {@link #batchSize}.
     *
     * @param nodes
     */
    private void delete(List<NodeRef> nodes)
    {
        if (deleteBatchCount <= batchSize)
        {
            deleteNodes(nodes);
            return;
        }

        partition(nodes, batchSize).forEach(this::deleteNodes);
    }

    /**
     *
     * It deletes the {@link java.util.List List} of
     * {@link org.alfresco.service.cmr.repository.NodeRef NodeRef} received as
     * argument.
     *
     * @param nodes
     */
    private void deleteNodes(List<NodeRef> nodes)
    {
        for (NodeRef nodeRef : nodes)
        {
            AuthenticationUtil.runAsSystem(() ->
            {
                RetryingTransactionCallback<Void> txnWork = () ->
                {
                    nodeService.deleteNode(nodeRef);
                    return null;
                };
                return transactionService.getRetryingTransactionHelper().doInTransaction(txnWork, false, true);
            });
        }
    }

    /**
     *
     * It returns the {@link java.util.List List} of
     * {@link org.alfresco.service.cmr.repository.NodeRef NodeRef} of the
     * archive store set to be deleted according to configuration for
     * <b>deleteBatchCount</b> and <b>keepPeriod</b>.
     *
     * @return
     */
    private List<NodeRef> getBatchToDelete()
    {
        return getTrashcanChildAssocs()
                .stream()
                .filter(el -> olderThanDaysToKeep(el.getChildRef()))
                .map(ChildAssociationRef::getChildRef)
                .collect(Collectors.toList());
    }

    /**
     *
     * It will return the first {@link #deleteBatchCount}
     * {@link org.alfresco.service.cmr.repository.ChildAssociationRef}s
     * of type {@link ContentModel}.ASSOC_CHILDREN
     * from the archive store set.
     *
     * @return
     */
    private List<ChildAssociationRef> getTrashcanChildAssocs()
    {
        StoreRef archiveStore = new StoreRef(archiveStoreUrl);
        NodeRef archiveRoot = nodeService.getRootNode(archiveStore);

        return nodeService.getChildAssocs(
                archiveRoot, ContentModel.ASSOC_CHILDREN, RegexQNamePattern.MATCH_ALL, deleteBatchCount, false);
    }

    /**
     *
     * It checks if the archived node has been archived since longer than
     * <b>keepPeriod</b>. If <b>keepPeriod</b> is 0 or negative it will return
     * always true.
     *
     * @param node
     * @return
     */
    private boolean olderThanDaysToKeep(NodeRef node)
    {
        Date archivedDate = (Date) nodeService.getProperty(node, ContentModel.PROP_ARCHIVED_DATE);
        long archivedDateValue = 0;
        if (archivedDate != null)
        {
            archivedDateValue = archivedDate.getTime();
        }

        ZonedDateTime before = Instant.ofEpochMilli(System.currentTimeMillis()).minus(keepPeriod).atZone(ZoneId.of("UTC"));
        return Instant.ofEpochMilli(archivedDateValue).atZone(ZoneId.of("UTC")).isBefore(before);
    }

    /**
     *
     * It returns the number of nodes present on trashcan.
     *
     * @return
     */
    public long getNumberOfNodesInTrashcan()
    {
        StoreRef storeRef = new StoreRef(archiveStoreUrl);
        NodeRef archiveRoot = nodeService.getRootNode(storeRef);

        return nodeService.getChildAssocs(
                archiveRoot, ContentModel.ASSOC_CHILDREN, RegexQNamePattern.MATCH_ALL).size();
    }

    /**
     *
     * The method that will clean the specified <b>archiveStoreUrl</b> to the
     * limits defined by the values set for <b>deleteBatchCount</b> and
     * <b>keepPeriod</b>.
     *
     */
    public void clean()
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Running TrashcanCleaner");
        }

        AuthenticationUtil.runAsSystem(() ->
        {
            RetryingTransactionCallback<Void> txnWork = () ->
            {
                trashcanNodes = getBatchToDelete();

                if (logger.isDebugEnabled())
                {
                    logger.debug(String.format("Number of nodes to delete: %s", trashcanNodes.size()));
                }

                return null;
            };
            return transactionService.getRetryingTransactionHelper().doInTransaction(txnWork, true, true);
        });

        deleteNodes(trashcanNodes);

        if (logger.isDebugEnabled())
        {
            logger.debug("TrashcanCleaner finished");
        }
    }
}
