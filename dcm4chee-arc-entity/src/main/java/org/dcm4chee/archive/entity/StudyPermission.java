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

package org.dcm4chee.archive.entity;

import java.io.Serializable;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

/**
 * @author Damien Evans <damien.daddy@gmail.com>
 * @author Justin Falk <jfalkmu@gmail.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
@NamedQueries({
    @NamedQuery(
        name="StudyPermission.findByStudyInstanceUID",
        query="SELECT p FROM StudyPermission p WHERE p.studyInstanceUID = ?1"),
    @NamedQuery(
        name="StudyPermission.checkPermission",
        query="SELECT p.pk FROM StudyPermission p "
            + "WHERE p.studyInstanceUID = ?1 AND p.role = ?2 AND p.action = ?3"),
    @NamedQuery(
        name="StudyPermission.checkExportPermission",
        query="SELECT p.pk FROM StudyPermission p "
            + "WHERE p.studyInstanceUID = ?1 AND p.role = ?2 AND p.action = org.dcm4chee.archive.entity.StudyPermissionAction.EXPORT "
            + "AND p.exportDestination = ?3"),
    @NamedQuery(
        name="StudyPermission.revokePermission",
        query="DELETE FROM StudyPermission p "
            + "WHERE p.studyInstanceUID = ?1 AND p.role = ?2 AND p.action = ?3"),
    @NamedQuery(
        name="StudyPermission.revokeExportPermission",
        query="DELETE FROM StudyPermission p "
            + "WHERE p.studyInstanceUID = ?1 AND p.role = ?2 AND p.action = org.dcm4chee.archive.entity.StudyPermissionAction.EXPORT "
            + "AND p.exportDestination = ?3")
})
@Entity
@Table(name = "study_permission")
public class StudyPermission implements Serializable {

    private static final long serialVersionUID = -8451966500593375437L;

    public static final String FIND_BY_STUDY_INSTANCE_UID = "StudyPermission.findByStudyInstanceUID";
    public static final String CHECK_PERMISSION = "StudyPermission.checkPermission";
    public static final String CHECK_EXPORT_PERMISSION = "StudyPermission.checkExportPermission";
    public static final String REVOKE_PERMISSION = "StudyPermission.revokePermission";
    public static final String REVOKE_EXPORT_PERMISSION = "StudyPermission.revokeExportPermission";

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Basic(optional = false)
    @Column(name = "study_iuid", updatable = false)
    private String studyInstanceUID;

    @Basic(optional = false)
    @Column(name = "roles", updatable = false)
    private String role;

    @Basic(optional = false)
    @Column(name = "action", updatable = false)
    private StudyPermissionAction action;

    @Column(name = "export_dest", updatable = false)
    private String exportDestination;


    public StudyPermission() {}

    public StudyPermission(String studyInstanceUID, String role, StudyPermissionAction action) {
        this.studyInstanceUID = studyInstanceUID;
        this.role = role;
        this.action = action;
    }

    public StudyPermission(String studyInstanceUID, String role, String exportDestination) {
        this(studyInstanceUID, role, StudyPermissionAction.EXPORT);
        this.exportDestination = exportDestination;
    }

    public long getPk() {
        return pk;
    }

    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    public String getRole() {
        return role;
    }

    public StudyPermissionAction getAction() {
        return action;
    }

    public String getExportDestination() {
        return exportDestination;
    }

    @Override
    public String toString() {
        return "StudyPermission[pk=" + pk
                + ", uid=" + studyInstanceUID
                + ", role=" + role
                + ", action=" + action
                + ", dest=" + exportDestination
                + "]";
    }

}
