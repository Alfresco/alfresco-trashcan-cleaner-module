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

import org.alfresco.schedule.AbstractScheduledLockedJob;
import org.quartz.JobExecutionContext;

/**
 * 
 * This class is the job responsible for cleaning the trashcan periodically
 * according to <b>trashcan-cleaner.deleteBatchCount</b> and <b>trashcan-cleaner.keepPeriod</b>
 * set. It's a {@link org.springframework.scheduling.quartz.QuartzJobBean
 * QuartzJobBean} implemented through extension of the
 * {@link org.alfresco.schedule.AbstractScheduledLockedJob
 * AbstractScheduledLockedJob}.
 * 
 * <b>trashcan-cleaner.deleteBatchCount</b>: It will set how many nodes in trashcan to
 * delete at maximum during <b>clean</b> execution. By default the value is
 * 1000. <b>trashcan-cleaner.keepPeriod</b>: The number of days to keep a document in
 * trashcan since its deletion. Any node archived less than the value specified
 * won't be deleted during <b>clean</b> execution. If the value is 0 or negative
 * any archived will be eligible for deletion (default behavior if no positive
 * value is explicitly set).
 * 
 * @author Rui Fernandes
 * 
 */
public class TrashcanCleanerJob extends AbstractScheduledLockedJob
{
    /**
     * The implementation of the
     * {@link org.alfresco.schedule.AbstractScheduledLockedJob
     * AbstractScheduledLockedJob} abstract executeJob method.
     */
    @Override
    public void executeJob(JobExecutionContext jobContext)
    {
        TrashcanCleaner trashcanCleaner = (TrashcanCleaner) jobContext.getJobDetail().getJobDataMap().get("trashcanCleaner");
        trashcanCleaner.clean();
    }
}
