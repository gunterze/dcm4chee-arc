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

package org.dcm4chee.archive.dao;

import static org.junit.Assert.*;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.UserTransaction;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.VR;
import org.dcm4chee.archive.common.StoreParam;
import org.dcm4chee.archive.entity.Patient;
import org.dcm4chee.archive.test.util.Deployments;
import org.dcm4chee.archive.test.util.ParamFactory;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
@RunWith(Arquillian.class)
public class PatientServiceTest {

    private static final String PID_1234 = "PATIENT_SERVICE_TEST-1234";
    private static final String PID_5678 = "PATIENT_SERVICE_TEST-5678";
    private static final String ISSUER_X = "DCM4CHEE_TESTDATA_X";
    private static final String ISSUER_Y = "DCM4CHEE_TESTDATA_Y";
    private static final String TEST_1234 = "Test PatientService 1234";
    private static final String TEST_1234_X = "Test PatientService 1234-X";
    private static final String TEST_1234_Y = "Test PatientService 1234-Y";
    private static final String TEST_5678_X = "Test PatientService 5678-X";

    private static final String NULL_MERGE_FK =
            "update Patient p set p.mergedWith=NULL " +
            "where p.patientID like 'PATIENT_SERVICE_TEST-%'";
    private static final String CLEAR_PATIENTS =
            "delete from Patient p " +
            "where p.patientID like 'PATIENT_SERVICE_TEST-%'";
    private static final String CLEAR_ISSUERS = 
            "delete from Issuer i " +
            "where i.localNamespaceEntityID like 'PATIENT_SERVICE_TEST-%'";

    private final StoreParam storeParam = ParamFactory.createStoreParam();

    @PersistenceContext
    EntityManager em;
    
    @Inject
    UserTransaction utx;

    @EJB
    private PatientService patientService;

    @Deployment
    public static WebArchive createDeployment() {
        WebArchive arc = Deployments.createWebArchive()
                .addClass(ParamFactory.class)
                .addPackage("org.dcm4chee.archive.common")
                .addPackage("org.dcm4chee.archive.dao")
                .addPackage("org.dcm4chee.archive.exception")
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
        return arc;
    }

    @Before
    public void clearData()  throws Exception {
        utx.begin();
        em.joinTransaction();
        System.out.println("Dumping old records...");
        em.createQuery(NULL_MERGE_FK).executeUpdate();
        em.createQuery(CLEAR_PATIENTS).executeUpdate();
        em.createQuery(CLEAR_ISSUERS).executeUpdate();
        utx.commit();
    }

    private Attributes attrs(String name, String pid, String issuer) {
        Attributes attrs = new Attributes();
        attrs.setString(Tag.PatientName, VR.PN, name);
        attrs.setString(Tag.PatientID, VR.LO, pid);
        if (issuer != null)
            attrs.setString(Tag.IssuerOfPatientID, VR.LO, issuer);
        return attrs;
    }

    @Test
    public void testFindUniqueOrCreatePatient() throws Exception {
        Patient pat1234 = patientService.findUniqueOrCreatePatient(
                attrs(TEST_1234, PID_1234, null), storeParam, true, true);
        Patient pat1234X = patientService.findUniqueOrCreatePatient(
                attrs(TEST_1234_X, PID_1234, ISSUER_X), storeParam, true, true);
        Patient pat1234Y = patientService.findUniqueOrCreatePatient(
                attrs(TEST_1234_Y, PID_1234, ISSUER_Y), storeParam, true, true);
        assertEquals(pat1234.getPk(), pat1234X.getPk());
        assertEquals(TEST_1234, 
                pat1234X.getAttributes().getString(Tag.PatientName));
        assertTrue(pat1234X.getPk() != pat1234Y.getPk());
    }

    @Test
    public void testUpdateOrCreatePatient() throws Exception {
        patientService.updateOrCreatePatient(
                attrs(TEST_1234, PID_1234, null), storeParam);
        Patient pat1234X = patientService.updateOrCreatePatient(
                attrs(TEST_1234_X, PID_1234, ISSUER_X), storeParam);
        Patient pat1234 = patientService.findUniqueOrCreatePatient(
                attrs(TEST_1234, PID_1234, null), storeParam, true, true);
        assertEquals(pat1234.getPk(), pat1234X.getPk());
        assertEquals(TEST_1234_X, 
                pat1234.getAttributes().getString(Tag.PatientName));
    }

    @Test
    public void testMergePatient() throws Exception {
        Attributes attrs1234Y = attrs(TEST_1234_Y, PID_1234, ISSUER_Y);
        Attributes attrs5678X = attrs(TEST_5678_X, PID_5678, ISSUER_X);
        patientService.mergePatient(attrs1234Y, attrs5678X, storeParam);
        Patient pat5678X = patientService.findUniqueOrCreatePatient(
                attrs5678X, storeParam, true, true);
        assertEquals(TEST_1234_Y, 
                pat5678X.getAttributes().getString(Tag.PatientName));
    }

}
