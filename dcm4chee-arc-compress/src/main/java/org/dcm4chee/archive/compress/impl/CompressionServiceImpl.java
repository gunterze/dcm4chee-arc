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
 * Portions created by the Initial Developer are Copyright (C) 2011-2013
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

package org.dcm4chee.archive.compress.impl;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;

import javax.enterprise.context.ApplicationScoped;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.data.VR;
import org.dcm4che.imageio.codec.CompressionRule;
import org.dcm4che.imageio.codec.Compressor;
import org.dcm4che.io.DicomOutputStream;
import org.dcm4chee.archive.compress.CompressionService;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
@ApplicationScoped
public class CompressionServiceImpl implements CompressionService {

    @Override
    public void compress(CompressionRule rule, File src, File dest,
            MessageDigest digest, Attributes fmi, Attributes attrs)
            throws IOException {
        try (  Compressor compressor = new Compressor(attrs,
                        fmi.getString(Tag.TransferSyntaxUID));
               DicomOutputStream out = dicomOutputStream(dest, digest)
            ) {

            String ts = rule.getTransferSyntax();
            compressor.compress(ts, rule.getImageWriteParams());
            fmi.setString(Tag.TransferSyntaxUID, VR.UI, ts);
            out.writeDataset(fmi, attrs);
        }
    }

    private DicomOutputStream dicomOutputStream(File dest, MessageDigest digest)
            throws IOException {
        dest.getParentFile().mkdirs();
        if (digest == null)
            return new DicomOutputStream(dest);
        else
            return new DicomOutputStream(
                    new BufferedOutputStream(
                        new DigestOutputStream(
                                new FileOutputStream(dest), digest)),
                    UID.ExplicitVRLittleEndian);
    }

}
