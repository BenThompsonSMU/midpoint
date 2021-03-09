/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.qmodel.object;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import javax.xml.namespace.QName;

import com.querydsl.core.Tuple;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.SerializationOptions;
import com.evolveum.midpoint.repo.sqale.SqaleUtils;
import com.evolveum.midpoint.repo.sqale.qmodel.SqaleTransformerBase;
import com.evolveum.midpoint.repo.sqale.qmodel.common.QUri;
import com.evolveum.midpoint.repo.sqlbase.JdbcSession;
import com.evolveum.midpoint.repo.sqlbase.SqlTransformerContext;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.MetadataType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;

public class ObjectSqlTransformer<S extends ObjectType, Q extends QObject<R>, R extends MObject>
        extends SqaleTransformerBase<S, Q, R> {

    public ObjectSqlTransformer(
            SqlTransformerContext transformerContext,
            QObjectMapping<S, Q, R> mapping) {
        super(transformerContext, mapping);
    }

    @Override
    public S toSchemaObject(Tuple row, Q entityPath,
            Collection<SelectorOptions<GetOperationOptions>> options)
            throws SchemaException {

        PrismObject<S> prismObject;
        String serializedForm = new String(row.get(entityPath.fullObject), StandardCharsets.UTF_8);
        try {
            SqlTransformerContext.ParseResult<S> result = transformerContext.parsePrismObject(serializedForm);
            prismObject = result.prismObject;
            if (result.parsingContext.hasWarnings()) {
                logger.warn("Object {} parsed with {} warnings",
                        ObjectTypeUtil.toShortString(prismObject), result.parsingContext.getWarnings().size());
            }
        } catch (SchemaException | RuntimeException | Error e) {
            // This is a serious thing. We have corrupted XML in the repo. This may happen even
            // during system init. We want really loud and detailed error here.
            logger.error("Couldn't parse object {} {}: {}: {}\n{}",
                    mapping.schemaType().getSimpleName(), row.get(entityPath.oid),
                    e.getClass().getName(), e.getMessage(), serializedForm, e);
            throw e;
        }

        return prismObject.asObjectable();
    }

    /**
     * Override this to fill additional row attributes after calling this super version.
     *
     * *This must be called with active JDBC session* so it can create new {@link QUri} rows.
     * As this is intended for inserts *DO NOT* set {@link MObject#objectType} to any value,
     * it must be NULL otherwise the DB will complain about the value for the generated column.
     */
    @NotNull
    public R toRowObjectWithoutFullObject(S schemaObject, JdbcSession jdbcSession) {
        R row = mapping.newRowObject();

        row.oid = oidToUUid(schemaObject.getOid());

        // primitive columns common to ObjectType
        PolyStringType name = schemaObject.getName();
        row.nameOrig = name.getOrig();
        row.nameNorm = name.getNorm();

        // can this be reused? AbstractCredentialType, PasswordHistoryEntryType (is it stored?), AssignmentType... the rest is not in repo for sure
        MetadataType metadata = schemaObject.getMetadata();
        if (metadata != null) {
            ObjectReferenceType creatorRef = metadata.getCreatorRef();
            if (creatorRef != null) {
                row.creatorRefTargetOid = oidToUUid(creatorRef.getOid());
                row.creatorRefTargetType = schemaTypeToCode(creatorRef.getType());
                row.creatorRefRelationId = processCachedUri(creatorRef.getRelation(), jdbcSession);
            }
            row.createChannelId = processCachedUri(metadata.getCreateChannel(), jdbcSession);
            row.createTimestamp = MiscUtil.asInstant(metadata.getCreateTimestamp());
            // TODO metadata.getCreateApproverRef()

            ObjectReferenceType modifierRef = metadata.getModifierRef();
            if (modifierRef != null) {
                row.modifierRefTargetOid = oidToUUid(modifierRef.getOid());
                row.modifierRefTargetType = schemaTypeToCode(modifierRef.getType());
                row.modifierRefRelationId = processCachedUri(modifierRef.getRelation(), jdbcSession);
            }
            row.modifyChannelId = processCachedUri(metadata.getModifyChannel(), jdbcSession);
            row.modifyTimestamp = MiscUtil.asInstant(metadata.getModifyTimestamp());
            // TODO metadata.getModifyApproverRef()
        }

        /*
        TODO, for ref lists see also RUtil.toRObjectReferenceSet
        subtype? it's obsolete already
        parentOrgRefs
        triggers
        repo.setPolicySituation(RUtil.listToSet(jaxb.getPolicySituation()));

        if (jaxb.getExtension() != null) {
            copyExtensionOrAttributesFromJAXB(jaxb.getExtension().asPrismContainerValue(), repo, repositoryContext, RObjectExtensionType.EXTENSION, generatorResult);
        }

        repo.getTextInfoItems().addAll(RObjectTextInfo.createItemsSet(jaxb, repo, repositoryContext));
        for (OperationExecutionType opExec : jaxb.getOperationExecution()) {
            ROperationExecution rOpExec = new ROperationExecution(repo);
            ROperationExecution.fromJaxb(opExec, rOpExec, jaxb, repositoryContext, generatorResult);
            repo.getOperationExecutions().add(rOpExec);
        }

        repo.getRoleMembershipRef().addAll(
                RUtil.toRObjectReferenceSet(jaxb.getRoleMembershipRef(), repo, RReferenceType.ROLE_MEMBER, repositoryContext.relationRegistry));

        repo.getDelegatedRef().addAll(
                RUtil.toRObjectReferenceSet(jaxb.getDelegatedRef(), repo, RReferenceType.DELEGATED, repositoryContext.relationRegistry));

        repo.getArchetypeRef().addAll(
                RUtil.toRObjectReferenceSet(jaxb.getArchetypeRef(), repo, RReferenceType.ARCHETYPE, repositoryContext.relationRegistry));

        for (AssignmentType assignment : jaxb.getAssignment()) {
            RAssignment rAssignment = new RAssignment(repo, RAssignmentOwner.FOCUS);
            RAssignment.fromJaxb(assignment, rAssignment, jaxb, repositoryContext, generatorResult);

            repo.getAssignments().add(rAssignment);
        }
        */
        ObjectReferenceType tenantRef = schemaObject.getTenantRef();
        if (tenantRef != null) {
            row.tenantRefTargetOid = oidToUUid(tenantRef.getOid());
            row.tenantRefTargetType = schemaTypeToCode(tenantRef.getType());
            row.tenantRefRelationId = processCachedUri(tenantRef.getRelation(), jdbcSession);
        }

        row.lifecycleState = schemaObject.getLifecycleState();
        row.version = SqaleUtils.objectVersionAsInt(schemaObject);

        // TODO extensions

        return row;
    }

    /**
     * Serializes schema object and sets {@link R#fullObject}.
     */
    public void setFullObject(R row, S schemaObject) throws SchemaException {
        row.fullObject = createFullObject(schemaObject);
    }

    public byte[] createFullObject(S schemaObject) throws SchemaException {
        if (schemaObject.getOid() == null || schemaObject.getVersion() == null) {
            throw new IllegalArgumentException(
                    "Serialized object must have assigned OID and version: " + schemaObject);
        }

        return transformerContext.serializer()
                .itemsToSkip(fullObjectItemsToSkip())
                .options(SerializationOptions
                        .createSerializeReferenceNamesForNullOids()
                        .skipIndexOnly(true)
                        .skipTransient(true))
                .serialize(schemaObject.asPrismObject())
                .getBytes(StandardCharsets.UTF_8);
    }

    protected Collection<? extends QName> fullObjectItemsToSkip() {
        // TODO extend later, things like FocusType.F_JPEG_PHOTO, see ObjectUpdater#updateFullObject
        return Collections.emptyList();
    }
}
