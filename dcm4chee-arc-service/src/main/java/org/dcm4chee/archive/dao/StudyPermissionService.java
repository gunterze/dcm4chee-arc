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

package org.dcm4chee.archive.dao;

import java.util.List;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.dcm4chee.archive.entity.StudyPermission;
import org.dcm4chee.archive.entity.StudyPermissionAction;


/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
@Stateless
public class StudyPermissionService {

    @PersistenceContext
    private EntityManager em;

    public List<StudyPermission> findStudyPermissions(String studyInstanceUID) {
        return em.createNamedQuery(
                    StudyPermission.FIND_BY_STUDY_INSTANCE_UID,
                    StudyPermission.class)
                .setParameter(1, studyInstanceUID)
                .getResultList();
    }

    public boolean hasStudyPermission(String studyInstanceUID,
            String role, StudyPermissionAction action) {
        return !em.createNamedQuery(StudyPermission.CHECK_PERMISSION)
                .setParameter(1, studyInstanceUID)
                .setParameter(2, role)
                .setParameter(3, action)
                .getResultList().isEmpty();
    }

    public boolean hasStudyExportPermission(String studyInstanceUID, 
            String role, String dest) {
        return !em.createNamedQuery(StudyPermission.CHECK_EXPORT_PERMISSION)
                .setParameter(1, studyInstanceUID)
                .setParameter(2, role)
                .setParameter(3, dest)
                .getResultList().isEmpty();
    }

    public boolean grantStudyPermission(String studyInstanceUID,
            String role, StudyPermissionAction action) {
        if (hasStudyPermission(studyInstanceUID, role, action))
            return false;

        em.persist(new StudyPermission(studyInstanceUID, role, action));
        return true;
    }

    public boolean grantStudyExportPermission(String studyInstanceUID,
            String role, String dest) {
        if (hasStudyExportPermission(studyInstanceUID, role, dest))
            return false;

        em.persist(new StudyPermission(studyInstanceUID, role, dest));
        return true;
    }

    public boolean revokeStudyPermission(String studyInstanceUID, String role,
            StudyPermissionAction action) {
        return em.createNamedQuery(StudyPermission.REVOKE_PERMISSION)
                .setParameter(1, studyInstanceUID)
                .setParameter(2, role)
                .setParameter(3, action)
                .executeUpdate() > 0;
    }

    public boolean revokeStudyExportPermission(String studyInstanceUID,
            String role, String dest) {
        return em.createNamedQuery(StudyPermission.REVOKE_EXPORT_PERMISSION)
                .setParameter(1, studyInstanceUID)
                .setParameter(2, role)
                .setParameter(3, dest)
                .executeUpdate() > 0;
    }

}
