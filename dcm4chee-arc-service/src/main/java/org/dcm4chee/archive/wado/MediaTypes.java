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
package org.dcm4chee.archive.wado;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.MediaType;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4chee.archive.entity.InstanceFileRef;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public class MediaTypes {


    /**
     * "application/dicom"
     */
    public final static MediaType APPLICATION_DICOM_TYPE =
            new MediaType("application", "dicom");

    /**
     * "image/jpeg"
     */
    public final static MediaType IMAGE_JPEG_TYPE =
            new MediaType("image", "jpeg");

    /**
     * "video/mpeg"
     */
    public final static MediaType VIDEO_MPEG_TYPE =
            new MediaType("video", "mpeg");

    /**
     * "application/pdf"
     */
    public final static MediaType APPLICATION_PDF_TYPE =
            new MediaType("application", "pdf");

    public static boolean equalsIgnoreParams(MediaType type1, MediaType type2) {
        return type1.getType().equalsIgnoreCase(type2.getType())
            && type1.getSubtype().equalsIgnoreCase(type2.getSubtype());
    }

    public static boolean isDicomApplicationType(MediaType mediaType) {
        return equalsIgnoreParams(mediaType, APPLICATION_DICOM_TYPE);
    }

    public static List<MediaType> supportedMediaTypesOf(InstanceFileRef ref,
            Attributes attrs) {
        List<MediaType> list = new ArrayList<MediaType>(4);
        if (attrs.contains(Tag.BitsAllocated)) {
            if (attrs.getInt(Tag.NumberOfFrames, 1) > 1) {
                list.add(APPLICATION_DICOM_TYPE);
                boolean mpeg = (UID.MPEG2.equals(ref.transferSyntaxUID)
                        || UID.MPEG2MainProfileHighLevel
                            .equals(ref.transferSyntaxUID)
                        || UID.MPEG4AVCH264HighProfileLevel41
                            .equals(ref.transferSyntaxUID)
                        || UID.MPEG4AVCH264BDCompatibleHighProfileLevel41
                            .equals(ref.transferSyntaxUID));
                list.add(mpeg ? VIDEO_MPEG_TYPE : IMAGE_JPEG_TYPE);
            } else {
                list.add(IMAGE_JPEG_TYPE);
                list.add(APPLICATION_DICOM_TYPE);
            }
        } else if (attrs.contains(Tag.ContentSequence)) {
            list.add(MediaType.TEXT_HTML_TYPE);
            list.add(MediaType.TEXT_PLAIN_TYPE);
//            list.add(APPLICATION_PDF_TYPE);
            list.add(APPLICATION_DICOM_TYPE);
        } else {
            list.add(APPLICATION_DICOM_TYPE);
            if (UID.EncapsulatedPDFStorage.equals(ref.sopClassUID))
                list.add(APPLICATION_PDF_TYPE);
            else if (UID.EncapsulatedCDAStorage.equals(ref.sopClassUID))
                list.add(MediaType.TEXT_XML_TYPE);
        }
        return list ;
    }
}
