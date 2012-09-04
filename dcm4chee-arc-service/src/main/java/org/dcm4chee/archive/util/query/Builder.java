/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chee.archive.util.query;

import java.util.List;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Issuer;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.dcm4chee.archive.common.IDWithIssuer;
import org.dcm4chee.archive.common.QueryParam;
import org.dcm4chee.archive.conf.AttributeFilter;
import org.dcm4chee.archive.conf.Entity;
import org.dcm4chee.archive.entity.Code;
import org.dcm4chee.archive.entity.QCode;
import org.dcm4chee.archive.entity.QContentItem;
import org.dcm4chee.archive.entity.QInstance;
import org.dcm4chee.archive.entity.QIssuer;
import org.dcm4chee.archive.entity.QPatient;
import org.dcm4chee.archive.entity.QRequestedProcedure;
import org.dcm4chee.archive.entity.QScheduledProcedureStep;
import org.dcm4chee.archive.entity.QScheduledStationAETitle;
import org.dcm4chee.archive.entity.QSeries;
import org.dcm4chee.archive.entity.QServiceRequest;
import org.dcm4chee.archive.entity.QStudy;
import org.dcm4chee.archive.entity.QStudyPermission;
import org.dcm4chee.archive.entity.QVerifyingObserver;
import org.dcm4chee.archive.entity.StudyPermissionAction;

import com.mysema.query.BooleanBuilder;
import com.mysema.query.jpa.hibernate.HibernateSubQuery;
import com.mysema.query.types.ExpressionUtils;
import com.mysema.query.types.Predicate;
import com.mysema.query.types.expr.SimpleExpression;
import com.mysema.query.types.expr.StringExpression;
import com.mysema.query.types.path.BeanPath;
import com.mysema.query.types.path.CollectionPath;
import com.mysema.query.types.path.StringPath;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@gmail.com>
 */
public abstract class Builder {

    public static void addPatientLevelPredicates(BooleanBuilder builder,
            IDWithIssuer[] pids, Attributes keys, QueryParam queryParam) {

        boolean matchUnknown = queryParam.isMatchUnknown();

        builder.and(pids(pids, matchUnknown));

        if (keys == null)
            return;

        builder.and(MatchPersonName.match(QPatient.patient.patientName,
                QPatient.patient.patientIdeographicName,
                QPatient.patient.patientPhoneticName,
                QPatient.patient.patientFamilyNameSoundex,
                QPatient.patient.patientGivenNameSoundex,
                keys.getString(Tag.PatientName, "*"),
                queryParam));
        builder.and(wildCard(QPatient.patient.patientSex,
                keys.getString(Tag.PatientSex, "*").toUpperCase(), matchUnknown, false));
        builder.and(MatchDateTimeRange.rangeMatch(QPatient.patient.patientBirthDate, 
                keys, Tag.PatientBirthDate, MatchDateTimeRange.FormatDate.DA, matchUnknown));
        AttributeFilter attrFilter = queryParam.getAttributeFilters()[Entity.Patient.ordinal()];
        builder.and(wildCard(QPatient.patient.patientCustomAttribute1,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute1(), "*"),
                matchUnknown, true));
        builder.and(wildCard(QPatient.patient.patientCustomAttribute2,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute2(), "*"),
                matchUnknown, true));
        builder.and(wildCard(QPatient.patient.patientCustomAttribute3,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute3(), "*"),
                matchUnknown, true));
    }

    public static void addStudyLevelPredicates(BooleanBuilder builder, Attributes keys,
            QueryParam queryParam) {
        if (keys == null)
            return;

        boolean matchUnknown = queryParam.isMatchUnknown();
        boolean combinedDatetimeMatching = queryParam.isCombinedDatetimeMatching();
        builder.and(uids(QStudy.study.studyInstanceUID, keys.getStrings(Tag.StudyInstanceUID), false));
        builder.and(wildCard(QStudy.study.studyID, keys.getString(Tag.StudyID, "*"), matchUnknown, false));
        builder.and(MatchDateTimeRange.rangeMatch(QStudy.study.studyDate, QStudy.study.studyTime, 
                Tag.StudyDate, Tag.StudyTime, Tag.StudyDateAndTime, 
                keys, combinedDatetimeMatching, matchUnknown));
        builder.and(MatchPersonName.match(QStudy.study.referringPhysicianName,
                QStudy.study.referringPhysicianIdeographicName,
                QStudy.study.referringPhysicianPhoneticName,
                QStudy.study.referringPhysicianFamilyNameSoundex,
                QStudy.study.referringPhysicianGivenNameSoundex,
                keys.getString(Tag.ReferringPhysicianName, "*"),
                queryParam));
        builder.and(wildCard(QStudy.study.studyDescription,
                keys.getString(Tag.StudyDescription, "*"), matchUnknown, true));
        String accNo = keys.getString(Tag.AccessionNumber, "*");
        builder.and(wildCard(QStudy.study.accessionNumber, accNo, matchUnknown, false));
        if(!accNo.equals("*"))
            builder.and(issuer(QStudy.study.issuerOfAccessionNumber,
                    keys.getNestedDataset(Tag.IssuerOfAccessionNumberSequence),
                    queryParam.getDefaultIssuerOfAccessionNumber(),
                    matchUnknown));
        builder.and(modalitiesInStudy(
                keys.getString(Tag.ModalitiesInStudy, "*").toUpperCase(), matchUnknown));
        builder.and(code(QStudy.study.procedureCodes,
                keys.getNestedDataset(Tag.ProcedureCodeSequence), matchUnknown));
        AttributeFilter attrFilter = queryParam.getAttributeFilters()[Entity.Study.ordinal()];
        builder.and(wildCard(QStudy.study.studyCustomAttribute1,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute1(), "*"),
                matchUnknown, true));
        builder.and(wildCard(QStudy.study.studyCustomAttribute2,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute2(), "*"),
                matchUnknown, true));
        builder.and(wildCard(QStudy.study.studyCustomAttribute3,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute3(), "*"),
                matchUnknown, true));
        builder.and(permission(queryParam.getRoles(), StudyPermissionAction.QUERY));
    }

    public static void addSeriesLevelPredicates(BooleanBuilder builder, Attributes keys,
            QueryParam queryParam) {
        if (keys == null)
            return;

        boolean matchUnknown = queryParam.isMatchUnknown();
        builder.and(uids(QSeries.series.seriesInstanceUID,
                keys.getStrings(Tag.SeriesInstanceUID), false));
        builder.and(wildCard(QSeries.series.seriesNumber,
                keys.getString(Tag.SeriesNumber, "*"), matchUnknown, false));
        builder.and(wildCard(QSeries.series.modality,
                keys.getString(Tag.Modality, "*").toUpperCase(), matchUnknown, false));
        builder.and(wildCard(QSeries.series.bodyPartExamined,
                keys.getString(Tag.BodyPartExamined, "*").toUpperCase(), matchUnknown, false));
        builder.and(wildCard(QSeries.series.laterality,
                keys.getString(Tag.Laterality, "*").toUpperCase(), matchUnknown, false));
        builder.and(MatchDateTimeRange.rangeMatch(
                QSeries.series.performedProcedureStepStartDate,
                QSeries.series.performedProcedureStepStartTime,
                Tag.PerformedProcedureStepStartDate,
                Tag.PerformedProcedureStepStartTime,
                Tag.PerformedProcedureStepStartDateAndTime,
                keys, queryParam.isCombinedDatetimeMatching(), matchUnknown));
        builder.and(MatchPersonName.match(QSeries.series.performingPhysicianName,
                QSeries.series.performingPhysicianIdeographicName,
                QSeries.series.performingPhysicianPhoneticName,
                QSeries.series.performingPhysicianFamilyNameSoundex,
                QSeries.series.performingPhysicianGivenNameSoundex,
                keys.getString(Tag.PerformingPhysicianName, "*"),
                queryParam));
        builder.and(wildCard(QSeries.series.seriesDescription,
                keys.getString(Tag.SeriesDescription, "*"), matchUnknown, true));
        builder.and(wildCard(QSeries.series.stationName,
                keys.getString(Tag.StationName, "*"), matchUnknown, true));
        builder.and(wildCard(QSeries.series.institutionName,
                keys.getString(Tag.InstitutionalDepartmentName, "*"), matchUnknown, true));
        builder.and(wildCard(QSeries.series.institutionalDepartmentName,
                keys.getString(Tag.InstitutionName, "*"), matchUnknown, true));
        builder.and(requestAttributes(keys.getNestedDataset(Tag.RequestAttributesSequence),
                queryParam));
        builder.and(code(QSeries.series.institutionCode,
                keys.getNestedDataset(Tag.InstitutionCodeSequence), matchUnknown));
        AttributeFilter attrFilter = queryParam.getAttributeFilters()[Entity.Series.ordinal()];
        builder.and(wildCard(QSeries.series.seriesCustomAttribute1,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute1(), "*"),
                matchUnknown, true));
        builder.and(wildCard(QSeries.series.seriesCustomAttribute2,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute2(), "*"),
                matchUnknown, true));
        builder.and(wildCard(QSeries.series.seriesCustomAttribute3,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute3(), "*"),
                matchUnknown, true));
    }

    public static void addInstanceLevelPredicates(BooleanBuilder builder, Attributes keys,
            QueryParam queryParam) {
        if (keys == null)
            return;

        boolean matchUnknown = queryParam.isMatchUnknown();
        boolean combinedDatetimeMatching = queryParam.isCombinedDatetimeMatching();
        builder.and(uids(QInstance.instance.sopInstanceUID, keys.getStrings(Tag.SOPInstanceUID), false));
        builder.and(uids(QInstance.instance.sopClassUID, keys.getStrings(Tag.SOPClassUID), false));
        builder.and(wildCard(QInstance.instance.instanceNumber,
                keys.getString(Tag.InstanceNumber, "*"), matchUnknown, false));
        builder.and(wildCard(QInstance.instance.verificationFlag,
                keys.getString(Tag.VerificationFlag, "*").toUpperCase(), matchUnknown, false));
        builder.and(wildCard(QInstance.instance.completionFlag,
                keys.getString(Tag.CompletionFlag, "*").toUpperCase(), matchUnknown, false));
        builder.and(MatchDateTimeRange.rangeMatch(
                QInstance.instance.contentDate,
                QInstance.instance.contentTime,
                Tag.ContentDate, Tag.ContentTime, Tag.ContentDateAndTime,
                keys, combinedDatetimeMatching, matchUnknown));
        builder.and(code(QInstance.instance.conceptNameCode,
                keys.getNestedDataset(Tag.ConceptNameCodeSequence), matchUnknown));
        builder.and(verifyingObserver(keys.getNestedDataset(Tag.VerifyingObserverSequence),
                queryParam));
        Sequence contentSeq = keys.getSequence(Tag.ContentSequence);
        if (contentSeq != null)
            for (Attributes item : contentSeq)
                builder.and(contentItem(item));
        AttributeFilter attrFilter = queryParam.getAttributeFilters()[Entity.Instance.ordinal()];
        builder.and(wildCard(QInstance.instance.instanceCustomAttribute1,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute1(), "*"),
                matchUnknown, true));
        builder.and(wildCard(QInstance.instance.instanceCustomAttribute2,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute2(), "*"),
                matchUnknown, true));
        builder.and(wildCard(QInstance.instance.instanceCustomAttribute3,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute3(), "*"),
                matchUnknown, true));
        builder.and(QInstance.instance.replaced.isFalse());
        andNotInCodes(builder, QInstance.instance.conceptNameCode, queryParam.getHideConceptNameCodes());
        andNotInCodes(builder, QInstance.instance.rejectionCode, queryParam.getHideRejectionCodes());
    }

    public static Predicate pids(IDWithIssuer[] pids, boolean matchUnknown) {
        if (pids == null || pids.length == 0)
            return null;

        BooleanBuilder result = new BooleanBuilder();
        for (IDWithIssuer pid : pids)
            result.or(pid(pid, matchUnknown));

        return result;
    }

    static Predicate pid(IDWithIssuer pid, boolean matchUnknown) {
        return ExpressionUtils.allOf(
                wildCard(QPatient.patient.patientID, pid.id, matchUnknown, false),
                issuer(QPatient.patient.issuerOfPatientID, pid.issuer, matchUnknown));
    }

    static Predicate wildCard(StringPath path, String value, boolean matchUnknown, boolean ignoreCase) {
        if (value == null || value.equals("*"))
            return null;

        Predicate predicate;
        StringExpression expr = ignoreCase && isUpperCase(value) ? path.toUpperCase() : path;
        if (containsWildcard(value)) {
            String pattern = toLikePattern(value);
            if (pattern.equals("%"))
                return null;

            predicate = expr.like(pattern);
        } else
            predicate = expr.eq(value);

        return matchUnknown(predicate, path, matchUnknown);
    }

    static boolean isUpperCase(String s) {
        return s.equals(s.toUpperCase());
    }

    static boolean containsWildcard(String s) {
        return s.indexOf('*') >= 0 || s.indexOf('?') >= 0;
    }

    static Predicate matchUnknown(Predicate predicate, StringPath path, boolean matchUnknown) {
        return matchUnknown ? ExpressionUtils.or(predicate, path.eq("*")) : predicate;
    }

    static <T> Predicate matchUnknown(Predicate predicate, BeanPath<T> path, boolean matchUnknown) {
        return matchUnknown ? ExpressionUtils.or(predicate, path.isNull()) : predicate;
    }

    static <E,Q extends SimpleExpression<? super E>> Predicate matchUnknown(
            Predicate predicate, CollectionPath<E, Q> path, boolean matchUnknown) {
        return matchUnknown ? ExpressionUtils.or(predicate, path.isEmpty()) : predicate;
    }

    static String toLikePattern(String s) {
        StringBuilder like = new StringBuilder(s.length());
        char[] cs = s.toCharArray();
        char p = 0;
        for (char c : cs) {
            switch (c) {
            case '*':
                if (c != p)
                    like.append('%');
                break;
            case '?':
                like.append('_');
                break;
            case '_':
            case '%':
                like.append('!');
                // fall through
            default:
                like.append(c);
            }
            p = c;
        }
        return like.toString();
    }

    public static Predicate uids(StringPath path, String[] values, boolean matchUnknown) {
        if (values == null || values.length == 0 || values[0].equals("*"))
            return null;

        return matchUnknown(
                path.in(values),
                path,
                matchUnknown);
    }

    static Predicate modalitiesInStudy(String modality, boolean matchUnknown) {
        if (modality.equals("*"))
            return null;

        return new HibernateSubQuery()
            .from(QSeries.series)
            .where(QSeries.series.study.eq(QStudy.study),
                    wildCard(QSeries.series.modality, modality, matchUnknown, false))
            .exists();
    }

    static Predicate code(Attributes item) {
        if (item == null || item.isEmpty())
            return null;

        return ExpressionUtils.allOf(
                wildCard(QCode.code.codeValue, 
                        item.getString(Tag.CodeValue, "*"), false, false),
                wildCard(QCode.code.codingSchemeDesignator,
                        item.getString(Tag.CodingSchemeDesignator, "*"), false, false),
                wildCard(QCode.code.codingSchemeVersion,
                        item.getString(Tag.CodingSchemeVersion, "*"), false, false));
    }

    static Predicate code(QCode code, Attributes item, boolean matchUnknown) {
        Predicate predicate = code(item);
        if (predicate == null)
            return null;

        return matchUnknown(
                new HibernateSubQuery()
                    .from(QCode.code)
                    .where(QCode.code.eq(code), predicate)
                    .exists(),
                code,
                matchUnknown);
    }

    static Predicate code(CollectionPath<Code, QCode> codes, Attributes item,
            boolean matchUnknown) {
        Predicate predicate = code(item);
        if (predicate == null)
            return null;

        return matchUnknown(
                new HibernateSubQuery()
                    .from(QCode.code)
                    .where(codes.contains(QCode.code), predicate)
                    .exists(),
                codes,
                matchUnknown);
    }

    public static void andNotInCodes(BooleanBuilder builder, QCode code, List<Code> codes) {
        if (codes != null && !codes.isEmpty())
            builder.and(ExpressionUtils.or(code.isNull(), code.notIn(codes)));
    }

    static Predicate issuer(QIssuer path, Attributes item, Issuer defaultIssuer,
            boolean matchUnknown) {
        Predicate issuer = issuer(path, item, matchUnknown);
        return issuer != null 
                ? issuer
                : issuer(path, defaultIssuer, matchUnknown);
    }

    static Predicate issuer(QIssuer path, Attributes item, boolean matchUnknown) {
        if (item == null || item.isEmpty())
            return null;

        return issuer(path,
                item.getString(Tag.LocalNamespaceEntityID, "*"),
                item.getString(Tag.UniversalEntityID, "*"),
                item.getString(Tag.UniversalEntityIDType, "*"),
                matchUnknown);
    }

    static Predicate issuer(QIssuer path, Issuer issuer, boolean matchUnknown) {
        if (issuer == null)
            return null;

        return issuer(path,
                issuer.getLocalNamespaceEntityID(),
                issuer.getUniversalEntityID(),
                issuer.getUniversalEntityIDType(),
                matchUnknown);
    }

    private static Predicate issuer(QIssuer path, String entityID,
            String entityUID, String entityUIDType, boolean matchUnknown) {
        Predicate predicate = ExpressionUtils.anyOf(
                wildCard(QIssuer.issuer.localNamespaceEntityID, entityID, false, false),
                ExpressionUtils.allOf(
                        wildCard(QIssuer.issuer.universalEntityID, entityUID, false, false),
                        wildCard(QIssuer.issuer.universalEntityIDType, entityUIDType, false, false))
                );

        if (predicate == null)
            return null;

        return matchUnknown(
                new HibernateSubQuery()
                    .from(QIssuer.issuer)
                    .where(QIssuer.issuer.eq(path), predicate)
                    .exists(),
                path,
                matchUnknown);
    }

    static Predicate requestAttributes(Attributes item, QueryParam queryParam) {
        if (item == null || item.isEmpty())
            return null;

        boolean matchUnknown = queryParam.isMatchUnknown();
        BooleanBuilder builder = new BooleanBuilder();
        Builder.addServiceRequestPredicates(builder, item, queryParam);
        Builder.addRequestedProcedurePredicates(builder, item, queryParam);
        Builder.addScheduledProcedureStepPredicates(builder, item, queryParam);
        if (!builder.hasValue())
            return null;

        return matchUnknown(
                new HibernateSubQuery()
                    .from(QScheduledProcedureStep.scheduledProcedureStep)
                    .innerJoin(QScheduledProcedureStep.scheduledProcedureStep.requestedProcedure, QRequestedProcedure.requestedProcedure)
                    .innerJoin(QRequestedProcedure.requestedProcedure.serviceRequest, QServiceRequest.serviceRequest)
                    .where(QSeries.series.scheduledProcedureSteps.contains(QScheduledProcedureStep.scheduledProcedureStep),
                            builder)
                    .exists(),
                QSeries.series.scheduledProcedureSteps,
                matchUnknown);
    }

    static Predicate verifyingObserver(Attributes item, QueryParam queryParam) {
        if (item == null || item.isEmpty())
            return null;

        boolean matchUnknown = queryParam.isMatchUnknown();
        Predicate predicate = ExpressionUtils.allOf(
                    MatchDateTimeRange.rangeMatch(
                        QVerifyingObserver.verifyingObserver.verificationDateTime, item, 
                        Tag.VerificationDateTime, MatchDateTimeRange.FormatDate.DT, matchUnknown),
                    MatchPersonName.match(
                        QVerifyingObserver.verifyingObserver.verifyingObserverName,
                        QVerifyingObserver.verifyingObserver.verifyingObserverIdeographicName,
                        QVerifyingObserver.verifyingObserver.verifyingObserverPhoneticName,
                        QVerifyingObserver.verifyingObserver.verifyingObserverFamilyNameSoundex,
                        QVerifyingObserver.verifyingObserver.verifyingObserverGivenNameSoundex,
                        item.getString(Tag.VerifyingObserverName, "*"),
                        queryParam));

        if (predicate == null)
            return null;

        return matchUnknown(
                new HibernateSubQuery()
                    .from(QVerifyingObserver.verifyingObserver)
                    .where(QInstance.instance.verifyingObservers
                            .contains(QVerifyingObserver.verifyingObserver),
                            predicate)
                    .exists(),
                QInstance.instance.verifyingObservers,
                matchUnknown);
    }

    static Predicate contentItem(Attributes item) {
        String valueType = item.getString(Tag.ValueType);
        if (!("CODE".equals(valueType) || "TEXT".equals(valueType)))
            return null;

        Predicate predicate = ExpressionUtils.allOf(
                    code(QContentItem.contentItem.conceptName,
                        item.getNestedDataset(Tag.ConceptNameCodeSequence), false),
                    wildCard(QContentItem.contentItem.relationshipType,
                            item.getString(Tag.RelationshipType, "*").toUpperCase(), false, false),
                    code(QContentItem.contentItem.conceptCode,
                        item.getNestedDataset(Tag.ConceptCodeSequence), false),
                    wildCard(QContentItem.contentItem.textValue,
                            item.getString(Tag.TextValue, "*"), false, true));
        if (predicate == null)
            return null;

        return new HibernateSubQuery()
            .from(QContentItem.contentItem)
            .where(QInstance.instance.contentItems.contains(QContentItem.contentItem), predicate)
            .exists();
    }

    static Predicate permission(String[] roles, StudyPermissionAction action) {
        if (roles == null || roles.length == 0)
            return null;
        
        return new HibernateSubQuery()
            .from(QStudyPermission.studyPermission)
            .where(QStudyPermission.studyPermission.studyInstanceUID.eq(QStudy.study.studyInstanceUID),
                   QStudyPermission.studyPermission.action.eq(action),
                   QStudyPermission.studyPermission.role.in(roles))
            .exists();
    }

    public static void addServiceRequestPredicates(BooleanBuilder builder,
            Attributes item, QueryParam queryParam) {

        boolean matchUnknown = queryParam.isMatchUnknown();
        String accNo = item.getString(Tag.AccessionNumber, "*");
        builder.and(wildCard(QServiceRequest.serviceRequest.requestingService,
                    item.getString(Tag.RequestingService, "*"),
                    matchUnknown, true));
        builder.and(MatchPersonName.match(
                QServiceRequest.serviceRequest.requestingPhysician,
                QServiceRequest.serviceRequest.requestingPhysicianIdeographicName,
                QServiceRequest.serviceRequest.requestingPhysicianPhoneticName,
                QServiceRequest.serviceRequest.requestingPhysicianFamilyNameSoundex,
                QServiceRequest.serviceRequest.requestingPhysicianGivenNameSoundex,
                item.getString(Tag.ReferringPhysicianName, "*"),
                queryParam));
        builder.and(wildCard(QServiceRequest.serviceRequest.accessionNumber, accNo, matchUnknown, false));

        if (!accNo.equals("*"))
            builder.and(
                    issuer(QServiceRequest.serviceRequest.issuerOfAccessionNumber,
                        item.getNestedDataset(Tag.IssuerOfAccessionNumberSequence),
                        queryParam.getDefaultIssuerOfAccessionNumber(), matchUnknown));

    }

    public static void addRequestedProcedurePredicates(BooleanBuilder builder,
            Attributes keys, QueryParam queryParam) {
        boolean matchUnknown = queryParam.isMatchUnknown();
        builder.and(wildCard(QRequestedProcedure.requestedProcedure.requestedProcedureID,
                keys.getString(Tag.RequestedProcedureID, "*"),
                matchUnknown, false));
        builder.and(uids(QRequestedProcedure.requestedProcedure.studyInstanceUID,
                keys.getStrings(Tag.StudyInstanceUID), matchUnknown));
    }

    public static void addScheduledProcedureStepPredicates(BooleanBuilder builder,
            Attributes item, QueryParam queryParam) {
        if (item == null || item.isEmpty())
            return;

        boolean matchUnknown = queryParam.isMatchUnknown();
        boolean combinedDatetimeMatching = queryParam.isCombinedDatetimeMatching();
        builder.and(wildCard(QScheduledProcedureStep.scheduledProcedureStep.modality,
                item.getString(Tag.Modality, "*").toUpperCase(), matchUnknown, false));
        builder.and(scheduledStationAET(item.getString(Tag.ScheduledStationAETitle, "*"), matchUnknown));
        builder.and(MatchPersonName.match(
                QScheduledProcedureStep.scheduledProcedureStep.scheduledPerformingPhysicianName,
                QScheduledProcedureStep.scheduledProcedureStep.scheduledPerformingPhysicianIdeographicName,
                QScheduledProcedureStep.scheduledProcedureStep.scheduledPerformingPhysicianPhoneticName,
                QScheduledProcedureStep.scheduledProcedureStep.scheduledPerformingPhysicianFamilyNameSoundex,
                QScheduledProcedureStep.scheduledProcedureStep.scheduledPerformingPhysicianGivenNameSoundex,
                item.getString(Tag.ScheduledPerformingPhysicianName, "*"),
                queryParam));
        builder.and(MatchDateTimeRange.rangeMatch(
                QScheduledProcedureStep.scheduledProcedureStep.scheduledStartDate,
                QScheduledProcedureStep.scheduledProcedureStep.scheduledStartTime,
                Tag.ScheduledProcedureStepStartDate,
                Tag.ScheduledProcedureStepStartTime,
                Tag.ScheduledProcedureStepStartDateAndTime,
                item, combinedDatetimeMatching, matchUnknown));
        builder.and(wildCard(QScheduledProcedureStep.scheduledProcedureStep.scheduledProcedureStepID,
                item.getString(Tag.ScheduledProcedureStepID, "*"),
                matchUnknown, false));
        builder.and(uids(QScheduledProcedureStep.scheduledProcedureStep.status,
                item.getStrings(Tag.ScheduledProcedureStepStatus), matchUnknown));
    }

    private static Predicate scheduledStationAET(String aet, boolean matchUnknown) {
        if (aet.equals("*"))
            return null;

        return new HibernateSubQuery()
            .from(QScheduledStationAETitle.scheduledStationAETitle)
            .where(QScheduledProcedureStep.scheduledProcedureStep.scheduledStationAETs.contains(
                    QScheduledStationAETitle.scheduledStationAETitle),
                    wildCard(QScheduledStationAETitle.scheduledStationAETitle.aeTitle,
                            aet, matchUnknown, false))
            .exists();
    }

}
