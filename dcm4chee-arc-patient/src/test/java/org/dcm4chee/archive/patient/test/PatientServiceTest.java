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

package org.dcm4chee.archive.patient.test;

import java.io.File;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.UserTransaction;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.VR;
import org.dcm4che.soundex.ESoundex;
import org.dcm4chee.archive.conf.AttributeFilter;
import org.dcm4chee.archive.entity.Patient;
import org.dcm4chee.archive.patient.PatientService;
import org.dcm4chee.archive.patient.impl.PatientServiceBean;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.PomEquippedResolveStage;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Umberto Cappellini <umberto.cappellini@agfa.com>
 *
 */
@RunWith(Arquillian.class)
public class PatientServiceTest {

    private static final String DCM4CHEE_ARC_SPI =
            "org.dcm4che.dcm4chee-arc:dcm4chee-arc-spi";
    private static final String DCM4CHEE_ARC_ISSUER =
            "org.dcm4che.dcm4chee-arc:dcm4chee-arc-issuer";
    
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

    @PersistenceContext
    EntityManager em;
    
    @Resource
    UserTransaction utx;

    @EJB
    private PatientService patientService;

    @Deployment
    public static EnterpriseArchive createDeployment() {
        
        PomEquippedResolveStage resolver = Maven.resolver().loadPomFromFile("pom.xml");

        EnterpriseArchive ear= ShrinkWrap.create(EnterpriseArchive.class, "test.ear");
        ear.addAsLibraries(resolver.resolve(DCM4CHEE_ARC_SPI).withTransitivity().as(File.class));
        ear.addAsModules(resolver.resolve(DCM4CHEE_ARC_ISSUER).withoutTransitivity().as(File.class));
        
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class);
        jar.addClass(PatientServiceBean.class);
        jar.addClass(ParamFactory.class);
        jar.addClass(PatientServiceTest.class);
        jar.addAsManifestResource("MANIFEST.MF");
        
        ear.addAsModule(jar);
        
        System.out.println(ear.toString(true));
        
        return ear;
    }

    @Before
    public void clearData() throws Exception {
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
                new AttributeFilter(ParamFactory.PATIENT_ATTRS),
                new ESoundex(), attrs(TEST_1234, PID_1234, null), true, true);
        System.out.println(pat1234);
        Patient pat1234X = patientService.findUniqueOrCreatePatient(
                new AttributeFilter(ParamFactory.PATIENT_ATTRS),
                new ESoundex(), attrs(TEST_1234_X, PID_1234, ISSUER_X), true,
                true);
        Patient pat1234Y = patientService.findUniqueOrCreatePatient(
                new AttributeFilter(ParamFactory.PATIENT_ATTRS),
                new ESoundex(), attrs(TEST_1234_Y, PID_1234, ISSUER_Y), true,
                true);
        Assert.assertEquals(pat1234.getPk(), pat1234X.getPk());
        Assert.assertEquals(TEST_1234,
                pat1234X.getAttributes().getString(Tag.PatientName));
        Assert.assertNotSame(pat1234X.getPk(),pat1234Y.getPk());
    }
}


