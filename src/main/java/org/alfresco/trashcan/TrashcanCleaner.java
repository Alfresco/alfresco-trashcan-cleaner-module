/*
 * Copyright (C) 2005-2013 Alfresco Software Limited.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.

 * As a special exception to the terms and conditions of version 2.0 of 
 * the GPL, you may redistribute this Program in connection with Free/Libre 
 * and Open Source Software ("FLOSS") applications as described in Alfresco's 
 * FLOSS exception.  You should have recieved a copy of the text describing 
 * the FLOSS exception, and it is also available here: 
 * http://www.alfresco.com/legal/licensing"
 */
package org.alfresco.trashcan;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.lock.JobLockService.JobLockRefreshCallback;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.security.authentication.AuthenticationUtil.RunAsWork;
import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.transaction.TransactionService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 
 * This class is capable of cleaning the trashcan without depending on searches
 * over the archive store. So the corresponding index core could be deactivated
 * with no impact on its execution. It will clean (<b>clean</b> method the
 * trashcan according to defined <b>deleteBatchCount</b> and <b>daysToKeep</b>.
 * 
 * <b>deleteBatchCount</b>: It will set how many nodes in trashcan to delete at
 * maximum during <b>clean</b> execution. By default the value is 1000.
 * <b>daysToKeep</b>: The number of days to keep a document in trashcan since
 * its deletion. Any node archived less than the value specified won't be
 * deleted during <b>clean</b> execution. If the value is 0 or negative any
 * archived will be eligible for deletion (default behavior if no positive value
 * is explicitly set).
 * 
 * By default the cleaning will be executed against the standard store for
 * trashcan: archive://SpacesStore. If you want you can also specify a different
 * <b>archiveStoreUrl</b> than the default.
 * 
 * @author Rui Fernandes
 * @author sglover
 * 
 * TODO: fetch child associations using the deleteBatchCount rather than all associations?
 * 
 */
public class TrashcanCleaner implements JobLockRefreshCallback
{
    private static final Log logger = LogFactory.getLog(TrashcanCleaner.class);

    private final NodeService nodeService;
    private final TransactionService transactionService;

    private final AtomicBoolean isActive = new AtomicBoolean(true);

    private final String archiveStoreUrl = "archive://SpacesStore";
    private final int deleteBatchCount;
    private final Duration keepPeriod;

    /**
     * 
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
     * It deletes the {@link java.util.List List} of
     * {@link org.alfresco.service.cmr.repository.NodeRef NodeRef} received as
     * argument.
     * 
     * @param nodes
     */
    private void deleteNodes(List<NodeRef> nodes)
    {
        for (int i = nodes.size(); i > 0; i--) 
        {
            nodeService.deleteNode(nodes.get(i - 1));
        }
    }

    /**
     * 
     * It returns the {@link java.util.List List} of
     * {@link org.alfresco.service.cmr.repository.NodeRef NodeRef} of the
     * archive store set to be deleted according to configuration for
     * <b>deleteBatchCount</b> and <b>daysToKeep</b>.
     * 
     * @return
     */
    private List<NodeRef> getBatchToDelete()
    {
        List<ChildAssociationRef> childAssocs = getTrashcanChildAssocs();
        List<NodeRef> nodes = new ArrayList<NodeRef>(deleteBatchCount);
        if (logger.isDebugEnabled())
        {
            logger.debug(String.format("Found %s nodes on trashcan", childAssocs.size()));
        }
        return fillBatchToDelete(nodes, childAssocs);
    }

    /**
     * 
     * It will fill up a {@link java.util.List List} of
     * {@link org.alfresco.service.cmr.repository.NodeRef NodeRef} from all the
     * {@link org.alfresco.service.cmr.repository.ChildAssociationRef
     * ChildAssociationRef} of the archive store set, according to the limit
     * parameters: <b>deleteBatchCount</b> and <b>daysToKeep</b>.
     * 
     * @param batch
     * @param trashChildAssocs
     * @return
     */
    private List<NodeRef> fillBatchToDelete(List<NodeRef> batch, List<ChildAssociationRef> trashChildAssocs)
    {
        for (int j = trashChildAssocs.size(); j > 0 && batch.size() < deleteBatchCount; j--) 
        {
            ChildAssociationRef childAssoc = trashChildAssocs.get(j - 1);
            NodeRef childRef = childAssoc.getChildRef();
            if (olderThanDaysToKeep(childRef))
            {
                batch.add(childRef);
            }
        }
        return batch;
    }

    /**
     * 
     * It will return all
     * {@link org.alfresco.service.cmr.repository.ChildAssociationRef
     * ChildAssociationRef} of the archive store set.
     * 
     * @return
     */
    private List<ChildAssociationRef> getTrashcanChildAssocs()
    {
        StoreRef archiveStore = new StoreRef(archiveStoreUrl);
        NodeRef archiveRoot = nodeService.getRootNode(archiveStore);
        List<ChildAssociationRef> allChildren = nodeService.getChildAssocs(archiveRoot);
        return filterArchiveUsers(allChildren);
    }

    /**
     * 
     * Don't include on list of nodes to be deleted the archiveuser node types.
     * 
     * @return
     */
    private List<ChildAssociationRef> filterArchiveUsers(List<ChildAssociationRef> allChilds)
    {
        List<ChildAssociationRef> children = new ArrayList<ChildAssociationRef>();
        for (ChildAssociationRef childAssoc : allChilds) 
        {
            NodeRef child = childAssoc.getChildRef();
            if (!ContentModel.TYPE_ARCHIVE_USER.equals(nodeService.getType(child)))
            {
                children.add(childAssoc);
            }
        }

        return children;
    }

    /**
     * 
     * It checks if the archived node has been archived since longer than
     * <b>daysToKeep</b>. If <b>daysToKeep</b> is 0 or negative it will return
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

        Instant before = LocalDateTime.now().toInstant(ZoneOffset.UTC).minus(keepPeriod);
        return Instant.ofEpochMilli(archivedDateValue).isBefore(before);
    }

    /**
     * 
     * It returns the number of nodes present on trashcan.
     * 
     * @return
     */
    public long getNumberOfNodesInTrashcan() 
    {
        return getTrashcanChildAssocs().size();
    }

    /**
     * 
     * The method that will clean the specified <b>archiveStoreUrl</b> to the
     * limits defined by the values set for <b>deleteBatchCount</b> and
     * <b>daysToKeep</b>.
     * 
     */
    public void clean()
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Running TrashcanCleaner");
        }

        AuthenticationUtil.runAsSystem(new RunAsWork<Void>()
        {
            @Override
            public Void doWork() throws Exception
            {
                RetryingTransactionCallback<Void> txnWork = new RetryingTransactionCallback<Void>()
                {
                    public Void execute() throws Exception
                    {
                        List<NodeRef> nodes = getBatchToDelete();

                        if (logger.isDebugEnabled()) 
                        {
                            logger.debug(String.format("Number of nodes to delete: %s", nodes.size()));
                        }

                        deleteNodes(nodes);

                        if (logger.isDebugEnabled())
                        {
                            logger.debug("Nodes deleted");
                        }

                        return null;
                    }
                };
                return transactionService.getRetryingTransactionHelper().doInTransaction(txnWork);
            }
        });

        if (logger.isDebugEnabled())
        {
            logger.debug("TrashcanCleaner finished");
        }
    }

    @Override
    public boolean isActive()
    {
        return isActive.get();
    }

    @Override
    public void lockReleased()
    {
    }
}
