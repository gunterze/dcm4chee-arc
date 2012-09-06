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

import static org.dcm4chee.archive.entity.StudyPermissionAction.*;

import javax.ejb.EJB;

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
public class StudyPermissionServiceTest {

    private static final String UID = "1.2.40.0.13.1.1.99.20120906";

    @EJB
    private StudyPermissionService dao;

    @Deployment
    public static WebArchive createDeployment() {
        WebArchive arc = Deployments.createWebArchive()
                .addClass(StudyPermissionService.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
        return arc;
    }

    @Before
    public void clearDB(){
        dao.revokeStudyPermission(UID, "DCM4CHEE_TEST", QUERY);
        dao.revokeStudyPermission(UID, "DCM4CHEE_TEST", EXPORT);
    }

    @Test
    public void test() throws Exception {
        assertFalse(dao.hasStudyPermission(UID, "DCM4CHEE_TEST", QUERY));
        assertFalse(dao.hasStudyExportPermission(UID, "DCM4CHEE_TEST", "DCM4CHEE"));
        assertTrue(dao.grantStudyPermission(UID, "DCM4CHEE_TEST", QUERY));
        assertTrue(dao.grantStudyExportPermission(UID, "DCM4CHEE_TEST", "DCM4CHEE"));
        assertTrue(dao.hasStudyPermission(UID, "DCM4CHEE_TEST", QUERY));
        assertTrue(dao.hasStudyExportPermission(UID, "DCM4CHEE_TEST", "DCM4CHEE"));
        assertEquals(2, dao.findStudyPermissions(UID).size());
        assertFalse(dao.grantStudyPermission(UID, "DCM4CHEE_TEST", QUERY));
        assertFalse(dao.grantStudyExportPermission(UID, "DCM4CHEE_TEST", "DCM4CHEE"));
        assertTrue(dao.revokeStudyPermission(UID, "DCM4CHEE_TEST", QUERY));
        assertTrue(dao.revokeStudyExportPermission(UID, "DCM4CHEE_TEST", "DCM4CHEE"));
        assertFalse(dao.revokeStudyPermission(UID, "DCM4CHEE_TEST", QUERY));
        assertFalse(dao.revokeStudyExportPermission(UID, "DCM4CHEE_TEST", "DCM4CHEE"));
    }
}
