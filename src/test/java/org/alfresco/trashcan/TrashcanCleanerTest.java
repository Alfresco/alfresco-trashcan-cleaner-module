package org.alfresco.trashcan;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.node.archive.NodeArchiveService;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TrashcanCleanerTest
{
    private static final StoreRef ARCHIVE_STORE = new StoreRef("archive", "SpacesStore");
    private static final int BATCH_SIZE = 10;
    private static final String KEEP_PERIOD = "PT10S";
    private static final String ROOT_ID = "root";
    private static final String NODE_ID = "node1";
    private static final long OLD_AGE_MS = 30_000L;

    @Mock
    private NodeService nodeService;

    @Mock
    private TransactionService transactionService;

    @Mock
    private NodeArchiveService nodeArchiveService;

    @Mock
    private RetryingTransactionHelper transactionHelper;

    private TrashcanCleaner cleaner;

    @BeforeEach
    public void setUp()
    {
        when(transactionService.getRetryingTransactionHelper()).thenReturn(transactionHelper);
        stubTransactionExecution(true);
        stubTransactionExecution(false);
        cleaner = new TrashcanCleaner(nodeService, transactionService, BATCH_SIZE, KEEP_PERIOD, nodeArchiveService);
    }

    /**
     * Tests that clean() delegates node deletion to nodeArchiveService.purgeArchivedNode()
     * for any eligible archived node. No Thread.sleep needed — the archived date is mocked.
     */
    @Test
    public void testCleanDelegatesToPurgeArchivedNode()
    {
        NodeRef archiveRoot = new NodeRef(ARCHIVE_STORE, ROOT_ID);
        NodeRef archivedNode = new NodeRef(ARCHIVE_STORE, NODE_ID);
        List<ChildAssociationRef> childAssocs = Arrays.asList(newChildAssoc(archiveRoot, archivedNode));

        when(nodeService.getRootNode(ARCHIVE_STORE)).thenReturn(archiveRoot);
        when(nodeService.getChildAssocs(archiveRoot, ContentModel.ASSOC_CHILDREN,
                org.alfresco.service.namespace.RegexQNamePattern.MATCH_ALL, BATCH_SIZE, false))
                .thenReturn(childAssocs);
        when(nodeService.getProperty(archivedNode, ContentModel.PROP_ARCHIVED_DATE))
                .thenReturn(new Date(System.currentTimeMillis() - OLD_AGE_MS));

        cleaner.clean();

        verify(nodeArchiveService).purgeArchivedNode(archivedNode);
    }

    private void stubTransactionExecution(boolean readOnly)
    {
        when(transactionHelper.doInTransaction(any(RetryingTransactionHelper.RetryingTransactionCallback.class), eq(readOnly), eq(true)))
                .thenAnswer(invocation -> invocation.<RetryingTransactionHelper.RetryingTransactionCallback<?>>getArgument(0).execute());
    }

    private ChildAssociationRef newChildAssoc(NodeRef parent, NodeRef child)
    {
        return new ChildAssociationRef(ContentModel.ASSOC_CHILDREN, parent, QName.createQName(NODE_ID), child, true, 0);
    }
}
