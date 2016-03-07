/*
 * Copyright (c) 2010-2013 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.wf.api;

import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import java.util.Collection;
import java.util.List;

/**
 * TODO specify and clean-up error handling
 *
 * @author mederly
 */

public interface WorkflowManager {

    /*
     * Work items
     * ==========
     */

    <T extends Containerable> Integer countContainers(Class<T> type, ObjectQuery query, Collection<SelectorOptions<GetOperationOptions>> options, OperationResult result)
            throws SchemaException;

    <T extends Containerable> SearchResultList<T> searchContainers(Class<T> type, ObjectQuery query, Collection<SelectorOptions<GetOperationOptions>> options, OperationResult result)
            throws SchemaException;

    /*
     * Process instances
     * =================
     */

    int countProcessInstancesRelatedToUser(String userOid, boolean requestedBy, boolean requestedFor, boolean finished, OperationResult parentResult);

    List<WfProcessInstanceType> listProcessInstancesRelatedToUser(String userOid, boolean requestedBy, boolean requestedFor, boolean finished, int first, int count, OperationResult parentResult);

    /**
     * Returns information about a process instance. WorkItems attribute is filled-in only upon request! (see getWorkItems parameter)
     *
     * @param instanceId
     * @param historic
     * @param getWorkItems
     * @param parentResult
     * @return
     * @throws ObjectNotFoundException
     * @throws WorkflowException
     */
    public WfProcessInstanceType getProcessInstanceById(String instanceId, boolean historic, boolean getWorkItems, OperationResult parentResult) throws ObjectNotFoundException;

    /*
     * CHANGING THINGS
     * ===============
     */

    /**
     * Approves or rejects a work item (without supplying any further information).
     *  @param taskId identifier of activiti task backing the work item
     * @param decision true = approve, false = reject
     * @param comment
     * @param parentResult
     */
    void approveOrRejectWorkItem(String taskId, boolean decision, String comment, OperationResult parentResult);

    void claimWorkItem(String workItemId, OperationResult result);

    void releaseWorkItem(String workItemId, OperationResult result);

    void stopProcessInstance(String instanceId, String username, OperationResult parentResult);

    void deleteProcessInstance(String instanceId, OperationResult parentResult);

    /*
     * MISC
     * ====
     */

    public boolean isEnabled();

    // TODO remove this
    PrismContext getPrismContext();

    void registerProcessListener(ProcessListener processListener);

    void registerWorkItemListener(WorkItemListener workItemListener);

    List<? extends ObjectReferenceType> getApprovedBy(Task task, OperationResult result) throws SchemaException;

    boolean isCurrentUserAuthorizedToSubmit(WorkItemNewType workItem);

    boolean isCurrentUserAuthorizedToClaim(WorkItemNewType workItem);

    <T extends ObjectType> void augmentTaskObject(PrismObject<T> object, Collection<SelectorOptions<GetOperationOptions>> options,
            Task task, OperationResult result);

    <T extends ObjectType> void augmentTaskObjectList(SearchResultList<PrismObject<T>> list,
            Collection<SelectorOptions<GetOperationOptions>> options, Task task, OperationResult result);

}
