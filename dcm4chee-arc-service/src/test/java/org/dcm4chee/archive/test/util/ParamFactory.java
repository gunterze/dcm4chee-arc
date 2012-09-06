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
 * Portions created by the Initial Developer are Copyright (C) 2012
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

package org.dcm4chee.archive.test.util;

import org.dcm4che.data.Tag;
import org.dcm4che.soundex.ESoundex;
import org.dcm4chee.archive.common.QueryParam;
import org.dcm4chee.archive.common.StoreParam;
import org.dcm4chee.archive.conf.AttributeFilter;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public abstract class ParamFactory {

    private static final int[] PATIENT_ATTRS = {
        Tag.SpecificCharacterSet,
        Tag.PatientName,
        Tag.PatientID,
        Tag.IssuerOfPatientID,
        Tag.IssuerOfPatientIDQualifiersSequence,
        Tag.PatientBirthDate,
        Tag.PatientBirthTime,
        Tag.PatientSex,
        Tag.PatientInsurancePlanCodeSequence,
        Tag.PatientPrimaryLanguageCodeSequence,
        Tag.OtherPatientNames,
        Tag.OtherPatientIDsSequence,
        Tag.PatientBirthName,
        Tag.PatientAge,
        Tag.PatientSize,
        Tag.PatientSizeCodeSequence,
        Tag.PatientWeight,
        Tag.PatientAddress,
        Tag.PatientMotherBirthName,
        Tag.MilitaryRank,
        Tag.BranchOfService,
        Tag.MedicalRecordLocator,
        Tag.MedicalAlerts,
        Tag.Allergies,
        Tag.CountryOfResidence,
        Tag.RegionOfResidence,
        Tag.PatientTelephoneNumbers,
        Tag.EthnicGroup,
        Tag.Occupation,
        Tag.SmokingStatus,
        Tag.AdditionalPatientHistory,
        Tag.PregnancyStatus,
        Tag.LastMenstrualDate,
        Tag.PatientReligiousPreference,
        Tag.PatientSpeciesDescription,
        Tag.PatientSpeciesCodeSequence,
        Tag.PatientSexNeutered,
        Tag.PatientBreedDescription,
        Tag.PatientBreedCodeSequence,
        Tag.BreedRegistrationSequence,
        Tag.ResponsiblePerson,
        Tag.ResponsiblePersonRole,
        Tag.ResponsibleOrganization,
        Tag.PatientComments,
        Tag.ClinicalTrialSponsorName,
        Tag.ClinicalTrialProtocolID,
        Tag.ClinicalTrialProtocolName,
        Tag.ClinicalTrialSiteID,
        Tag.ClinicalTrialSiteName,
        Tag.ClinicalTrialSubjectID,
        Tag.ClinicalTrialSubjectReadingID,
        Tag.PatientIdentityRemoved,
        Tag.DeidentificationMethod,
        Tag.DeidentificationMethodCodeSequence,
        Tag.ClinicalTrialProtocolEthicsCommitteeName,
        Tag.ClinicalTrialProtocolEthicsCommitteeApprovalNumber,
        Tag.SpecialNeeds,
        Tag.PertinentDocumentsSequence,
        Tag.PatientState,
        Tag.PatientClinicalTrialParticipationSequence,
        Tag.ConfidentialityConstraintOnPatientDataDescription
    };
    private static final int[] STUDY_ATTRS = {
        Tag.SpecificCharacterSet,
        Tag.StudyDate,
        Tag.StudyTime,
        Tag.AccessionNumber,
        Tag.IssuerOfAccessionNumberSequence,
        Tag.ReferringPhysicianName,
        Tag.StudyDescription,
        Tag.ProcedureCodeSequence,
        Tag.PatientAge,
        Tag.PatientSize,
        Tag.PatientSizeCodeSequence,
        Tag.PatientWeight,
        Tag.Occupation,
        Tag.AdditionalPatientHistory,
        Tag.PatientSexNeutered,
        Tag.StudyInstanceUID,
        Tag.StudyID 
    };
    private static final int[] SERIES_ATTRS = {
        Tag.SpecificCharacterSet,
        Tag.Modality,
        Tag.Manufacturer,
        Tag.InstitutionName,
        Tag.InstitutionCodeSequence,
        Tag.StationName,
        Tag.SeriesDescription,
        Tag.InstitutionalDepartmentName,
        Tag.PerformingPhysicianName,
        Tag.ManufacturerModelName,
        Tag.ReferencedPerformedProcedureStepSequence,
        Tag.BodyPartExamined,
        Tag.SeriesInstanceUID,
        Tag.SeriesNumber,
        Tag.Laterality,
        Tag.PerformedProcedureStepStartDate,
        Tag.PerformedProcedureStepStartTime,
        Tag.RequestAttributesSequence
    };
    private static final int[] INSTANCE_ATTRS = {
        Tag.SpecificCharacterSet,
        Tag.ImageType,
        Tag.SOPClassUID,
        Tag.SOPInstanceUID,
        Tag.ContentDate,
        Tag.ContentTime,
        Tag.ReferencedSeriesSequence,
        Tag.InstanceNumber,
        Tag.NumberOfFrames,
        Tag.Rows,
        Tag.Columns,
        Tag.BitsAllocated,
        Tag.ConceptNameCodeSequence,
        Tag.VerifyingObserverSequence,
        Tag.ReferencedRequestSequence,
        Tag.CompletionFlag,
        Tag.VerificationFlag,
        Tag.DocumentTitle,
        Tag.MIMETypeOfEncapsulatedDocument,
        Tag.ContentLabel,
        Tag.ContentDescription,
        Tag.PresentationCreationDate,
        Tag.PresentationCreationTime,
        Tag.ContentCreatorName,
        Tag.OriginalAttributesSequence
    };
    private static final int[] VISIT_ATTRS = {
        Tag.SpecificCharacterSet,
        Tag.InstitutionName,
        Tag.InstitutionAddress,
        Tag.InstitutionCodeSequence,
        Tag.ReferringPhysicianName,
        Tag.ReferringPhysicianAddress,
        Tag.ReferringPhysicianTelephoneNumbers,
        Tag.ReferringPhysicianIdentificationSequence,
        Tag.AdmittingDiagnosesDescription,
        Tag.AdmittingDiagnosesCodeSequence,
        Tag.VisitStatusID,
        Tag.AdmissionID,
        Tag.IssuerOfAdmissionIDSequence,
        Tag.RouteOfAdmissions,
        Tag.AdmittingDate,
        Tag.AdmittingTime,
        Tag.ServiceEpisodeID,
        Tag.ServiceEpisodeDescription,
        Tag.IssuerOfServiceEpisodeIDSequence,
        Tag.CurrentPatientLocation,
        Tag.PatientInstitutionResidence,
        Tag.VisitComments
    };
    private static final int[] SERVICE_REQUEST_ATTRS = {
        Tag.SpecificCharacterSet,
        Tag.AccessionNumber,
        Tag.IssuerOfAccessionNumberSequence,
        Tag.RequestingPhysicianIdentificationSequence,
        Tag.RequestingPhysician,
        Tag.RequestingService,
        Tag.RequestingServiceCodeSequence,
        Tag.OrderPlacerIdentifierSequence,
        Tag.OrderFillerIdentifierSequence,
        Tag.IssueDateOfImagingServiceRequest,
        Tag.IssueTimeOfImagingServiceRequest,
        Tag.OrderEnteredBy,
        Tag.OrderEntererLocation,
        Tag.OrderCallbackPhoneNumber,
        Tag.PlacerOrderNumberImagingServiceRequest,
        Tag.FillerOrderNumberImagingServiceRequest,
        Tag.ImagingServiceRequestComments
    };
    private static final int[] REQUESTED_PROCEDURE_ATTRS = {
        Tag.SpecificCharacterSet,
        Tag.StudyDate,
        Tag.StudyTime,
        Tag.StudyInstanceUID,
        Tag.RequestedProcedureDescription,
        Tag.RequestedProcedureCodeSequence,
        Tag.RequestedProcedureID,
        Tag.ReasonForTheRequestedProcedure,
        Tag.RequestedProcedurePriority,
        Tag.PatientTransportArrangements,
        Tag.RequestedProcedureLocation,
        Tag.ConfidentialityCode,
        Tag.ReportingPriority,
        Tag.ReasonForRequestedProcedureCodeSequence,
        Tag.NamesOfIntendedRecipientsOfResults,
        Tag.IntendedRecipientsOfResultsIdentificationSequence,
        Tag.RequestedProcedureComments
    };
    private static final int[] SPS_ATTRS = {
        Tag.SpecificCharacterSet,
        Tag.Modality,
        Tag.AnatomicalOrientationType,
        Tag.RequestedContrastAgent,
        Tag.ScheduledStationAETitle,
        Tag.ScheduledProcedureStepStartDate,
        Tag.ScheduledProcedureStepStartTime,
        Tag.ScheduledProcedureStepEndDate,
        Tag.ScheduledProcedureStepEndTime,
        Tag.ScheduledPerformingPhysicianName,
        Tag.ScheduledProcedureStepDescription,
        Tag.ScheduledProtocolCodeSequence,
        Tag.ScheduledProcedureStepID,
        Tag.ScheduledPerformingPhysicianIdentificationSequence,
        Tag.ScheduledStationName,
        Tag.ScheduledProcedureStepLocation,
        Tag.PreMedication,
        Tag.ScheduledProcedureStepStatus,
        Tag.CommentsOnTheScheduledProcedureStep,
        Tag.ScheduledSpecimenSequence
    };
    private static final int[] PPS_ATTRS = {
        Tag.SpecificCharacterSet,
        Tag.Modality,
        Tag.ProcedureCodeSequence,
        Tag.AnatomicStructureSpaceOrRegionSequence,
        Tag.DistanceSourceToDetector,
        Tag.ImageAndFluoroscopyAreaDoseProduct,
        Tag.StudyID,
        Tag.AdmissionID,
        Tag.IssuerOfAdmissionIDSequence,
        Tag.ServiceEpisodeID,
        Tag.ServiceEpisodeDescription,
        Tag.IssuerOfServiceEpisodeIDSequence,
        Tag.PerformedStationAETitle,
        Tag.PerformedStationName,
        Tag.PerformedLocation,
        Tag.PerformedProcedureStepStartDate,
        Tag.PerformedProcedureStepStartTime,
        Tag.PerformedProcedureStepEndDate,
        Tag.PerformedProcedureStepEndTime,
        Tag.PerformedProcedureStepStatus,
        Tag.PerformedProcedureStepID,
        Tag.PerformedProcedureStepDescription,
        Tag.PerformedProcedureTypeDescription,
        Tag.PerformedProtocolCodeSequence,
        Tag.ScheduledStepAttributesSequence,
        Tag.CommentsOnThePerformedProcedureStep,
        Tag.PerformedProcedureStepDiscontinuationReasonCodeSequence,
        Tag.TotalTimeOfFluoroscopy,
        Tag.TotalNumberOfExposures,
        Tag.EntranceDose,
        Tag.ExposedArea,
        Tag.DistanceSourceToEntrance,
        Tag.ExposureDoseSequence,
        Tag.CommentsOnRadiationDose,
        Tag.BillingProcedureStepSequence,
        Tag.FilmConsumptionSequence,
        Tag.BillingSuppliesAndDevicesSequence,
        Tag.PerformedSeriesSequence,
        Tag.ReasonForPerformedProcedureCodeSequence,
        Tag.EntranceDoseInmGy
    };
    private static final AttributeFilter[] ATTR_FILTERS = {
        new AttributeFilter(PATIENT_ATTRS),
        new AttributeFilter(STUDY_ATTRS),
        new AttributeFilter(SERIES_ATTRS),
        new AttributeFilter(INSTANCE_ATTRS),
        new AttributeFilter(VISIT_ATTRS),
        new AttributeFilter(SERVICE_REQUEST_ATTRS),
        new AttributeFilter(REQUESTED_PROCEDURE_ATTRS),
        new AttributeFilter(SPS_ATTRS),
        new AttributeFilter(PPS_ATTRS)
    };

    public static StoreParam createStoreParam() {
        StoreParam storeParam = new StoreParam();
        storeParam.setAttributeFilters(ATTR_FILTERS);
        storeParam.setFuzzyStr(new ESoundex());
        return storeParam;
    }

    public static QueryParam createQueryParam() {
        QueryParam queryParam = new QueryParam();
        queryParam.setAttributeFilters(ATTR_FILTERS);
        queryParam.setFuzzyStr(new ESoundex());
        return queryParam;
    }
}
