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

import java.io.File;
import java.io.Serializable;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToOne;
import javax.persistence.Table;

/**
 * @author Damien Evans <damien.daddy@gmail.com>
 * @author Justin Falk <jfalkmu@gmail.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
@Entity
@Table(name = "filesystem")
@NamedQueries({
@NamedQuery(
    name = "FileSystem.findByGroupID",
    query = "SELECT fs FROM FileSystem fs WHERE fs.groupID = ?1"),
@NamedQuery(
    name = "FileSystem.findByGroupIDAndStatus",
    query = "SELECT fs FROM FileSystem fs WHERE fs.groupID = ?1 AND fs.status = ?2"),
@NamedQuery(
    name = "FileSystem.getGroupIDs",
    query = "SELECT DISTINCT fs.groupID FROM FileSystem fs")
})
public class FileSystem implements Serializable {

    private static final long serialVersionUID = -5237294062957988389L;

    public static final String FIND_BY_GROUP_ID = "FileSystem.findByGroupID";
    public static final String FIND_BY_GROUP_ID_AND_STATUS = "FileSystem.findByGroupIDAndStatus";
    public static final String GET_GROUP_IDS = "FileSystem.getGroupIDs";

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Basic(optional = false)
    @Column(name = "fs_group_id")
    private String groupID;

    @Basic(optional = false)
    @Column(name = "fs_uri", unique = true)
    private String uri;

    @Basic(optional = false)
    @Column(name = "availability")
    private Availability availability;

    @Basic(optional = false)
    @Column(name = "fs_status")
    private FileSystemStatus status;

    @OneToOne(fetch=FetchType.LAZY)
    @JoinColumn(name = "next_fk")
    private FileSystem nextFileSystem;

    public long getPk() {
        return pk;
    }

    public String getURI() {
        return uri;
    }

    public void setURI(String uri) {
        this.uri = uri.endsWith("/")
                ? uri.substring(0, uri.length() - 1)
                : uri;
    }

    public String getGroupID() {
        return groupID;
    }

    public void setGroupID(String groupID) {
        this.groupID = groupID;
    }

    public Availability getAvailability() {
        return availability;
    }

    public void setAvailability(Availability availability) {
        this.availability = availability;
    }

    public FileSystemStatus getStatus() {
        return status;
    }

    public void setStatus(FileSystemStatus status) {
        this.status = status;
    }

    public FileSystem getNextFileSystem() {
        return nextFileSystem;
    }

    public void setNextFileSystem(FileSystem nextFileSystem) {
        this.nextFileSystem = nextFileSystem;
    }

    public File getDirectory() {
        try {
            return new File(new URI(uri));
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    public Path getPath() {
        try {
            return Paths.get(new URI(uri));
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    @Override
    public String toString() {
        return "FileSystem[pk=" + pk
                + ", group=" + groupID
                + ", uri=" + uri
                + ", avail=" + availability
                + ", status=" + status
                + "]";
    }
}
