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
import org.dcm4che.data.Tag;
import org.dcm4che.data.VR;
import org.dcm4chee.archive.common.IDWithIssuer;
import org.dcm4chee.archive.common.QueryParam;
import org.dcm4chee.archive.test.util.Deployments;
import org.dcm4chee.archive.test.util.ParamFactory;
import org.dcm4chee.archive.util.BeanLocator;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
@RunWith(Arquillian.class)
public class QueryServiceTest {

    @Deployment
    public static WebArchive createDeployment() {
        WebArchive arc = Deployments.createWebArchive()
                .addClass(ParamFactory.class)
                .addClass(BeanLocator.class)
                .addPackage("org.dcm4chee.archive.common")
                .addPackage("org.dcm4chee.archive.exception")
                .addPackage("org.dcm4chee.archive.query.dao")
                .addPackage("org.dcm4chee.archive.util.query")
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
        return arc;
    }

    private QueryService queryService() {
        return BeanLocator.lookup(QueryService.class,
                "java:global/test/QueryService");
    }

    @Test
    public void testFindPatientByPatientID() throws Exception {
        QueryParam queryParam = ParamFactory.createQueryParam();
        IDWithIssuer[] pids = { new IDWithIssuer("DOB*") };
        QueryService queryService = queryService();
        try {
            queryService.findPatients(pids, null, queryParam);
            assertArrayEquals(
                    new String[] { "DOB_20010101", "DOB_20020202", "DOB_NONE" },
                    matches(queryService, Tag.PatientID));
        } finally {
            queryService.close();
        }
    }

    @Test
    public void testFindPatientByPatientName() throws Exception {
        QueryParam queryParam = ParamFactory.createQueryParam();
        QueryService queryService = queryService();
        try {
            queryService.findPatients(null, 
                    attrs(Tag.PatientName, VR.PN, "OOMIYA^SHOUGO"),
                    queryParam);
            assertArrayEquals(new String[] { "OOMIYA_SHOUGO" },
                    matches(queryService, Tag.PatientID));
        } finally {
            queryService.close();
        }
    }

    @Test
    public void testFindPatientByJapanesePatientName1() throws Exception {
        testFindPatientByJapanesePatientName(
                "OOMIYA^SHOUGO=大宮^省吾=オオミヤ^ショウゴ","OOMIYA_SHOUGO");
    }

    @Test
    public void testFindPatientByJapanesePatientName2() throws Exception {
        testFindPatientByJapanesePatientName("大宮^省吾", "OOMIYA_SHOUGO");
    }

    private void testFindPatientByJapanesePatientName(String name,
            String... expected_ids) {
        QueryParam queryParam = ParamFactory.createQueryParam();
        Attributes keys = new Attributes(2);
        keys.setString(Tag.SpecificCharacterSet, VR.CS,
                "ISO 2022 IR 6", "ISO 2022 IR 87");
        keys.setString(Tag.PatientName, VR.PN, name);
        QueryService queryService = queryService();
        try {
            queryService.findPatients(null, keys, queryParam);
            assertArrayEquals(expected_ids, matches(queryService, Tag.PatientID));
        } finally {
            queryService.close();
        }
    }

    @Test
    public void testFindPatientByPatientNameFuzzy1() throws Exception {
        testFindPatientByFuzzyPatientName("LUCAS^GEORGE", false, 
                "FUZZY_GEORGE", "FUZZY_JOERG");
    }

    @Test
    public void testFindPatientByPatientNameFuzzy2() throws Exception {
        testFindPatientByFuzzyPatientName("LUCAS^JÖRG", false,
                "FUZZY_GEORGE", "FUZZY_JOERG");
    }

    @Test
    public void testFindPatientByPatientNameFuzzy3() throws Exception {
        testFindPatientByFuzzyPatientName("LUKE", false, "FUZZY_LUKE");
    }

    @Test
    public void testFindPatientByPatientNameFuzzy4() throws Exception {
        testFindPatientByFuzzyPatientName("LU*", false, 
                "FUZZY_GEORGE", "FUZZY_JOERG", "FUZZY_LUKE");
    }

    @Test
    public void testFindPatientByPatientNameFuzzy5() throws Exception {
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
        QueryService queryService = queryService();
        try {
            queryService.findPatients(pids,
                    attrs(Tag.PatientName, VR.PN, name),
                    queryParam);
            assertArrayEquals(expected_ids, matches(queryService, Tag.PatientID));
        } finally {
            queryService.close();
        }
    }

    @Test
    public void testFindPatientByPatientBirthDate() throws Exception {
        testFindPatientByPatientBirthDate("20010101", false, "DOB_20010101");
    }

    @Test
    public void testFindPatientByPatientBirthDateRange() throws Exception {
        testFindPatientByPatientBirthDate("20010101-20020202", false,
                "DOB_20010101", "DOB_20020202");
    }

    @Test
    public void testFindPatientByPatientBirthDateMatchUnknown() throws Exception {
        testFindPatientByPatientBirthDate("20010101", true,
                "DOB_20010101", "DOB_NONE");
    }

    @Test
    public void testFindPatientByPatientBirthDateRangeMatchUnknown() throws Exception {
        testFindPatientByPatientBirthDate("20010101-20020202", true,
                "DOB_20010101", "DOB_20020202", "DOB_NONE");
    }

    private void testFindPatientByPatientBirthDate(String date, boolean matchUnknown,
            String... expected_ids) {
        QueryParam queryParam = ParamFactory.createQueryParam();
        queryParam.setMatchUnknown(matchUnknown);
        IDWithIssuer[] pids = { new IDWithIssuer("DOB*") };
        QueryService queryService = queryService();
        try {
            queryService.findPatients(pids,
                    attrs(Tag.PatientBirthDate, VR.DA, date), queryParam);
            assertArrayEquals(expected_ids, matches(queryService, Tag.PatientID));
        } finally {
            queryService.close();
        }
    }

    @Test
    public void testFindStudyByModalitiesInStudyPR() throws Exception {
        testFindStudyByModalitiesInStudy("PR", false,
                "CT+PR", "MR+PR");
    }

    @Test
    public void testFindStudyByModalitiesInStudyMatchUnknownPR() throws Exception {
        testFindStudyByModalitiesInStudy("PR", true,
                "CT+PR",
                "MR+PR",
                "NO_MODALITY");
    }

    @Test
    public void testFindStudyByModalitiesInStudyCT() throws Exception {
        testFindStudyByModalitiesInStudy("CT", false,
                "CT+PR");
    }

    @Test
    public void testFindStudyByModalitiesInStudyMatchUnknownCT() throws Exception {
        testFindStudyByModalitiesInStudy("CT", true,
                "CT+PR",
                "NO_MODALITY");
    }

    private void testFindStudyByModalitiesInStudy(String modality,
            boolean matchUnknown, String... expected_ids) {
        QueryParam queryParam = ParamFactory.createQueryParam();
        queryParam.setMatchUnknown(matchUnknown);
        IDWithIssuer[] pids = { new IDWithIssuer("MODS_IN_STUDY") };
        QueryService queryService = queryService();
        try {
            queryService.findStudies(pids,
                    attrs(Tag.ModalitiesInStudy, VR.CS, modality), queryParam);
            assertArrayEquals(expected_ids, matches(queryService, Tag.StudyID));
        } finally {
            queryService.close();
        }
    }

    @Test
    public void testFindStudyByDateTime() throws Exception {
        testFindStudyByDateTime("20110620", "103000.000", false, false,
                "DT_20110620_1030");
    }

    @Test
    public void testFindStudyByOpenEndTime() throws Exception {
        testFindStudyByDateTime(null, "1030-", false, false,
                "DT_20100620_1030",
                "DT_20100620_1430",
                "DT_20110620_1030",
                "DT_20110620_1430");
    }

    @Test
    public void testFindStudyByOpenStartTime() throws Exception {
       testFindStudyByDateTime(null, "-1430", false, false,
                "DT_20100620_1030",
                "DT_20100620_1430",
                "DT_20110620_1030",
                "DT_20110620_1430");
    }
    
    @Test
    public void testFindStudyByDateTimeMatchUnknown() throws Exception {
        testFindStudyByDateTime("20110620", "103000.000", true, false,
                "DT_20110620",
                "DT_20110620_1030",
                "DT_NONE");
    }

    @Test
    public void testFindStudyByTimeRange() throws Exception {
        testFindStudyByDateTime(null, "1030-1430", false, false,
                "DT_20100620_1030",
                "DT_20100620_1430",
                "DT_20110620_1030",
                "DT_20110620_1430");
    }

    @Test
    public void testFindStudyByDateRange() throws Exception {
        testFindStudyByDateTime("20100620-20110620", null, false, false,
                "DT_20100620",
                "DT_20100620_1030",
                "DT_20100620_1430",
                "DT_20110620",
                "DT_20110620_1030",
                "DT_20110620_1430");
    }

    @Test
    public void testFindStudyByDateTimeRange() throws Exception {
        testFindStudyByDateTime("20100620-20110620", "1030-1430", false, false,
                "DT_20100620_1030",
                "DT_20100620_1430",
                "DT_20110620_1030",
                "DT_20110620_1430");
    }

    @Test
    public void testFindStudyByDateTimeRangeCombined() throws Exception {
        testFindStudyByDateTime("20100620-20110620", "1040-1430", false, true,
                "DT_20100620",
                "DT_20100620_1430",
                "DT_20110620",
                "DT_20110620_1030",
                "DT_20110620_1430");
    }

    @Test
    public void testFindStudyByDateTimeRangeCombinedOpenEndRange() throws Exception {
        testFindStudyByDateTime("20100620-", "1040-", false, true,
                "DT_20100620",
                "DT_20100620_1430",
                "DT_20110620",
                "DT_20110620_1030",
                "DT_20110620_1430");
    }

    @Test
    public void testFindStudyByDateTimeRangeCombinedOpenStartRange() throws Exception {
        testFindStudyByDateTime("-20110620", "-1420", false, true,
                "DT_20100620",
                "DT_20100620_1030",
                "DT_20100620_1430",
                "DT_20110620",
                "DT_20110620_1030");
    }

    @Test
    public void testFindStudyByDateTimeRangeCombinedMatchUnknown() throws Exception {
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
        QueryService queryService = queryService();
        try {
            queryService.findStudies(pids, keys, queryParam);
            assertArrayEquals(expected_ids, matches(queryService, Tag.StudyID));
        } finally {
            queryService.close();
        }
    }

    @Test
    public void testFindStudyByIssuerOfAccessionNumber() throws Exception {
        testFindStudyByIssuerOfAccessionNumber(
                "A1234", "DCM4CHEE_TESTDATA_ACCNO_ISSUER_1", false,
                "ACCNO_ISSUER_1");
    }

    @Test
    public void testFindStudyByIssuerOfAccessionNumberMatchUnknown() throws Exception {
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
        QueryService queryService = queryService();
        try {
            queryService.findStudies(pids, keys, queryParam);
            assertArrayEquals(expected_ids, matches(queryService, Tag.StudyID));
        } finally {
            queryService.close();
        }
    }

    @Test
    public void testFindStudyByProcedureCodes() throws Exception {
        testFindStudyByProcedureCodes("PROC_CODE_1", "99DCM4CHEE_TEST", false,
                "PROC_CODE_1");
    }
    
    @Test
    public void testFindStudyByProcedureCodesMatchUnknown() throws Exception {
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
        QueryService queryService = queryService();
        try {
            queryService.findStudies(pids, keys, queryParam);
            assertArrayEquals(expected_ids, matches(queryService, Tag.StudyID));
        } finally {
            queryService.close();
        }
    }

//    @Test
//    public void testFindStudyByStudyPermission() throws Exception {
//        String suids[] = {
//                "1.2.40.0.13.1.1.99.10",
//                "1.2.40.0.13.1.1.99.11",
//                "1.2.40.0.13.1.1.99.12" };
//        for (String suid : suids)
//            mgr.grantStudyPermission(suid, "DCM4CHEE_TEST", StudyPermissionAction.QUERY);
//        try {
//            queryService.findStudies(null, new Attributes(),
//                    queryParam(false, false, "DCM4CHEE_TEST", "FooBar"));
//            assertSetEquals(studyIUIDResultList(query), suids);
//            queryService.findStudies(null, new Attributes(), queryParam(false, false, "FooBar"));
//            assertTrue(studyIUIDResultList(query).isEmpty());
//        } finally {
//            queryService.close();
//            for (String suid : suids)
//                mgr.revokeStudyPermission(suid, "DCM4CHEE_TEST", StudyPermissionAction.QUERY);
//        }
//    }

    private Attributes attrs(int tag, VR vr, String value) {
        Attributes attrs = new Attributes(1);
        attrs.setString(tag, vr, value);
        return attrs;
    }

    private String[] matches(QueryService queryService, int tag) {
        TreeSet<String> patids = new TreeSet<String>();
        while (queryService.hasMoreMatches())
            patids.add(queryService.nextMatch().getString(tag));
        return patids.toArray(new String[patids.size()]) ;
    }
}
