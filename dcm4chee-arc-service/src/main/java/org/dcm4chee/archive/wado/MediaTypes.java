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
    public final static String APPLICATION_DICOM = "application/dicom";

    /**
     * "application/dicom"
     */
    public final static MediaType APPLICATION_DICOM_TYPE =
            new MediaType("application", "dicom");

    /**
     * "application/dicom+xml"
     */
    public final static String APPLICATION_DICOM_XML = "application/dicom+xml";

    /**
     * "application/dicom+xml"
     */
    public final static MediaType APPLICATION_DICOM_XML_TYPE =
            new MediaType("application", "dicom+xml");

    /**
     * "application/octet-stream"
     */
    public final static String APPLICATION_OCTET_STREAM =
            "application/octet-stream";

    /**
     * "application/octet-stream"
     */
    public final static MediaType APPLICATION_OCTET_STREAM_TYPE =
            new MediaType("application", "octet-stream");

    /**
     * "image/jpeg"
     */
    public final static String IMAGE_JPEG = "image/jpeg";

    /**
     * "image/jpeg"
     */
    public final static MediaType IMAGE_JPEG_TYPE =
            new MediaType("image", "jpeg");

    /**
     * "image/dicom+jpeg"
     */
    public final static String IMAGE_DICOM_JPEG = "image/dicom+jpeg";

    /**
     * "image/dicom+jpeg"
     */
    public final static MediaType IMAGE_DICOM_JPEG_TYPE =
            new MediaType("image", "dicom+jpeg");

    /**
     * "image/dicom+jpeg-ls"
     */
    public final static String IMAGE_DICOM_JPEG_LS = "image/dicom+jpeg-ls";

    /**
     * "image/dicom+jpeg-ls"
     */
    public final static MediaType IMAGE_DICOM_JPEG_LS_TYPE =
            new MediaType("image", "dicom+jpeg-ls");

    /**
     * "image/dicom+jpeg-jp2"
     */
    public final static String IMAGE_DICOM_JPEG_JP2 = "image/dicom+jpeg-jp2";

    /**
     * "image/dicom+jpeg-jp2"
     */
    public final static MediaType IMAGE_DICOM_JPEG_JP2_TYPE =
            new MediaType("image", "dicom+jpeg-jp2");

    /**
     * "image/dicom+jpeg-jpx"
     */
    public final static String IMAGE_DICOM_JPEG_JPX = "image/dicom+jpeg-jpx";

    /**
     * "image/dicom+jpeg-jpx"
     */
    public final static MediaType IMAGE_DICOM_JPEG_JPX_TYPE =
            new MediaType("image", "dicom+jpeg-jpx");

    /**
     * "image/dicom+rle"
     */
    public final static String IMAGE_DICOM_RLE = "image/dicom+rle";

    /**
     * "image/dicom+rle"
     */
    public final static MediaType IMAGE_DICOM_RLE_TYPE =
            new MediaType("image", "dicom+rle");

    /**
     * "video/mpeg"
     */
    public final static String VIDEO_MPEG = "video/mpeg";

    /**
     * "video/mpeg"
     */
    public final static MediaType VIDEO_MPEG_TYPE =
            new MediaType("video", "mpeg");

    /**
     * "video/mp4"
     */
    public final static String VIDEO_MP4 = "video/mp4";

    /**
     * "video/mp4"
     */
    public final static MediaType VIDEO_MP4_TYPE =
            new MediaType("video", "mp4");

    /**
     * "application/pdf"
     */
    public final static String APPLICATION_PDF = "application/pdf";

    /**
     * "application/pdf"
     */
    public final static MediaType APPLICATION_PDF_TYPE =
            new MediaType("application", "pdf");

    /**
     * "multipart/related"
     */
    public final static String MULTIPART_RELATED = "multipart/related";

    /**
     * "multipart/related"
     */
    public final static MediaType MULTIPART_RELATED_TYPE =
            new MediaType("multipart", "related");

    public static MediaType bodyPartMediaType(MediaType mediaType) {
        if (mediaType.isWildcardType())
            return mediaType;

        return MediaType.valueOf(mediaType.getParameters().get("type"));
    }

    public static boolean equalsIgnoreParameters(MediaType a, MediaType b) {
       return a.getType().equalsIgnoreCase(b.getType())
           && a.getSubtype().equalsIgnoreCase(b.getSubtype());
    }

    public static boolean isApplicationOctetStream(MediaType mediaType) {
        return equalsIgnoreParameters(mediaType, APPLICATION_OCTET_STREAM_TYPE);
    }

    public static boolean isMultiframeMediaType(MediaType mediaType) {
        return mediaType.getType().equalsIgnoreCase("video")
                || mediaType.getSubtype().equalsIgnoreCase("dicom+jpeg-jpx");
    }

    public static List<MediaType> supportedMediaTypesOf(InstanceFileRef ref,
            Attributes attrs) {
        List<MediaType> list = new ArrayList<MediaType>(4);
        if (attrs.contains(Tag.BitsAllocated)) {
            if (attrs.getInt(Tag.NumberOfFrames, 1) > 1) {
                list.add(APPLICATION_DICOM_TYPE);
                MediaType mediaType;
                if (UID.MPEG2.equals(ref.transferSyntaxUID)
                        || UID.MPEG2MainProfileHighLevel
                        .equals(ref.transferSyntaxUID))
                    mediaType = VIDEO_MPEG_TYPE;
                else if (UID.MPEG4AVCH264HighProfileLevel41
                        .equals(ref.transferSyntaxUID)
                        || UID.MPEG4AVCH264BDCompatibleHighProfileLevel41
                        .equals(ref.transferSyntaxUID))
                    mediaType = VIDEO_MP4_TYPE;
                else
                    mediaType= IMAGE_JPEG_TYPE;
                list.add(mediaType);
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

    public static MediaType forTransferSyntax(String ts) {
        if (isLittleEndian(ts))
            return APPLICATION_OCTET_STREAM_TYPE;

        if (UID.JPEGLossless.equals(ts))
            return IMAGE_DICOM_JPEG_TYPE;

        if (UID.JPEGLSLossless.equals(ts))
            return IMAGE_DICOM_JPEG_LS_TYPE;

        if (UID.JPEG2000LosslessOnly.equals(ts))
            return IMAGE_DICOM_JPEG_JP2_TYPE;

        if (UID.JPEG2000Part2MultiComponentLosslessOnly.equals(ts))
            return IMAGE_DICOM_JPEG_JPX_TYPE;

        if (UID.RLELossless.equals(ts))
            return IMAGE_DICOM_RLE_TYPE;

        String s;
        if (UID.JPEGBaseline1.equals(ts)
                || UID.JPEGExtended24.equals(ts)
                || UID.JPEGLosslessNonHierarchical14.equals(ts))
            s = IMAGE_DICOM_JPEG;
        else if (UID.JPEGLSLossyNearLossless.equals(ts))
            s = IMAGE_DICOM_JPEG_LS;
        else if (UID.JPEG2000.equals(ts))
            s = IMAGE_DICOM_JPEG_JP2;
        else if (UID.JPEG2000Part2MultiComponent.equals(ts))
            s = IMAGE_DICOM_JPEG_JP2;
        else if (UID.MPEG2.equals(ts)
                || UID.MPEG2MainProfileHighLevel.equals(ts))
            s = VIDEO_MPEG;
        else if (UID.MPEG4AVCH264HighProfileLevel41.equals(ts)
                || UID.MPEG4AVCH264BDCompatibleHighProfileLevel41.equals(ts))
            s = VIDEO_MP4;
        else
            throw new IllegalArgumentException("ts: " + ts);

        return MediaType.valueOf(s + ";transfer-syntax=" + ts);
    }

    public static boolean isLittleEndian(String ts) {
        return UID.ExplicitVRLittleEndian.equals(ts)
                || UID.ImplicitVRLittleEndian.equals(ts);
    }

}
