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

import org.alfresco.schedule.AbstractScheduledLockedJob;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * 
 * This class is the job responsible for cleaning the trashcan periodically
 * according to <b>trashcan.deleteBatchCount</b> and <b>trashcan.daysToKeep</b>
 * set. It's a {@link org.springframework.scheduling.quartz.QuartzJobBean
 * QuartzJobBean} implemented through extension of the
 * {@link org.alfresco.schedule.AbstractScheduledLockedJob
 * AbstractScheduledLockedJob}.
 * 
 * <b>trashcan.deleteBatchCount</b>: It will set how many nodes in trashcan to
 * delete at maximum during <b>clean</b> execution. By default the value is
 * 1000. <b>trashcan.daysToKeep</b>: The number of days to keep a document in
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
    public void executeJob(JobExecutionContext jobContext) throws JobExecutionException 
    {
        TrashcanCleaner trashcanCleaner = (TrashcanCleaner) jobContext.getJobDetail().getJobDataMap().get("trashcanCleaner");
        trashcanCleaner.clean();
    }
}
