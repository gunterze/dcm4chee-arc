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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.data.VR;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.io.DicomOutputStream;
import org.dcm4che.soundex.FuzzyStr;
import org.dcm4che.util.StringUtils;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class Utils {

    public static byte[] encodeAttributes(Attributes attrs) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(512);
        try {
            @SuppressWarnings("resource")
            DicomOutputStream dos = new DicomOutputStream(out,
                    UID.ExplicitVRLittleEndian);
            dos.writeDataset(null, attrs);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return out.toByteArray();
    }

    public static Attributes decodeAttributes(byte[] b) {
        if (b == null || b.length == 0)
            return new Attributes(0);
        ByteArrayInputStream is = new ByteArrayInputStream(b);
        try {
            @SuppressWarnings("resource")
            DicomInputStream dis = new DicomInputStream(is);
            return dis.readDataset(-1, -1);
        } catch (IOException e) {
            throw new BlobCorruptedException(e);
        }
    }

    public static void decodeAttributes(Attributes attrs, byte[] b) {
        if (b == null || b.length == 0)
            return;
        ByteArrayInputStream is = new ByteArrayInputStream(b);
        try {
            @SuppressWarnings("resource")
            DicomInputStream dis = new DicomInputStream(is);
            dis.readFileMetaInformation();
            dis.readAttributes(attrs, -1, -1);
        } catch (IOException e) {
            throw new BlobCorruptedException(e);
        }
    }

    public static void setStudyQueryAttributes(Attributes attrs,
            int numberOfStudyRelatedSeries,
            int numberOfStudyRelatedInstances,
            String modalitiesInStudy,
            String sopClassesInStudy) {
        attrs.setInt(Tag.NumberOfStudyRelatedSeries, VR.IS,
                numberOfStudyRelatedSeries);
        attrs.setInt(Tag.NumberOfStudyRelatedInstances, VR.IS,
                numberOfStudyRelatedInstances);
        attrs.setString(Tag.ModalitiesInStudy, VR.CS,
                StringUtils.split(modalitiesInStudy, '\\'));
        attrs.setString(Tag.SOPClassesInStudy, VR.CS,
                StringUtils.split(sopClassesInStudy, '\\'));
    }

    public static void setSeriesQueryAttributes(Attributes attrs,
            int numberOfSeriesRelatedInstances) {
        attrs.setInt(Tag.NumberOfSeriesRelatedInstances, VR.IS,
                numberOfSeriesRelatedInstances);
    }

    public static void setRetrieveAET(Attributes attrs, String retrieveAETs,
            String externalRetrieveAET) {
        if (retrieveAETs != null)
            if (externalRetrieveAET != null) {
                String[] ss = StringUtils.split(retrieveAETs, '\\');
                String[] ss2 = new String[ss.length + 1];
                ss2[ss.length] = externalRetrieveAET;
                attrs.setString(Tag.RetrieveAETitle, VR.AE, ss2);
            } else
                attrs.setString(Tag.RetrieveAETitle, VR.AE,
                        StringUtils.split(retrieveAETs, '\\'));
        else
            if (externalRetrieveAET != null)
                attrs.setString(Tag.RetrieveAETitle, VR.AE,
                        externalRetrieveAET);
    }

    public static void setAvailability(Attributes attrs, Availability availability) {
        attrs.setString(Tag.InstanceAvailability, VR.CS, availability.toCodeString());
    }

    public static String toFuzzy(FuzzyStr fuzzyStr, String s) {
        String fuzzy = fuzzyStr.toFuzzy(s);
        return fuzzy.length() == 0 ? "*" : fuzzy;
    }

    public static String[] intersection(String[] ss1, String[] ss2) {
        int l = 0;
        for (int i = 0; i < ss1.length; i++)
            if (contains(ss2, ss1[i]))
                ss1[l++] = ss1[i];
        return l == ss1.length ? ss1 : Arrays.copyOf(ss1, l);
    }

    public static boolean contains(String[] ss, String s0) {
        for (String s : ss)
            if (s0.equals(s))
                return true;
        return false;
    }
}
