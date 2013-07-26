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

package org.dcm4chee.archive.query.dao;

import static org.junit.Assert.*;

import java.util.TreeSet;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.dcm4che.data.VR;
import org.dcm4chee.archive.common.IDWithIssuer;
import org.dcm4chee.archive.common.QueryParam;
import org.dcm4chee.archive.dao.SeriesService;
import org.dcm4chee.archive.test.util.Deployments;
import org.dcm4chee.archive.test.util.ParamFactory;
import org.dcm4chee.archive.util.BeanLocator;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
@RunWith(Arquillian.class)
public class QueryServiceTest {

    private QueryService queryService;

    @Deployment
    public static WebArchive createDeployment() {
        WebArchive arc = Deployments.createWebArchive()
                .addClass(ParamFactory.class)
                .addClass(BeanLocator.class)
                .addClass(SeriesService.class)
                .addPackage("org.dcm4chee.archive.common")
                .addPackage("org.dcm4chee.archive.exception")
                .addPackage("org.dcm4chee.archive.query.dao")
                .addPackage("org.dcm4chee.archive.util.query")
                .addAsWebInfResource("query-ejb-jar.xml", "ejb-jar.xml")
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
        return arc;
    }

    @Before
    public void initQueryService() {
        queryService = BeanLocator.lookup(QueryService.class,
                "java:global/test/QueryService");
    }

    @After
    public void closeQueryService() {
        queryService.close();
    }

    @Test
    public void testFindPatientByPatientID() {
        QueryParam queryParam = ParamFactory.createQueryParam();
        IDWithIssuer[] pids = { new IDWithIssuer("DOB*") };
        queryService.createPatientQuery(pids, null, queryParam);
        assertArrayEquals(
                new String[] { "DOB_20010101", "DOB_20020202", "DOB_NONE" },
                matches(queryService, Tag.PatientID));
    }

    @Test
    public void testFindPatientByPatientName() {
        QueryParam queryParam = ParamFactory.createQueryParam();
        queryService.createPatientQuery(null, 
                attrs(Tag.PatientName, VR.PN, "OOMIYA^SHOUGO"),
                queryParam);
        assertArrayEquals(new String[] { "OOMIYA_SHOUGO" },
                matches(queryService, Tag.PatientID));
    }

    @Test
    public void testFindPatientByJapanesePatientName1() {
        testFindPatientByJapanesePatientName(
                "OOMIYA^SHOUGO=大宮^省吾=オオミヤ^ショウゴ","OOMIYA_SHOUGO");
    }

    @Test
    public void testFindPatientByJapanesePatientName2() {
        testFindPatientByJapanesePatientName("大宮^省吾", "OOMIYA_SHOUGO");
    }

    private void testFindPatientByJapanesePatientName(String name,
            String... expected_ids) {
        QueryParam queryParam = ParamFactory.createQueryParam();
        Attributes keys = new Attributes(2);
        keys.setString(Tag.SpecificCharacterSet, VR.CS,
                "ISO 2022 IR 6", "ISO 2022 IR 87");
        keys.setString(Tag.PatientName, VR.PN, name);
        queryService.createPatientQuery(null, keys, queryParam);
        assertArrayEquals(expected_ids, matches(queryService, Tag.PatientID));
    }

    @Test
    public void testFindPatientByPatientNameFuzzy1() {
        testFindPatientByFuzzyPatientName("LUCAS^GEORGE", false, 
                "FUZZY_GEORGE", "FUZZY_JOERG");
    }

    @Test
    public void testFindPatientByPatientNameFuzzy2() {
        testFindPatientByFuzzyPatientName("LUCAS^JÖRG", false,
                "FUZZY_GEORGE", "FUZZY_JOERG");
    }

    @Test
    public void testFindPatientByPatientNameFuzzy3() {
        testFindPatientByFuzzyPatientName("LUKE", false, "FUZZY_LUKE");
    }

    @Test
    public void testFindPatientByPatientNameFuzzy4() {
        testFindPatientByFuzzyPatientName("LU*", false, 
                "FUZZY_GEORGE", "FUZZY_JOERG", "FUZZY_LUKE");
    }

    @Test
    public void testFindPatientByPatientNameFuzzy5() {
        testFindPatientByFuzzyPatientName("LU*", true,
                "FUZZY_GEORGE", "FUZZY_JOERG", "FUZZY_LUKE",
                "FUZZY_NONE", "FUZZY_NUMERICAL");
    }

    private void testFindPatientByFuzzyPatientName(String name, boolean matchUnknown,
            String... expected_ids) {
        QueryParam queryParam = ParamFactory.createQueryParam();
        queryParam.setFuzzySemanticMatching(true);
        queryParam.setMatchUnknown(matchUnknown);
        IDWithIssuer[] pids = { new IDWithIssuer("FUZZY*") };
        queryService.createPatientQuery(pids,
                attrs(Tag.PatientName, VR.PN, name),
                queryParam);
        assertArrayEquals(expected_ids, matches(queryService, Tag.PatientID));
    }

    @Test
    public void testFindPatientByPatientBirthDate() {
        testFindPatientByPatientBirthDate("20010101", false, "DOB_20010101");
    }

    @Test
    public void testFindPatientByPatientBirthDateRange() {
        testFindPatientByPatientBirthDate("20010101-20020202", false,
                "DOB_20010101", "DOB_20020202");
    }

    @Test
    public void testFindPatientByPatientBirthDateMatchUnknown() {
        testFindPatientByPatientBirthDate("20010101", true,
                "DOB_20010101", "DOB_NONE");
    }

    @Test
    public void testFindPatientByPatientBirthDateRangeMatchUnknown() {
        testFindPatientByPatientBirthDate("20010101-20020202", true,
                "DOB_20010101", "DOB_20020202", "DOB_NONE");
    }

    private void testFindPatientByPatientBirthDate(String date, boolean matchUnknown,
            String... expected_ids) {
        QueryParam queryParam = ParamFactory.createQueryParam();
        queryParam.setMatchUnknown(matchUnknown);
        IDWithIssuer[] pids = { new IDWithIssuer("DOB*") };
        queryService.createPatientQuery(pids,
                attrs(Tag.PatientBirthDate, VR.DA, date), queryParam);
        assertArrayEquals(expected_ids, matches(queryService, Tag.PatientID));
    }

    @Test
    public void testFindStudyByModalitiesInStudyPR() {
        testFindStudyByModalitiesInStudy("PR", false,
                "CT+PR", "MR+PR");
    }

    @Test
    public void testFindStudyByModalitiesInStudyMatchUnknownPR() {
        testFindStudyByModalitiesInStudy("PR", true,
                "CT+PR",
                "MR+PR",
                "NO_MODALITY");
    }

    @Test
    public void testFindStudyByModalitiesInStudyCT() {
        testFindStudyByModalitiesInStudy("CT", false,
                "CT+PR");
    }

    @Test
    public void testFindStudyByModalitiesInStudyMatchUnknownCT() {
        testFindStudyByModalitiesInStudy("CT", true,
                "CT+PR",
                "NO_MODALITY");
    }

    private void testFindStudyByModalitiesInStudy(String modality,
            boolean matchUnknown, String... expected_ids) {
        QueryParam queryParam = ParamFactory.createQueryParam();
        queryParam.setMatchUnknown(matchUnknown);
        IDWithIssuer[] pids = { new IDWithIssuer("MODS_IN_STUDY") };
        queryService.createStudyQuery(pids,
                attrs(Tag.ModalitiesInStudy, VR.CS, modality), queryParam);
        assertArrayEquals(expected_ids, matches(queryService, Tag.StudyID));
    }

    @Test
    public void testFindStudyByDateTime() {
        testFindStudyByDateTime("20110620", "103000.000", false, false,
                "DT_20110620_1030");
    }

    @Test
    public void testFindStudyByOpenEndTime() {
        testFindStudyByDateTime(null, "1030-", false, false,
                "DT_20100620_1030",
                "DT_20100620_1430",
                "DT_20110620_1030",
                "DT_20110620_1430");
    }

    @Test
    public void testFindStudyByOpenStartTime() {
       testFindStudyByDateTime(null, "-1430", false, false,
                "DT_20100620_1030",
                "DT_20100620_1430",
                "DT_20110620_1030",
                "DT_20110620_1430");
    }
    
    @Test
    public void testFindStudyByDateTimeMatchUnknown() {
        testFindStudyByDateTime("20110620", "103000.000", true, false,
                "DT_20110620",
                "DT_20110620_1030",
                "DT_NONE");
    }

    @Test
    public void testFindStudyByTimeRange() {
        testFindStudyByDateTime(null, "1030-1430", false, false,
                "DT_20100620_1030",
                "DT_20100620_1430",
                "DT_20110620_1030",
                "DT_20110620_1430");
    }

    @Test
    public void testFindStudyByDateRange() {
        testFindStudyByDateTime("20100620-20110620", null, false, false,
                "DT_20100620",
                "DT_20100620_1030",
                "DT_20100620_1430",
                "DT_20110620",
                "DT_20110620_1030",
                "DT_20110620_1430");
    }

    @Test
    public void testFindStudyByDateTimeRange() {
        testFindStudyByDateTime("20100620-20110620", "1030-1430", false, false,
                "DT_20100620_1030",
                "DT_20100620_1430",
                "DT_20110620_1030",
                "DT_20110620_1430");
    }

    @Test
    public void testFindStudyByDateTimeRangeCombined() {
        testFindStudyByDateTime("20100620-20110620", "1040-1430", false, true,
                "DT_20100620",
                "DT_20100620_1430",
                "DT_20110620",
                "DT_20110620_1030",
                "DT_20110620_1430");
    }

    @Test
    public void testFindStudyByDateTimeRangeCombinedOpenEndRange() {
        testFindStudyByDateTime("20100620-", "1040-", false, true,
                "DT_20100620",
                "DT_20100620_1430",
                "DT_20110620",
                "DT_20110620_1030",
                "DT_20110620_1430");
    }

    @Test
    public void testFindStudyByDateTimeRangeCombinedOpenStartRange() {
        testFindStudyByDateTime("-20110620", "-1420", false, true,
                "DT_20100620",
                "DT_20100620_1030",
                "DT_20100620_1430",
                "DT_20110620",
                "DT_20110620_1030");
    }

    @Test
    public void testFindStudyByDateTimeRangeCombinedMatchUnknown() {
        testFindStudyByDateTime("20100620-20110620", "1040-1430", true, true,
                "DT_20100620",
                "DT_20100620_1430",
                "DT_20110620",
                "DT_20110620_1030",
                "DT_20110620_1430",
                "DT_NONE");
    }

    private void testFindStudyByDateTime(String date, String time,
            boolean matchUnknown, boolean combinedDatetimeMatching,
            String... expected_ids) {
        QueryParam queryParam = ParamFactory.createQueryParam();
        queryParam.setMatchUnknown(matchUnknown);
        queryParam.setCombinedDatetimeMatching(combinedDatetimeMatching);
        IDWithIssuer[] pids = { new IDWithIssuer("RANGE-MATCHING") };
        Attributes keys = new Attributes(2);
        if (date != null)
            keys.setString(Tag.StudyDate, VR.DA, date);
        if (time != null)
            keys.setString(Tag.StudyTime, VR.TM, time);
        queryService.createStudyQuery(pids, keys, queryParam);
        assertArrayEquals(expected_ids, matches(queryService, Tag.StudyID));
    }

    @Test
    public void testFindStudyByIssuerOfAccessionNumber() {
        testFindStudyByIssuerOfAccessionNumber(
                "A1234", "DCM4CHEE_TESTDATA_ACCNO_ISSUER_1", false,
                "ACCNO_ISSUER_1");
    }

    @Test
    public void testFindStudyByIssuerOfAccessionNumberMatchUnknown() {
        testFindStudyByIssuerOfAccessionNumber(
                "A1234", "DCM4CHEE_TESTDATA_ACCNO_ISSUER_2", true,
                "ACCNO_ISSUER_2", "NO_ACCNO_ISSUER");
    }

    private void testFindStudyByIssuerOfAccessionNumber(
            String accno, String issuer, boolean matchUnknown,
            String... expected_ids) {
        QueryParam queryParam = ParamFactory.createQueryParam();
        queryParam.setMatchUnknown(matchUnknown);
        IDWithIssuer[] pids = { new IDWithIssuer("ISSUER_OF_ACCNO") };
        Attributes keys = new Attributes(2);
        keys.setString(Tag.AccessionNumber, VR.SH, accno);
        keys.newSequence(Tag.IssuerOfAccessionNumberSequence, 1)
            .add(attrs(Tag.LocalNamespaceEntityID, VR.UT, issuer));
        queryService.createStudyQuery(pids, keys, queryParam);
        assertArrayEquals(expected_ids, matches(queryService, Tag.StudyID));
    }

    @Test
    public void testFindStudyByProcedureCodes() {
        testFindStudyByProcedureCodes("PROC_CODE_1", "99DCM4CHEE_TEST", false,
                "PROC_CODE_1");
    }
    
    @Test
    public void testFindStudyByProcedureCodesMatchUnknown() {
        testFindStudyByProcedureCodes("PROC_CODE_2", "99DCM4CHEE_TEST", true,
                "NO_PROC_CODE", "PROC_CODE_2");
    }
    
    private void testFindStudyByProcedureCodes(
            String code, String designator, boolean matchUnknown,
            String... expected_ids) {
        QueryParam queryParam = ParamFactory.createQueryParam();
        queryParam.setMatchUnknown(matchUnknown);
        IDWithIssuer[] pids = { new IDWithIssuer("PROC_CODE_SEQ") };
        Attributes keys = new Attributes(1);
        Attributes item = new Attributes(3);
        item.setString(Tag.CodeValue, VR.SH, code);
        item.setString(Tag.CodingSchemeDesignator, VR.SH, designator);
        keys.newSequence(Tag.ProcedureCodeSequence, 1).add(item);
        queryService.createStudyQuery(pids, keys, queryParam);
        assertArrayEquals(expected_ids, matches(queryService, Tag.StudyID));
    }

    @Test
    public void testFindSeriesByModality() {
        QueryParam queryParam = ParamFactory.createQueryParam();
        IDWithIssuer[] pids = { new IDWithIssuer("MODS_IN_STUDY") };
        queryService.createSeriesQuery(pids, attrs(Tag.Modality, VR.CS, "PR"), queryParam);
        assertArrayEquals(
                new String[] { 
                        "1.2.40.0.13.1.1.99.16.2",
                        "1.2.40.0.13.1.1.99.17.2" },
                matches(queryService, Tag.SeriesInstanceUID));
    }

    @Test
    public void testFindSeriesByModalitiesInStudyPR() {
        testFindSeriesByModalitiesInStudy("PR", false,
                "1.2.40.0.13.1.1.99.16.1",
                "1.2.40.0.13.1.1.99.16.2",
                "1.2.40.0.13.1.1.99.17.1",
                "1.2.40.0.13.1.1.99.17.2");
    }

    @Test
    public void testFindSeriesByModalitiesInStudyMatchUnknownPR() {
        testFindSeriesByModalitiesInStudy("PR", true,
                "1.2.40.0.13.1.1.99.16.1",
                "1.2.40.0.13.1.1.99.16.2",
                "1.2.40.0.13.1.1.99.17.1",
                "1.2.40.0.13.1.1.99.17.2",
                "1.2.40.0.13.1.1.99.18.1");
    }
    
    @Test
    public void testFindSeriesByModalitiesInStudyCT() {
        testFindSeriesByModalitiesInStudy("CT", false,
                "1.2.40.0.13.1.1.99.16.1",
                "1.2.40.0.13.1.1.99.16.2");
    }

    @Test
    public void testFindSeriesByModalitiesInStudyMatchUnknownCT() {
        testFindSeriesByModalitiesInStudy("CT", true,
                "1.2.40.0.13.1.1.99.16.1",
                "1.2.40.0.13.1.1.99.16.2",
                "1.2.40.0.13.1.1.99.18.1");
    }

    private void testFindSeriesByModalitiesInStudy(String modality,
            boolean matchUnknown, String... expected_ids) {
        QueryParam queryParam = ParamFactory.createQueryParam();
        queryParam.setMatchUnknown(matchUnknown);
        IDWithIssuer[] pids = { new IDWithIssuer("MODS_IN_STUDY") };
        queryService.createSeriesQuery(pids,
                attrs(Tag.ModalitiesInStudy, VR.CS, modality), queryParam);
        assertArrayEquals(expected_ids, matches(queryService, Tag.SeriesInstanceUID));
    }

    @Test
    public void testFindSeriesByRequestAttributesSequence() {
        QueryParam queryParam = ParamFactory.createQueryParam();
        IDWithIssuer[] pids = { new IDWithIssuer("REQ_ATTRS_SEQ") };
        Attributes keys = new Attributes(1);
        Attributes item = new Attributes(4);
        keys.newSequence(Tag.RequestAttributesSequence, 1).add(item);
        item.setString(Tag.RequestedProcedureID, VR.SH, "P-9913");
        item.setString(Tag.ScheduledProcedureStepID, VR.SH, "9913.1");
        item.setString(Tag.AccessionNumber, VR.SH, "A1234");
        Attributes issuer = new Attributes(1);
        item.newSequence(Tag.IssuerOfAccessionNumberSequence, 1).add(issuer);
        issuer.setString(Tag.LocalNamespaceEntityID, VR.UT, "DCM4CHEE_TESTDATA_ACCNO_ISSUER_1");
        
        queryService.createSeriesQuery(pids, keys, queryParam);
        assertArrayEquals(new String[] { "1.2.40.0.13.1.1.99.13.1" }, 
                matches(queryService, Tag.SeriesInstanceUID));
    }

    @Test
    public void testFindInstanceByVerificationFlag() {
        QueryParam queryParam = ParamFactory.createQueryParam();
        IDWithIssuer[] pids = { new IDWithIssuer("VERIFYING_OBSERVER_SEQ") };
        Attributes keys = new Attributes(2);
        keys.setString(Tag.Modality, VR.CS, "SR");
        keys.setString(Tag.VerificationFlag, VR.CS, "VERIFIED");
        queryService.createInstanceQuery(pids, keys, queryParam);
        assertArrayEquals(
                new String[] { 
                        "1.2.40.0.13.1.1.99.23.1.2",
                        "1.2.40.0.13.1.1.99.23.1.3" }, 
                matches(queryService, Tag.SOPInstanceUID));
    }

    @Test
    public void testFindInstanceByConceptCodeSequence() {
        testFindInstanceByConceptCodeSequence(
                "CONCEPT_NAME_1", "99DCM4CHEE_TEST", false,
                "1.2.40.0.13.1.1.99.22.1.1");
    }

    @Test
    public void testFindInstanceByConceptCodeSequenceMatchUnknown() {
        testFindInstanceByConceptCodeSequence(
                "CONCEPT_NAME_2", "99DCM4CHEE_TEST", true,
                "1.2.40.0.13.1.1.99.22.1.2", "1.2.40.0.13.1.1.99.22.1.3");
    }

    private void testFindInstanceByConceptCodeSequence(
            String code, String designator, boolean matchUnknown,
            String... expected_uids) {
        QueryParam queryParam = ParamFactory.createQueryParam();
        queryParam.setMatchUnknown(matchUnknown);
        IDWithIssuer[] pids = { new IDWithIssuer("CONCEPT_NAME_CODE_SEQ") };
        Attributes keys = new Attributes(1);
        Attributes item = new Attributes(3);
        item.setString(Tag.CodeValue, VR.SH, code);
        item.setString(Tag.CodingSchemeDesignator, VR.SH, designator);
        keys.newSequence(Tag.ConceptNameCodeSequence, 1).add(item);
        queryService.createInstanceQuery(pids, keys, queryParam);
        assertArrayEquals(expected_uids,
                matches(queryService, Tag.SOPInstanceUID));
    }

    @Test
    public void testFindInstanceByVerifyingObserver() {
        testFindInstanceByVerifyingObserver(
                "201106300830", "VERIFYINGOBSERVER1", false,
                "1.2.40.0.13.1.1.99.23.1.2",
                "1.2.40.0.13.1.1.99.23.1.3");
    }

    @Test
    public void testFindInstanceByVerifyingObserverMatchUnknown() {
        testFindInstanceByVerifyingObserver(
                "201106300830", "VERIFYINGOBSERVER1", true,
                "1.2.40.0.13.1.1.99.23.1.1",
                "1.2.40.0.13.1.1.99.23.1.2",
                "1.2.40.0.13.1.1.99.23.1.3");
    }

    @Test
    public void testFindInstanceByVerifyingObserverRange() {
        testFindInstanceByVerifyingObserver(
                "201106300000-20110701235900", null, false,
                "1.2.40.0.13.1.1.99.23.1.2",
                "1.2.40.0.13.1.1.99.23.1.3");
    }
    
    private void testFindInstanceByVerifyingObserver(
            String dateTime, String name, boolean matchUnknown,
            String... expected_uids) {
        QueryParam queryParam = ParamFactory.createQueryParam();
        queryParam.setMatchUnknown(matchUnknown);
        IDWithIssuer[] pids = { new IDWithIssuer("VERIFYING_OBSERVER_SEQ") };
        Attributes keys = new Attributes(1);
        Attributes item = new Attributes(2);
        item.setString(Tag.VerificationDateTime, VR.DT, dateTime);
        item.setString(Tag.VerifyingObserverName, VR.PN, name);
        keys.newSequence(Tag.VerifyingObserverSequence, 1).add(item);
        queryService.createInstanceQuery(pids, keys, queryParam);
        assertArrayEquals(expected_uids,
                matches(queryService, Tag.SOPInstanceUID));
    }

    @Test
    public void testFindInstanceByContentItem() {
        QueryParam queryParam = ParamFactory.createQueryParam();
        IDWithIssuer[] pids = { new IDWithIssuer("TF_INFO") };
        Attributes keys = new Attributes(1);
        Sequence contentSeq = keys.newSequence(Tag.ContentSequence, 2);
        contentSeq.add(contentSequenceItem("TCE101", "IHERADTF", null,
                "CONTAINS", "Max"));
        contentSeq.add(contentSequenceItem("TCE104", "IHERADTF", null,
                "CONTAINS", "Max's Abstract"));
        queryService.createInstanceQuery(pids, keys, queryParam);
        assertArrayEquals(
                new String[] { "1.2.40.0.13.1.1.99.27.1.1" },
                matches(queryService, Tag.SOPInstanceUID));
    }
    
    @Test
    public void testFindInstanceByContentItemSequence() {
        QueryParam queryParam = ParamFactory.createQueryParam();
        IDWithIssuer[] pids = { new IDWithIssuer("TF_INFO") };
        Attributes keys = new Attributes(1);
        Sequence contentSeq = keys.newSequence(Tag.ContentSequence, 2);
        contentSeq.add(contentSequenceItem("TCE104", "IHERADTF", null,
                "CONTAINS", "Moritz's Abstract"));
        contentSeq.add(contentSequenceCodeItem("TCE105", "IHERADTF", null,
                "466.0", "I9C", null, "CONTAINS"));
        queryService.createInstanceQuery(pids, keys, queryParam);
        assertArrayEquals(
                new String[] { "1.2.40.0.13.1.1.99.27.1.2" },
                matches(queryService, Tag.SOPInstanceUID));
    }

    private Attributes contentSequenceCodeItem(String value, String designator,
            String version, String value2, String designator2, String version2,
            String relationshipType) {
        Attributes item = new Attributes(4);
        item.setString(Tag.RelationshipType, VR.CS, relationshipType);
        item.setString(Tag.ValueType, VR.CS, "CODE");
        Attributes conceptName = new Attributes(3);
        conceptName.setString(Tag.CodeValue, VR.SH, value);
        conceptName.setString(Tag.CodingSchemeDesignator, VR.SH, designator);
        conceptName.setString(Tag.CodingSchemeVersion, VR.SH, version);
        item.newSequence(Tag.ConceptNameCodeSequence, 1).add(conceptName);
        Attributes conceptCode = new Attributes(3);
        conceptCode.setString(Tag.CodeValue, VR.SH, value2);
        conceptCode.setString(Tag.CodingSchemeDesignator, VR.SH, designator2);
        conceptCode.setString(Tag.CodingSchemeVersion, VR.SH, version2);
        item.newSequence(Tag.ConceptCodeSequence, 1).add(conceptCode);
        return item;
    }

    private Attributes contentSequenceItem(String value, String designator,
            String version, String relationshipType, String textValue) {
        Attributes item = new Attributes(4);
        item.setString(Tag.RelationshipType, VR.CS, relationshipType);
        item.setString(Tag.TextValue, VR.UT, textValue);
        item.setString(Tag.ValueType, VR.CS, "TEXT");
        Attributes nestedDs = new Attributes(3);
        nestedDs.setString(Tag.CodeValue, VR.SH, value);
        nestedDs.setString(Tag.CodingSchemeDesignator, VR.SH, designator);
        nestedDs.setString(Tag.CodingSchemeVersion, VR.SH, version);
        item.newSequence(Tag.ConceptNameCodeSequence, 1).add(nestedDs);
        return item;
    }

    private Attributes attrs(int tag, VR vr, String value) {
        Attributes attrs = new Attributes(1);
        attrs.setString(tag, vr, value);
        return attrs;
    }

    private String[] matches(QueryService queryService, int tag) {
        TreeSet<String> patids = new TreeSet<String>();
        while (queryService.hasMoreMatches())
            patids.add(queryService.nextMatch().getString(tag));
        return patids.toArray(new String[patids.size()]);
    }
}
