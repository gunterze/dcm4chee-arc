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
import java.util.Date;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.Table;

/**
 * @author Damien Evans <damien.daddy@gmail.com>
 * @author Justin Falk <jfalkmu@gmail.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
@Entity
@Table(name = "file_ref")
public class FileRef implements Serializable {

    private static final long serialVersionUID = 1735835006678974580L;

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Basic(optional = false)
    @Column(name = "created_time", updatable = false)
    private Date createdTime;

    @Basic(optional = false)
    @Column(name = "filepath", updatable = false)
    private String filePath;

    @Basic(optional = false)
    @Column(name = "file_tsuid", updatable = false)
    private String transferSyntaxUID;

    @Basic(optional = false)
    @Column(name = "file_size", updatable = false)
    private long fileSize;

    @Basic(optional = true)
    @Column(name = "file_digest", updatable = false)
    private String digest;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name = "instance_fk", updatable = false)
    private Instance instance;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name = "filesystem_fk", updatable = false)
    private FileSystem fileSystem;

    public FileRef() {};

    public FileRef(FileSystem fileSystem, String filePath, String transferSyntaxUID,
            long fileSize, String digest) {
        this.fileSystem = fileSystem;
        this.filePath = filePath;
        this.transferSyntaxUID = transferSyntaxUID;
        this.fileSize = fileSize;
        this.digest = digest;
    }

    @PrePersist
    public void onPrePersist() {
        Date now = new Date();
        createdTime = now;
    }

    public long getPk() {
        return pk;
    }

    public Date getCreatedTime() {
        return createdTime;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getTransferSyntaxUID() {
        return transferSyntaxUID;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getDigest() {
        return digest;
    }

    public Instance getInstance() {
        return instance;
    }

    public void setInstance(Instance instance) {
        this.instance = instance;
    }

    public FileSystem getFileSystem() {
        return fileSystem;
    }

    @Override
    public String toString() {
        return "File[pk=" + pk
                + ", path=" + filePath
                + ", tsuid=" + transferSyntaxUID
                + ", size=" + fileSize
                + "]";
    }
}
