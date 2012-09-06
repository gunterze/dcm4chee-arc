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

import javax.ejb.EJB;

import org.dcm4che.data.Attributes;
import org.dcm4che.io.SAXReader;
import org.dcm4chee.archive.common.StoreParam;
import org.dcm4chee.archive.dao.PatientService;
import org.dcm4chee.archive.dao.RequestService;
import org.dcm4chee.archive.entity.Availability;
import org.dcm4chee.archive.entity.Patient;
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
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
@RunWith(Arquillian.class)
public class InitTestData {

    private static final String SOURCE_AET = "SOURCE_AET";

    private static final String[] RETRIEVE_AETS = { "RETRIEVE_AET" };

    private static final String[] INSTANCES = {
        "testdata/date-range-1.xml",
        "testdata/date-range-2.xml",
        "testdata/date-range-3.xml",
        "testdata/date-range-4.xml",
        "testdata/date-range-5.xml",
        "testdata/date-range-6.xml",
        "testdata/date-range-7.xml",
        "testdata/accno-issuer-1.xml",
        "testdata/accno-issuer-2.xml",
        "testdata/accno-issuer-3.xml",
        "testdata/req-attrs-seq-1.xml",
        "testdata/req-attrs-seq-2.xml",
        "testdata/req-attrs-seq-3.xml",
        "testdata/mods-in-study-1.xml",
        "testdata/mods-in-study-2.xml",
        "testdata/mods-in-study-3.xml",
        "testdata/mods-in-study-4.xml",
        "testdata/mods-in-study-5.xml",
        "testdata/proc-code-seq-1.xml",
        "testdata/proc-code-seq-2.xml",
        "testdata/proc-code-seq-3.xml",
        "testdata/concept-name-code-seq-1.xml",
        "testdata/concept-name-code-seq-2.xml",
        "testdata/concept-name-code-seq-3.xml",
        "testdata/verifying-observer-seq-1.xml",
        "testdata/verifying-observer-seq-2.xml",
        "testdata/verifying-observer-seq-3.xml",
        "testdata/birthdate-1.xml",
        "testdata/birthdate-2.xml",
        "testdata/birthdate-3.xml",
        "testdata/tf-info-1.xml",
        "testdata/tf-info-2.xml",
        "testdata/fuzzy-1.xml",
        "testdata/fuzzy-2.xml",
        "testdata/fuzzy-3.xml",
        "testdata/fuzzy-4.xml",
        "testdata/fuzzy-5.xml",
        "testdata/person-name-1.xml",
   };

    private static final String[] MWL_ITEMS = {
        "testdata/mwl-ct-1.xml",
        "testdata/mwl-mr-1.xml",
        "testdata/mwl-mr-2.xml",
    };

    @EJB
    private PatientService patientService;

    @EJB
    private RequestService requestService;

    @Deployment
    public static WebArchive createDeployment() {
        WebArchive arc = Deployments.createWebArchive()
                .addClass(BeanLocator.class)
                .addClass(ParamFactory.class)
                .addPackage("org.dcm4chee.archive.common")
                .addPackage("org.dcm4chee.archive.dao")
                .addPackage("org.dcm4chee.archive.exception")
                .addPackage("org.dcm4chee.archive.mpps.dao")
                .addPackage("org.dcm4chee.archive.store.dao")
                .addPackage("org.dcm4chee.archive.util.query")
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
        for (String resourceName : INSTANCES)
            arc.addAsResource(resourceName);
        for (String resourceName : MWL_ITEMS)
            arc.addAsResource(resourceName);
        return arc;
    }

    private StoreService storeService() {
        return BeanLocator.lookup(StoreService.class,
                "java:/global/test/StoreService");
    }

    @Test
    public void initTestData() throws Exception {
        StoreParam storeParam = ParamFactory.createStoreParam();
        storeParam.setRetrieveAETs(RETRIEVE_AETS);
        StoreService storeService = storeService();
        storeService.setStoreParam(storeParam);
        for (String res : INSTANCES)
            storeService.newInstance(SOURCE_AET, load(res), new Attributes(),
                    Availability.ONLINE);
        Patient mwlPat = null;
        for (String res : MWL_ITEMS) {
            Attributes ds = load(res);
            if (mwlPat == null)
                mwlPat = patientService.findUniqueOrCreatePatient(
                        ds, storeParam, false, false);
            requestService.createScheduledProcedureStep(ds, mwlPat, storeParam);
        }
        storeService.close();
    }

    private Attributes load(String name) throws Exception {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return SAXReader.parse(cl.getResource(name).toString());
    }

}
