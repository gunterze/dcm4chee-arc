/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contentsOfthis file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copyOfthe License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is partOfdcm4che, an implementationOfDICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial DeveloperOfthe Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contentsOfthis file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisionsOfthe GPL or the LGPL are applicable instead
 * of those above. If you wish to allow useOfyour versionOfthis file only
 * under the termsOfeither the GPL or the LGPL, and not to allow others to
 * use your versionOfthis file under the termsOfthe MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your versionOfthis file under
 * the termsOfany oneOfthe MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chee.archive.conf;

import static org.dcm4che.net.TransferCapability.Role.SCP;
import static org.dcm4che.net.TransferCapability.Role.SCU;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.EnumSet;

import org.dcm4che.conf.api.AttributeCoercion;
import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.conf.api.ConfigurationNotFoundException;
import org.dcm4che.conf.api.DicomConfiguration;
import org.dcm4che.conf.api.hl7.HL7Configuration;
import org.dcm4che.conf.ldap.LdapDicomConfiguration;
import org.dcm4che.conf.ldap.audit.LdapAuditLoggerConfiguration;
import org.dcm4che.conf.ldap.audit.LdapAuditRecordRepositoryConfiguration;
import org.dcm4che.conf.ldap.hl7.LdapHL7Configuration;
import org.dcm4che.conf.prefs.PreferencesDicomConfiguration;
import org.dcm4che.conf.prefs.audit.PreferencesAuditLoggerConfiguration;
import org.dcm4che.conf.prefs.audit.PreferencesAuditRecordRepositoryConfiguration;
import org.dcm4che.conf.prefs.hl7.PreferencesHL7Configuration;
import org.dcm4che.data.Code;
import org.dcm4che.data.Issuer;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Connection;
import org.dcm4che.net.Connection.Protocol;
import org.dcm4che.net.Device;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.QueryOption;
import org.dcm4che.net.SSLManagerFactory;
import org.dcm4che.net.TransferCapability;
import org.dcm4che.net.audit.AuditLogger;
import org.dcm4che.net.audit.AuditRecordRepository;
import org.dcm4che.net.hl7.HL7Application;
import org.dcm4che.net.hl7.HL7DeviceExtension;
import org.dcm4che.util.AttributesFormat;
import org.dcm4che.util.SafeClose;
import org.dcm4che.util.StringUtils;
import org.dcm4chee.archive.conf.ldap.LdapArchiveConfiguration;
import org.dcm4chee.archive.conf.prefs.PreferencesArchiveConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ArchiveDeviceTest {

    private static final String PIX_MANAGER = "HL7RCV^DCM4CHEE";
    private static int PENDING_CMOVE_INTERVAL = 5000;
    private static final int CONFIGURATION_STALE_TIMEOUT = 60;
    private static final int WADO_ATTRIBUTES_STALE_TIMEOUT = 60;
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
        Tag.ObservationDateTime,
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
    private static final String[] IMAGE_TSUIDS = {
        UID.ImplicitVRLittleEndian,
        UID.ExplicitVRLittleEndian,
        UID.DeflatedExplicitVRLittleEndian,
        UID.ExplicitVRBigEndian,
        UID.JPEGBaseline1,
        UID.JPEGExtended24,
        UID.JPEGLossless,
        UID.JPEGLosslessNonHierarchical14,
        UID.JPEGLSLossless,
        UID.JPEGLSLossyNearLossless,
        UID.JPEG2000LosslessOnly,
        UID.JPEG2000,
        UID.RLELossless
    };
    private static final String[] VIDEO_TSUIDS = {
        UID.JPEGBaseline1,
        UID.MPEG2,
        UID.MPEG2MainProfileHighLevel,
        UID.MPEG4AVCH264BDCompatibleHighProfileLevel41,
        UID.MPEG4AVCH264HighProfileLevel41
    };
    private static final String[] OTHER_TSUIDS = {
        UID.ImplicitVRLittleEndian,
        UID.ExplicitVRLittleEndian,
        UID.DeflatedExplicitVRLittleEndian,
        UID.ExplicitVRBigEndian,
    };
    private static final String[] IMAGE_CUIDS = {
        UID.ComputedRadiographyImageStorage,
        UID.DigitalXRayImageStorageForPresentation,
        UID.DigitalXRayImageStorageForProcessing,
        UID.DigitalMammographyXRayImageStorageForPresentation,
        UID.DigitalMammographyXRayImageStorageForProcessing,
        UID.DigitalIntraOralXRayImageStorageForPresentation,
        UID.DigitalIntraOralXRayImageStorageForProcessing,
        UID.CTImageStorage,
        UID.EnhancedCTImageStorage,
        UID.UltrasoundMultiFrameImageStorageRetired,
        UID.UltrasoundMultiFrameImageStorage,
        UID.MRImageStorage,
        UID.EnhancedMRImageStorage,
        UID.EnhancedMRColorImageStorage,
        UID.NuclearMedicineImageStorageRetired,
        UID.UltrasoundImageStorageRetired,
        UID.UltrasoundImageStorage,
        UID.EnhancedUSVolumeStorage,
        UID.SecondaryCaptureImageStorage,
        UID.MultiFrameGrayscaleByteSecondaryCaptureImageStorage,
        UID.MultiFrameGrayscaleWordSecondaryCaptureImageStorage,
        UID.MultiFrameTrueColorSecondaryCaptureImageStorage,
        UID.XRayAngiographicImageStorage,
        UID.EnhancedXAImageStorage,
        UID.XRayRadiofluoroscopicImageStorage,
        UID.EnhancedXRFImageStorage,
        UID.XRayAngiographicBiPlaneImageStorageRetired,
        UID.XRay3DAngiographicImageStorage,
        UID.XRay3DCraniofacialImageStorage,
        UID.BreastTomosynthesisImageStorage,
        UID.IntravascularOpticalCoherenceTomographyImageStorageForPresentation,
        UID.IntravascularOpticalCoherenceTomographyImageStorageForProcessing,
        UID.NuclearMedicineImageStorage,
        UID.VLEndoscopicImageStorage,
        UID.VLMicroscopicImageStorage,
        UID.VLSlideCoordinatesMicroscopicImageStorage,
        UID.VLPhotographicImageStorage,
        UID.OphthalmicPhotography8BitImageStorage,
        UID.OphthalmicPhotography16BitImageStorage,
        UID.OphthalmicTomographyImageStorage,
        UID.VLWholeSlideMicroscopyImageStorage,
        UID.PositronEmissionTomographyImageStorage,
        UID.EnhancedPETImageStorage,
        UID.RTImageStorage,
    };
    private static final String[] VIDEO_CUIDS = {
        UID.VideoEndoscopicImageStorage,
        UID.VideoMicroscopicImageStorage,
        UID.VideoPhotographicImageStorage,
    };
    private static final String[] OTHER_CUIDS = {
        UID.MRSpectroscopyStorage,
        UID.MultiFrameSingleBitSecondaryCaptureImageStorage,
        UID.StandaloneOverlayStorageRetired,
        UID.StandaloneCurveStorageRetired,
        UID.TwelveLeadECGWaveformStorage,
        UID.GeneralECGWaveformStorage,
        UID.AmbulatoryECGWaveformStorage,
        UID.HemodynamicWaveformStorage,
        UID.CardiacElectrophysiologyWaveformStorage,
        UID.BasicVoiceAudioWaveformStorage,
        UID.GeneralAudioWaveformStorage,
        UID.ArterialPulseWaveformStorage,
        UID.RespiratoryWaveformStorage,
        UID.StandaloneModalityLUTStorageRetired,
        UID.StandaloneVOILUTStorageRetired,
        UID.GrayscaleSoftcopyPresentationStateStorageSOPClass,
        UID.ColorSoftcopyPresentationStateStorageSOPClass,
        UID.PseudoColorSoftcopyPresentationStateStorageSOPClass,
        UID.BlendingSoftcopyPresentationStateStorageSOPClass,
        UID.XAXRFGrayscaleSoftcopyPresentationStateStorage,
        UID.RawDataStorage,
        UID.SpatialRegistrationStorage,
        UID.SpatialFiducialsStorage,
        UID.DeformableSpatialRegistrationStorage,
        UID.SegmentationStorage,
        UID.SurfaceSegmentationStorage,
        UID.RealWorldValueMappingStorage,
        UID.StereometricRelationshipStorage,
        UID.LensometryMeasurementsStorage,
        UID.AutorefractionMeasurementsStorage,
        UID.KeratometryMeasurementsStorage,
        UID.SubjectiveRefractionMeasurementsStorage,
        UID.VisualAcuityMeasurementsStorage,
        UID.SpectaclePrescriptionReportStorage,
        UID.OphthalmicAxialMeasurementsStorage,
        UID.IntraocularLensCalculationsStorage,
        UID.MacularGridThicknessAndVolumeReportStorage,
        UID.OphthalmicVisualFieldStaticPerimetryMeasurementsStorage,
        UID.BasicStructuredDisplayStorage,
        UID.BasicTextSRStorage,
        UID.EnhancedSRStorage,
        UID.ComprehensiveSRStorage,
        UID.ProcedureLogStorage,
        UID.MammographyCADSRStorage,
        UID.KeyObjectSelectionDocumentStorage,
        UID.ChestCADSRStorage,
        UID.XRayRadiationDoseSRStorage,
        UID.ColonCADSRStorage,
        UID.ImplantationPlanSRStorage,
        UID.EncapsulatedPDFStorage,
        UID.EncapsulatedCDAStorage,
        UID.StandalonePETCurveStorageRetired,
        UID.RTDoseStorage,
        UID.RTStructureSetStorage,
        UID.RTBeamsTreatmentRecordStorage,
        UID.RTPlanStorage,
        UID.RTBrachyTreatmentRecordStorage,
        UID.RTTreatmentSummaryRecordStorage,
        UID.RTIonPlanStorage,
        UID.RTIonBeamsTreatmentRecordStorage,
    };
    private static final String[] QUERY_CUIDS = {
        UID.PatientRootQueryRetrieveInformationModelFIND,
        UID.StudyRootQueryRetrieveInformationModelFIND,
        UID.PatientStudyOnlyQueryRetrieveInformationModelFINDRetired,
        UID.ModalityWorklistInformationModelFIND
    };

    private static final String[] RETRIEVE_CUIDS = {
        UID.PatientRootQueryRetrieveInformationModelGET,
        UID.PatientRootQueryRetrieveInformationModelMOVE,
        UID.StudyRootQueryRetrieveInformationModelGET,
        UID.StudyRootQueryRetrieveInformationModelMOVE,
        UID.PatientStudyOnlyQueryRetrieveInformationModelGETRetired,
        UID.PatientStudyOnlyQueryRetrieveInformationModelMOVERetired
    };

    private static final Code INCORRECT_WORKLIST_ENTRY_SELECTED =
            new Code("110514", "DCM", null, "Incorrect worklist entry selected");
    private static final Code REJECTED_FOR_QUALITY_REASONS =
            new Code("113001", "DCM", null, "Rejected for Quality Reasons");
    private static final Code REJECT_FOR_PATIENT_SAFETY_REASONS =
            new Code("113037", "DCM", null, "Rejected for Patient Safety Reasons");
    private static final Code INCORRECT_MODALITY_WORKLIST_ENTRY =
            new Code("XXXXXX11", "99IHEIOCM", null, "Incorrect Modality Worklist Entry");
    private static final Code DATA_RETENTION_PERIOD_EXPIRED =
            new Code("XXXXXX22", "99IHEIOCM", null, "Data Retention Period Expired");

    private static final String[] OTHER_DEVICES = {
        "dcmqrscp",
        "stgcmtscu",
        "storescp",
        "mppsscp",
        "ianscp",
        "storescu",
        "mppsscu",
        "findscu",
        "getscu",
        "movescu",
        "hl7snd"
    };

    private static final String[] OTHER_AES = {
        "DCMQRSCP",
        "STGCMTSCU",
        "STORESCP",
        "MPPSSCP",
        "IANSCP",
        "STORESCU",
        "MPPSSCU",
        "FINDSCU",
        "GETSCU"
    };

    private static final Issuer SITE_A =
            new Issuer("Site A", "1.2.40.0.13.1.1.999.111.1111", "ISO");
    private static final Issuer SITE_B =
            new Issuer("Site B", "1.2.40.0.13.1.1.999.222.2222", "ISO");

    private static final Issuer[] OTHER_ISSUER = {
        SITE_B, // DCMQRSCP
        null, // STGCMTSCU
        SITE_A, // STORESCP
        SITE_A, // MPPSSCP
        null, // IANSCP
        SITE_A, // STORESCU
        SITE_A, // MPPSSCU
        SITE_A, // FINDSCU
        SITE_A, // GETSCU
    };

    private static final Code INST_A =
            new Code("111.1111", "99DCM4CHEE", null, "Site A");
    private static final Code INST_B =
            new Code("222.2222", "99DCM4CHEE", null, "Site B");

    private static final Code[] OTHER_INST_CODES = {
        INST_B, // DCMQRSCP
        null, // STGCMTSCU
        null, // STORESCP
        null, // MPPSSCP
        null, // IANSCP
        INST_A, // STORESCU
        null, // MPPSSCU
        null, // FINDSCU
        null, // GETSCU
    };

    private static final int[] OTHER_PORTS = {
        11113, 2763, // DCMQRSCP
        11114, 2765, // STGCMTSCU
        11115, 2766, // STORESCP
        11116, 2767, // MPPSSCP
        11117, 2768, // IANSCP
        Connection.NOT_LISTENING, Connection.NOT_LISTENING, // STORESCU
        Connection.NOT_LISTENING, Connection.NOT_LISTENING, // MPPSSCU
        Connection.NOT_LISTENING, Connection.NOT_LISTENING, // FINDSCU
        Connection.NOT_LISTENING, Connection.NOT_LISTENING, // GETSCU
    };

    private static final String[] HL7_MESSAGE_TYPES = {
        "ADT^A02",
        "ADT^A03",
        "ADT^A06",
        "ADT^A07",
        "ADT^A08",
        "ADT^A40",
        "ORM^O01"
    };

    private KeyStore keystore;
    private DicomConfiguration config;
    private HL7Configuration hl7Config;

    @Before
    public void setUp() throws Exception {
        keystore = SSLManagerFactory.loadKeyStore("JKS", 
                StringUtils.resourceURL("cacerts.jks"), "secret");
        config = System.getProperty("ldap") == null
                ? newPreferencesArchiveConfiguration()
                : newLdapArchiveConfiguration();
        cleanUp();
    }

    private DicomConfiguration newLdapArchiveConfiguration()
            throws ConfigurationException {
        LdapDicomConfiguration config = new LdapDicomConfiguration();
        LdapHL7Configuration hl7Config = new LdapHL7Configuration();
        config.addDicomConfigurationExtension(hl7Config);
        LdapArchiveConfiguration arcConfig = new LdapArchiveConfiguration();
        config.addDicomConfigurationExtension(arcConfig);
        hl7Config.addHL7ConfigurationExtension(arcConfig);
        config.addDicomConfigurationExtension(
                new LdapAuditLoggerConfiguration());
        config.addDicomConfigurationExtension(
                new LdapAuditRecordRepositoryConfiguration());
        this.hl7Config = hl7Config;
        return config;
    }

    private DicomConfiguration newPreferencesArchiveConfiguration() {
        PreferencesDicomConfiguration config = new PreferencesDicomConfiguration();
        PreferencesHL7Configuration hl7Config = new PreferencesHL7Configuration();
        config.addDicomConfigurationExtension(hl7Config);
        PreferencesArchiveConfiguration arcConfig = new PreferencesArchiveConfiguration();
        config.addDicomConfigurationExtension(arcConfig);
        hl7Config.addHL7ConfigurationExtension(arcConfig);
        config.addDicomConfigurationExtension(
                new PreferencesAuditLoggerConfiguration());
        config.addDicomConfigurationExtension(
                new PreferencesAuditRecordRepositoryConfiguration());
        this.hl7Config = hl7Config;
        return config;
    }

    @After
    public void tearDown() throws Exception {
        if (System.getProperty("keep") == null)
            cleanUp();
        config.close();
    }

    @Test
    public void test() throws Exception {
        for (int i = 0; i < OTHER_AES.length; i++) {
            String aet = OTHER_AES[i];
            config.registerAETitle(aet);
            config.persist(createDevice(OTHER_DEVICES[i], OTHER_ISSUER[i], OTHER_INST_CODES[i],
                    aet, "localhost", OTHER_PORTS[i<<1], OTHER_PORTS[(i<<1)+1]));
        }
        hl7Config.registerHL7Application(PIX_MANAGER);
        for (int i = OTHER_AES.length; i < OTHER_DEVICES.length; i++)
            config.persist(createDevice(OTHER_DEVICES[i]));
        config.persist(createHL7Device("hl7rcv", SITE_A, INST_A, PIX_MANAGER,
                "localhost", 2576, 12576));
        Device arrDevice = createARRDevice("syslog", Protocol.SYSLOG_UDP, 514);
        config.persist(arrDevice);
        config.registerAETitle("DCM4CHEE");
        config.registerAETitle("DCM4CHEE_ADMIN");
        config.persist(createArchiveDevice("dcm4chee-arc", arrDevice ));
        config.findApplicationEntity("DCM4CHEE");
        if (config instanceof PreferencesDicomConfiguration)
            export(System.getProperty("export"));
    }

    private Device createARRDevice(String name, Protocol protocol, int port) {
        Device arrDevice = new Device(name);
        AuditRecordRepository arr = new AuditRecordRepository();
        arrDevice.addDeviceExtension(arr);
        Connection auditUDP = new Connection("audit-udp", "localhost", port);
        auditUDP.setProtocol(protocol);
        arrDevice.addConnection(auditUDP);
        arr.addConnection(auditUDP);
        return arrDevice ;
    }

    private void cleanUp() throws Exception {
        config.unregisterAETitle("DCM4CHEE");
        config.unregisterAETitle("DCM4CHEE_ADMIN");
        for (String aet : OTHER_AES)
            config.unregisterAETitle(aet);
        hl7Config.unregisterHL7Application(PIX_MANAGER);
        try {
            config.removeDevice("dcm4chee-arc");
        } catch (ConfigurationNotFoundException e) {}
        try {
            config.removeDevice("syslog");
        } catch (ConfigurationNotFoundException e) {}
        try {
            config.removeDevice("hl7rcv");
        } catch (ConfigurationNotFoundException e) {}
        for (String name : OTHER_DEVICES)
            try {
                config.removeDevice(name);
            }  catch (ConfigurationNotFoundException e) {}
    }

    private void export(String name) throws Exception {
        if (name == null)
            return;

        OutputStream os = new FileOutputStream(name);
        try {
            ((PreferencesDicomConfiguration) config)
                    .getDicomConfigurationRoot().exportSubtree(os);
        } finally {
            SafeClose.close(os);
        }
    }

    private Device createDevice(String name) throws Exception {
        return init(new Device(name), null, null);
    }

    private Device init(Device device, Issuer issuer, Code institutionCode)
            throws Exception {
        String name = device.getDeviceName();
        device.setThisNodeCertificates(config.deviceRef(name),
                (X509Certificate) keystore.getCertificate(name));
        device.setIssuerOfPatientID(issuer);
        device.setIssuerOfAccessionNumber(issuer);
        if (institutionCode != null) {
            device.setInstitutionNames(institutionCode.getCodeMeaning());
            device.setInstitutionCodes(institutionCode);
        }
        return device;
    }

    private Device createDevice(String name,
            Issuer issuer, Code institutionCode, String aet,
            String host, int port, int tlsPort) throws Exception {
         Device device = init(new Device(name), issuer, institutionCode);
         ApplicationEntity ae = new ApplicationEntity(aet);
         ae.setAssociationAcceptor(true);
         device.addApplicationEntity(ae);
         Connection dicom = new Connection("dicom", host, port);
         device.addConnection(dicom);
         ae.addConnection(dicom);
         Connection dicomTLS = new Connection("dicom-tls", host, tlsPort);
         dicomTLS.setTlsCipherSuites(
                 Connection.TLS_RSA_WITH_AES_128_CBC_SHA, 
                 Connection.TLS_RSA_WITH_3DES_EDE_CBC_SHA);
         device.addConnection(dicomTLS);
         ae.addConnection(dicomTLS);
         return device;
    }

    private Device createHL7Device(String name,
            Issuer issuer, Code institutionCode, String appName,
            String host, int port, int tlsPort) throws Exception {
         Device device = new Device(name);
         HL7DeviceExtension hl7Device = new HL7DeviceExtension();
         device.addDeviceExtension(hl7Device);
         init(device, issuer, institutionCode);
         HL7Application hl7app = new HL7Application(appName);
         hl7Device.addHL7Application(hl7app);
         Connection hl7 = new Connection("hl7", host, port);
         hl7.setProtocol(Connection.Protocol.HL7);
         device.addConnection(hl7);
         hl7app.addConnection(hl7);
         Connection hl7TLS = new Connection("hl7-tls", host, tlsPort);
         hl7TLS.setProtocol(Connection.Protocol.HL7);
         hl7TLS.setTlsCipherSuites(
                 Connection.TLS_RSA_WITH_AES_128_CBC_SHA, 
                 Connection.TLS_RSA_WITH_3DES_EDE_CBC_SHA);
         device.addConnection(hl7TLS);
         hl7app.addConnection(hl7TLS);
         return device;
     }

    private Device createArchiveDevice(String name, Device arrDevice) throws Exception {
        Device device = new Device(name);
        HL7DeviceExtension hl7DevExt = new HL7DeviceExtension();
        ArchiveDeviceExtension arcDevExt = new ArchiveDeviceExtension();
        device.addDeviceExtension(hl7DevExt);
        device.addDeviceExtension(arcDevExt);
        arcDevExt.setIncorrectWorklistEntrySelectedCode(INCORRECT_WORKLIST_ENTRY_SELECTED);
        arcDevExt.setRejectedForQualityReasonsCode(REJECTED_FOR_QUALITY_REASONS);
        arcDevExt.setRejectedForPatientSafetyReasonsCode(REJECT_FOR_PATIENT_SAFETY_REASONS);
        arcDevExt.setIncorrectModalityWorklistEntryCode(INCORRECT_MODALITY_WORKLIST_ENTRY);
        arcDevExt.setDataRetentionPeriodExpiredCode(DATA_RETENTION_PERIOD_EXPIRED);
        arcDevExt.setFuzzyAlgorithmClass("org.dcm4che.soundex.ESoundex");
        arcDevExt.setConfigurationStaleTimeout(CONFIGURATION_STALE_TIMEOUT);
        arcDevExt.setWadoAttributesStaleTimeout(WADO_ATTRIBUTES_STALE_TIMEOUT);
        setAttributeFilters(arcDevExt);
        device.setKeyStoreURL("${jboss.server.config.url}/dcm4chee-arc/key.jks");
        device.setKeyStoreType("JKS");
        device.setKeyStorePin("secret");
        device.setThisNodeCertificates(config.deviceRef(name),
                (X509Certificate) keystore.getCertificate(name));
        for (String other : OTHER_DEVICES)
            device.setAuthorizedNodeCertificates(config.deviceRef(other),
                    (X509Certificate) keystore.getCertificate(other));
        Connection dicom = createConnection("dicom", "localhost", 11112);
        dicom.setMaxOpsInvoked(0);
        dicom.setMaxOpsPerformed(0);
        device.addConnection(dicom);
        Connection dicomTLS = new Connection("dicom-tls", "localhost", 2762);
        dicomTLS.setMaxOpsInvoked(0);
        dicomTLS.setMaxOpsPerformed(0);
        dicomTLS.setTlsCipherSuites(
                Connection.TLS_RSA_WITH_AES_128_CBC_SHA, 
                Connection.TLS_RSA_WITH_3DES_EDE_CBC_SHA);
        device.addConnection(dicomTLS);
        ApplicationEntity ae = createAE("DCM4CHEE",
                IMAGE_TSUIDS, VIDEO_TSUIDS, OTHER_TSUIDS, null, PIX_MANAGER);
        device.addApplicationEntity(ae);
        ae.addConnection(dicom);
        ae.addConnection(dicomTLS);
        ApplicationEntity adminAE = createAdminAE("DCM4CHEE_ADMIN",
                IMAGE_TSUIDS, VIDEO_TSUIDS, OTHER_TSUIDS, null, PIX_MANAGER);
        device.addApplicationEntity(adminAE);
        adminAE.addConnection(dicom);
        adminAE.addConnection(dicomTLS);
        HL7Application hl7App = new HL7Application("*");
        ArchiveHL7ApplicationExtension hl7AppExt = new ArchiveHL7ApplicationExtension();
        hl7App.addHL7ApplicationExtension(hl7AppExt);
        hl7App.setAcceptedMessageTypes(HL7_MESSAGE_TYPES);
        hl7App.setHL7DefaultCharacterSet("8859/1");
        hl7AppExt.addTemplatesURI("adt2dcm",
                "${jboss.server.config.url}/dcm4chee-arc/hl7-adt2dcm.xsl");
        hl7DevExt.addHL7Application(hl7App);
        Connection hl7 = new Connection("hl7", "localhost", 2575);
        hl7.setProtocol(Protocol.HL7);
        device.addConnection(hl7);
        hl7App.addConnection(hl7);
        Connection hl7TLS = new Connection("hl7-tls", "localhost", 12575);
        hl7TLS.setProtocol(Connection.Protocol.HL7);
        hl7TLS.setTlsCipherSuites(
                Connection.TLS_RSA_WITH_AES_128_CBC_SHA, 
                Connection.TLS_RSA_WITH_3DES_EDE_CBC_SHA);
        device.addConnection(hl7TLS);
        hl7App.addConnection(hl7TLS);
        AuditLogger auditLogger = new AuditLogger();
        device.addDeviceExtension(auditLogger);
        Connection auditUDP = new Connection("audit-udp", "localhost");
        auditUDP.setProtocol(Protocol.SYSLOG_UDP);
        device.addConnection(auditUDP);
        auditLogger.addConnection(auditUDP);
        auditLogger.setAuditSourceTypeCodes("4");
        auditLogger.setAuditRecordRepositoryDevice(arrDevice);
        return device ;
    }

    private void setAttributeFilters(ArchiveDeviceExtension device) {
        device.setAttributeFilter(Entity.Patient,
                new AttributeFilter(PATIENT_ATTRS));
        device.setAttributeFilter(Entity.Study,
                new AttributeFilter(STUDY_ATTRS));
        device.setAttributeFilter(Entity.Series,
                new AttributeFilter(SERIES_ATTRS));
        device.setAttributeFilter(Entity.Instance,
                new AttributeFilter(INSTANCE_ATTRS));
        device.setAttributeFilter(Entity.Visit,
                new AttributeFilter(VISIT_ATTRS));
        device.setAttributeFilter(Entity.ServiceRequest,
                new AttributeFilter(SERVICE_REQUEST_ATTRS));
        device.setAttributeFilter(Entity.RequestedProcedure,
                new AttributeFilter(REQUESTED_PROCEDURE_ATTRS));
        device.setAttributeFilter(Entity.ScheduledProcedureStep,
                new AttributeFilter(SPS_ATTRS));
        device.setAttributeFilter(Entity.PerformedProcedureStep,
                new AttributeFilter(PPS_ATTRS));
    }

    private Connection createConnection(String commonName,
            String hostname, int port, String... tlsCipherSuites) {
        Connection conn = new Connection(commonName, hostname, port);
        conn.setTlsCipherSuites(tlsCipherSuites);
        return conn;
    }


    private ApplicationEntity createAE(String aet,
            String[] image_tsuids, String[] video_tsuids, String[] other_tsuids,
            String pixConsumer, String pixManager) {
        ApplicationEntity ae = new ApplicationEntity(aet);
        ArchiveAEExtension aeExt = new ArchiveAEExtension();
        ae.addAEExtension(aeExt);
        ae.setAssociationAcceptor(true);
        ae.setAssociationInitiator(true);
        aeExt.setFileSystemGroupID("DEFAULT");
        aeExt.setInitFileSystemURI("${jboss.server.data.url}");
        aeExt.setSpoolFilePathFormat(new AttributesFormat(
                "archive/spool/{00020016,urlencoded}/{00020002}/{00020003}") );
        aeExt.setStorageFilePathFormat(new AttributesFormat(
                "archive/{now,date,yyyy/MM/dd}/{0020000D,hash}/{0020000E,hash}/{00080018,hash}") );
        aeExt.setDigestAlgorithm("MD5");
        aeExt.setRetrieveAETs(aet);
        aeExt.setStoreOriginalAttributes(true);
        aeExt.setPreserveSpoolFileOnFailure(true);
        aeExt.setSuppressWarningCoercionOfDataElements(false);
        aeExt.setMatchUnknown(true);
        aeExt.setSendPendingCGet(true);
        aeExt.setSendPendingCMoveInterval(5000);
        aeExt.addStoreDuplicate(
                new StoreDuplicate(
                        StoreDuplicate.Condition.NO_FILE,
                        StoreDuplicate.Action.STORE));
        aeExt.addStoreDuplicate(
                new StoreDuplicate(
                        StoreDuplicate.Condition.EQ_CHECKSUM,
                        StoreDuplicate.Action.IGNORE));
        aeExt.addStoreDuplicate(
                new StoreDuplicate(
                        StoreDuplicate.Condition.NE_CHECKSUM,
                        StoreDuplicate.Action.REPLACE));
        aeExt.addAttributeCoercion(new AttributeCoercion(null, 
                Dimse.C_STORE_RQ, 
                SCP,
                "ENSURE_PID",
                "${jboss.server.config.url}/dcm4chee-arc/ensure-pid.xsl"));
        aeExt.addAttributeCoercion(new AttributeCoercion(null, 
                Dimse.C_STORE_RQ, 
                SCU,
                "WITHOUT_PN",
                "${jboss.server.config.url}/dcm4chee-arc/nullify-pn.xsl"));
        addTCs(ae, null, SCP, IMAGE_CUIDS, image_tsuids);
        addTCs(ae, null, SCP, VIDEO_CUIDS, video_tsuids);
        addTCs(ae, null, SCP, OTHER_CUIDS, other_tsuids);
        addTCs(ae, null, SCU, IMAGE_CUIDS, image_tsuids);
        addTCs(ae, null, SCU, VIDEO_CUIDS, video_tsuids);
        addTCs(ae, null, SCU, OTHER_CUIDS, other_tsuids);
        addTCs(ae, EnumSet.allOf(QueryOption.class), SCP, QUERY_CUIDS, UID.ImplicitVRLittleEndian);
        addTCs(ae, EnumSet.of(QueryOption.RELATIONAL), SCP, RETRIEVE_CUIDS, UID.ImplicitVRLittleEndian);
        addTC(ae, null, SCP, UID.CompositeInstanceRetrieveWithoutBulkDataGET, UID.ImplicitVRLittleEndian);
        addTC(ae, null, SCP, UID.StorageCommitmentPushModelSOPClass, UID.ImplicitVRLittleEndian);
        addTC(ae, null, SCP, UID.ModalityPerformedProcedureStepSOPClass, UID.ImplicitVRLittleEndian);
        addTC(ae, null, SCU, UID.ModalityPerformedProcedureStepSOPClass, UID.ImplicitVRLittleEndian);
        addTC(ae, null, SCU, UID.InstanceAvailabilityNotificationSOPClass, UID.ImplicitVRLittleEndian);
        addTC(ae, null, SCP, UID.VerificationSOPClass, UID.ImplicitVRLittleEndian);
        addTC(ae, null, SCU, UID.VerificationSOPClass, UID.ImplicitVRLittleEndian);
        aeExt.setReturnOtherPatientIDs(true);
        aeExt.setReturnOtherPatientNames(true);
        aeExt.setLocalPIXConsumerApplication(pixConsumer);
        aeExt.setRemotePIXManagerApplication(pixManager);
        return ae;
    }

    private ApplicationEntity createAdminAE(String aet,
            String[] image_tsuids, String[] video_tsuids, String[] other_tsuids,
            String pixConsumer, String pixManager) {
        ApplicationEntity ae = new ApplicationEntity(aet);
        ArchiveAEExtension aeExt = new ArchiveAEExtension();
        ae.addAEExtension(aeExt);
        ae.setAssociationAcceptor(true);
        ae.setAssociationInitiator(true);
        aeExt.setMatchUnknown(true);
        aeExt.setSendPendingCGet(true);
        aeExt.setSendPendingCMoveInterval(PENDING_CMOVE_INTERVAL);
        aeExt.setShowRejectedInstances(true);
        addTCs(ae, null, SCU, IMAGE_CUIDS, image_tsuids);
        addTCs(ae, null, SCU, VIDEO_CUIDS, video_tsuids);
        addTCs(ae, null, SCU, OTHER_CUIDS, other_tsuids);
        addTCs(ae, EnumSet.allOf(QueryOption.class), SCP, QUERY_CUIDS, UID.ImplicitVRLittleEndian);
        addTCs(ae, EnumSet.of(QueryOption.RELATIONAL), SCP, RETRIEVE_CUIDS, UID.ImplicitVRLittleEndian);
        addTC(ae, null, SCP, UID.CompositeInstanceRetrieveWithoutBulkDataGET, UID.ImplicitVRLittleEndian);
        addTC(ae, null, SCP, UID.VerificationSOPClass, UID.ImplicitVRLittleEndian);
        addTC(ae, null, SCU, UID.VerificationSOPClass, UID.ImplicitVRLittleEndian);
        aeExt.setReturnOtherPatientIDs(true);
        aeExt.setReturnOtherPatientNames(true);
        aeExt.setLocalPIXConsumerApplication(pixConsumer);
        aeExt.setRemotePIXManagerApplication(pixManager);
        return ae;
    }

    private void addTCs(ApplicationEntity ae, EnumSet<QueryOption> queryOpts,
            TransferCapability.Role role, String[] cuids, String... tss) {
        for (String cuid : cuids)
            addTC(ae, queryOpts, role, cuid, tss);
    }

    private void addTC(ApplicationEntity ae, EnumSet<QueryOption> queryOpts,
            TransferCapability.Role role, String cuid, String... tss) {
        String name = UID.nameOf(cuid).replace('/', ' ');
        TransferCapability tc = new TransferCapability(name + ' ' + role, cuid, role, tss);
        tc.setQueryOptions(queryOpts);
        ae.addTransferCapability(tc);
    }
}
