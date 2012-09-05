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

package org.dcm4chee.archive.store.dao;

import static org.junit.Assert.*;

import java.util.Arrays;

import javax.ejb.EJB;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.io.SAXReader;
import org.dcm4chee.archive.common.IDWithIssuer;
import org.dcm4chee.archive.common.StoreParam;
import org.dcm4chee.archive.dao.PatientService;
import org.dcm4chee.archive.entity.Availability;
import org.dcm4chee.archive.entity.Instance;
import org.dcm4chee.archive.entity.PerformedProcedureStep;
import org.dcm4chee.archive.entity.Series;
import org.dcm4chee.archive.entity.Study;
import org.dcm4chee.archive.mpps.dao.MPPSService;
import org.dcm4chee.archive.mpps.dao.PPSWithIAN;
import org.dcm4chee.archive.test.util.Deployments;
import org.dcm4chee.archive.test.util.StoreParamFactory;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
@RunWith(Arquillian.class)
public class StoreServiceTest {

    private static final String MPPS_IUID = "1.2.40.0.13.1.1.99.20120130";

    @EJB
    private PatientService patientService;

    @EJB
    private StoreService storeService;

    @EJB
    private MPPSService mppsService;

    @Deployment
    public static WebArchive createDeployment() {
        WebArchive arc = Deployments.createWebArchive()
                .addClass(StoreParamFactory.class)
                .addPackage("org.dcm4chee.archive.common")
                .addPackage("org.dcm4chee.archive.dao")
                .addPackage("org.dcm4chee.archive.exception")
                .addPackage("org.dcm4chee.archive.mpps.dao")
                .addPackage("org.dcm4chee.archive.store.dao")
                .addPackage("org.dcm4chee.archive.util.query")
                .addAsResource("testdata/store-ct-1.xml")
                .addAsResource("testdata/store-ct-2.xml")
                .addAsResource("testdata/store-pr-1.xml")
                .addAsResource("testdata/mpps-create.xml")
                .addAsResource("testdata/mpps-set.xml")
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
        return arc;
    }

    private void clearData(IDWithIssuer pid) {
        patientService.deletePatient(pid);
    }

    @Test
    public void testNewInstance() throws Exception {
        Attributes mpps_create = load("testdata/mpps-create.xml");
        IDWithIssuer pid = IDWithIssuer.pidWithIssuer(mpps_create, null);
        String sourceAET = mpps_create.getString(Tag.PerformedStationAETitle);
        clearData(pid);
        StoreParam storeParam = StoreParamFactory.create();
        PerformedProcedureStep pps = mppsService.createPerformedProcedureStep(
                MPPS_IUID, mpps_create, storeParam);
        assertTrue(pps.isInProgress());
        PPSWithIAN ppsWithIAN = mppsService.updatePerformedProcedureStep(
                MPPS_IUID, load("testdata/mpps-set.xml"), storeParam);
        assertTrue(ppsWithIAN.pps.isCompleted());
        storeService.setStoreParam(storeParam);
        storeParam.setRetrieveAETs("AET_1","AET_2");
        storeParam.setExternalRetrieveAET("AET_3");
        Attributes modified1 = new Attributes();
        Instance ct1 = storeService.newInstance(sourceAET,
                load("testdata/store-ct-1.xml"), modified1, Availability.ONLINE);
        assertTrue(modified1.isEmpty());
        storeParam.setRetrieveAETs("AET_2");
        storeParam.setExternalRetrieveAET("AET_3");
        Attributes modified2 = new Attributes();
        Instance ct2 = storeService.newInstance(sourceAET,
                load("testdata/store-ct-2.xml"), modified2, Availability.NEARLINE);
        assertEquals(2, modified2.size());
        assertEquals("TEST-REPLACE", modified2.getString(Tag.StudyID));
        assertEquals("0", modified2.getString(Tag.SeriesNumber));
        storeParam.setRetrieveAETs("AET_1","AET_2");
        storeParam.setExternalRetrieveAET("AET_4");
        Attributes modified3 = new Attributes();
        Instance pr1 = storeService.newInstance(sourceAET,
                load("testdata/store-pr-1.xml"), modified3, Availability.ONLINE);
        assertEquals(1, modified3.size());
        assertEquals("TEST-REPLACE", modified3.getString(Tag.StudyID));
        Attributes ian = storeService.createIANforCurrentMPPS();
        assertNotNull(ian);

        Series ctSeries = ct1.getSeries();
        Series prSeries = pr1.getSeries();
        Study study = ctSeries.getStudy();
        assertEquals(ctSeries, ct2.getSeries());
        assertEquals(study, prSeries.getStudy());
        assertArrayEquals(new String[]{"CT", "PR" }, sort(study.getModalitiesInStudy()));
        assertArrayEquals(
                new String[]{
                        "1.2.840.10008.5.1.4.1.1.11.1",
                        "1.2.840.10008.5.1.4.1.1.2" },
                sort(study.getSOPClassesInStudy()));
        assertArrayEquals(new String[]{"AET_2"}, ctSeries.getRetrieveAETs());
        assertArrayEquals(new String[]{"AET_1", "AET_2"}, sort(prSeries.getRetrieveAETs()));
        assertArrayEquals(new String[]{"AET_2"}, study.getRetrieveAETs());
        assertEquals("AET_3", ctSeries.getExternalRetrieveAET());
        assertEquals("AET_4", prSeries.getExternalRetrieveAET());
        assertNull(study.getExternalRetrieveAET());
        assertEquals(Availability.NEARLINE, ctSeries.getAvailability());
        assertEquals(Availability.ONLINE, prSeries.getAvailability());
        assertEquals(Availability.NEARLINE, study.getAvailability());
    }

    private Attributes load(String name) throws Exception {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return SAXReader.parse(cl.getResource(name).toString());
    }

    private String[] sort(String[] a) {
        Arrays.sort(a);
        return a;
    }

}
