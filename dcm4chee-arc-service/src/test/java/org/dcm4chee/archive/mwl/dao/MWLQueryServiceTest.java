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

package org.dcm4chee.archive.mwl.dao;

import static org.junit.Assert.*;

import java.util.TreeSet;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.VR;
import org.dcm4chee.archive.common.IDWithIssuer;
import org.dcm4chee.archive.common.QueryParam;
import org.dcm4chee.archive.entity.ScheduledProcedureStep;
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
public class MWLQueryServiceTest {

    @Deployment
    public static WebArchive createDeployment() {
        WebArchive arc = Deployments.createWebArchive()
                .addClass(ParamFactory.class)
                .addClass(BeanLocator.class)
                .addPackage("org.dcm4chee.archive.common")
                .addPackage("org.dcm4chee.archive.exception")
                .addPackage("org.dcm4chee.archive.mwl.dao")
                .addPackage("org.dcm4chee.archive.util.query")
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
        return arc;
    }

    private final QueryParam queryParam = ParamFactory.createQueryParam();

    private MWLQueryService queryService() {
        return BeanLocator.lookup(MWLQueryService.class,
                "java:global/test/MWLQueryService");
    }

   @Test
    public void testByPatientID() throws Exception {
        IDWithIssuer[] pids = { new IDWithIssuer("MWL_TEST") };
        MWLQueryService queryService = queryService();
        try {
            queryService.findScheduledProcedureSteps(pids, new Attributes(), queryParam);
            assertArrayEquals(new String[] { "9933.1", "9934.1", "9934.2" }, 
                    spsids(queryService));
        } finally {
            queryService.close();
        }
     }

    @Test
    public void testByModality() throws Exception {
        MWLQueryService queryService = queryService();
        try {
            queryService.findScheduledProcedureSteps(
                    null,
                    sps(Tag.Modality, VR.CS, "CT"),
                    queryParam);
            assertArrayEquals(new String[] { "9933.1" }, spsids(queryService));
        } finally {
            queryService.close();
        }
    }

    @Test
    public void testByAccessionNumber() throws Exception {
        MWLQueryService queryService = queryService();
        try {
            queryService.findScheduledProcedureSteps(
                    null,
                    attrs(Tag.AccessionNumber, VR.SH, "MWL_TEST"),
                    queryParam);
            assertArrayEquals(
                    new String[] { "9933.1", "9934.1", "9934.2" },
                    spsids(queryService));
        } finally {
            queryService.close();
        }
    }

    @Test
    public void testByStudyInstanceUID() throws Exception {
        MWLQueryService queryService = queryService();
        try {
            queryService.findScheduledProcedureSteps(
                    null,
                    attrs(Tag.StudyInstanceUID, VR.UI, "1.2.40.0.13.1.1.99.33"),
                    queryParam);
            assertArrayEquals(new String[] { "9933.1" }, spsids(queryService));
        } finally {
            queryService.close();
        }
    }

    @Test
    public void testByRequestedProcedureID() throws Exception {
        MWLQueryService queryService = queryService();
        try {
            queryService.findScheduledProcedureSteps(
                    null,
                    attrs(Tag.RequestedProcedureID, VR.SH, "P-9934"),
                    queryParam);
            assertArrayEquals(new String[] { "9934.1", "9934.2" }, spsids(queryService));
        } finally {
            queryService.close();
        }
    }

    @Test
    public void testByScheduledProcedureID() throws Exception {
        MWLQueryService queryService = queryService();
        try {
            queryService.findScheduledProcedureSteps(
                    null,
                    sps(Tag.ScheduledProcedureStepID, VR.SH, "9934.2"),
                    queryParam);
            assertArrayEquals(new String[] { "9934.2" }, spsids(queryService));
        } finally {
            queryService.close();
        }
    }

    @Test
    public void testByStatus() throws Exception {
        MWLQueryService queryService = queryService();
        try {
            queryService.findScheduledProcedureSteps(
                    null,
                    sps(Tag.ScheduledProcedureStepStatus, VR.CS, 
                            ScheduledProcedureStep.ARRIVED, ScheduledProcedureStep.READY),
                    queryParam);
            assertArrayEquals(new String[] { "9934.1" }, spsids(queryService));
        } finally {
            queryService.close();
        }
    }

    @Test
    public void testByAET1() throws Exception {
        MWLQueryService queryService = queryService();
        try {
            queryService.findScheduledProcedureSteps(
                    null,
                    sps(Tag.ScheduledStationAETitle, VR.AE, "AET_MR1"),
                    queryParam);
            assertArrayEquals(new String[] { "9934.1" }, spsids(queryService));
        } finally {
            queryService.close();
        }
    }

    @Test
    public void testByAET2() throws Exception {
        MWLQueryService queryService = queryService();
        try {
            queryService.findScheduledProcedureSteps(
                    null,
                    sps(Tag.ScheduledStationAETitle, VR.AE, "AET_MR2"),
                    queryParam);
            assertArrayEquals(new String[] { "9934.1", "9934.2" }, spsids(queryService));
        } finally {
            queryService.close();
        }
    }

    @Test
    public void testByPerformingPhysican() throws Exception {
        MWLQueryService queryService = queryService();
        try {
            queryService.findScheduledProcedureSteps(
                    null,
                    sps(Tag.ScheduledPerformingPhysicianName, VR.PN,
                            "ScheduledPerformingPhysicianName3"),
                    queryParam);
            assertArrayEquals(new String[] { "9934.2" }, spsids(queryService));
        } finally {
            queryService.close();
        }
    }

    @Test
    public void testByStartDate() throws Exception {
        MWLQueryService queryService = queryService();
        try {
            queryService.findScheduledProcedureSteps(
                    null,
                    sps(Tag.ScheduledProcedureStepStartDate, VR.DA, "20111025"),
                    queryParam);
            assertArrayEquals(new String[] { "9934.1", "9934.2" }, spsids(queryService));
        } finally {
            queryService.close();
        }
    }

    @Test
    public void testByStartDateTime() throws Exception {
        MWLQueryService queryService = queryService();
        try {
            queryService.findScheduledProcedureSteps(
                    null,
                    spsStartDateTime("20111025", "1400-1500"),
                    queryParam);
            assertArrayEquals(new String[] { "9934.1" }, spsids(queryService));
        } finally {
            queryService.close();
        }
    }

    private Attributes attrs(int tag, VR vr, String value) {
        Attributes attrs = new Attributes(1);
        attrs.setString(tag, vr, value);
        return attrs;
    }

    private static Attributes sps(int tag, VR vr, String... values) {
        Attributes attrs = new Attributes(1);
        Attributes item = new Attributes(1);
        attrs.newSequence(Tag.ScheduledProcedureStepSequence, 1).add(item);
        item.setString(tag, vr, values);
        return attrs;
    }

    private static Attributes spsStartDateTime(String da, String tm) {
        Attributes attrs = new Attributes(1);
        Attributes item = new Attributes(2);
        attrs.newSequence(Tag.ScheduledProcedureStepSequence, 1).add(item);
        item.setString(Tag.ScheduledProcedureStepStartDate, VR.DA, da);
        item.setString(Tag.ScheduledProcedureStepStartTime, VR.TM, tm);
        return attrs;
    }

    private String[] spsids(MWLQueryService queryService) {
        TreeSet<String> spsids = new TreeSet<String>();
        while (queryService.hasMoreMatches())
            spsids.add(queryService.nextMatch()
                    .getNestedDataset(Tag.ScheduledProcedureStepSequence)
                    .getString(Tag.ScheduledProcedureStepID));
        return spsids.toArray(new String[spsids.size()]) ;
    }
}
