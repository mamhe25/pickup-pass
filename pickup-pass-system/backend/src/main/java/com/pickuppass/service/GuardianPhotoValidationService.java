package com.pickuppass.service;

import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.FaceAnnotation;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.Likelihood;
import com.google.protobuf.ByteString;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Validates guardian verification photos before they can be stored as an
 * approved pickup identity image.
 *
 * This is intentionally face-presence and image-quality validation only.
 * PickupPass does not perform facial recognition or try to identify the
 * person in the photo.
 */
@Service
public class GuardianPhotoValidationService {

    public static final int MAX_UPLOAD_BYTES = 500_000;
    public static final int MIN_DIMENSION_PX = 300;
    public static final int MAX_DIMENSION_PX = 4096;
    private static final double MIN_FACE_AREA_RATIO = 0.15;
    private static final double MAX_FACE_AREA_RATIO = 0.88;
    private static final float MIN_DETECTION_CONFIDENCE = 0.70f;
    private static final float MAX_ABS_POSE_DEGREES = 30f;

    private volatile ImageAnnotatorClient visionClient;

    public ValidationResult validate(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return ValidationResult.rejected(
                    "Choose a clear photo before continuing.");
        }
        if (bytes.length > MAX_UPLOAD_BYTES) {
            return ValidationResult.rejected(
                    "Photo is too large. Choose an image under 500 KB.");
        }

        final ImageMetadata metadata;
        try {
            metadata = inspectImage(bytes);
        } catch (Exception e) {
            return ValidationResult.rejected(
                    "The selected file could not be read as an image.");
        }
        if (metadata == null) {
            return ValidationResult.rejected(
                    "The selected file is not a supported JPEG or PNG image.");
        }

        int width = metadata.width();
        int height = metadata.height();
        if (width < MIN_DIMENSION_PX || height < MIN_DIMENSION_PX) {
            return ValidationResult.rejected(
                    "Use a higher-resolution photo. Minimum size is 300 × 300 pixels.");
        }
        if (width > MAX_DIMENSION_PX || height > MAX_DIMENSION_PX) {
            return ValidationResult.rejected(
                    "Photo dimensions are too large. Use an image up to 4096 × 4096 pixels.");
        }

        try {
            Image image = Image.newBuilder()
                    .setContent(ByteString.copyFrom(bytes))
                    .build();

            Feature faceFeature = Feature.newBuilder()
                    .setType(Feature.Type.FACE_DETECTION)
                    .setMaxResults(3)
                    .build();

            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .setImage(image)
                    .addFeatures(faceFeature)
                    .build();

            BatchAnnotateImagesResponse response =
                    visionClient().batchAnnotateImages(List.of(request));
            if (response.getResponsesCount() != 1) {
                return ValidationResult.unavailable(
                        "Photo validation is temporarily unavailable. Please try again.");
            }

            AnnotateImageResponse item = response.getResponses(0);
            if (item.hasError()) {
                return ValidationResult.unavailable(
                        "Photo validation is temporarily unavailable. Please try again.");
            }

            List<FaceAnnotation> faces = item.getFaceAnnotationsList();
            if (faces.isEmpty()) {
                return ValidationResult.rejected(
                        "No clear human face was detected. Use a recent front-facing photo of yourself.");
            }
            if (faces.size() > 1) {
                return ValidationResult.rejected(
                        "More than one face was detected. Use a photo showing only you.");
            }

            FaceAnnotation face = faces.get(0);
            if (face.getDetectionConfidence() < MIN_DETECTION_CONFIDENCE) {
                return ValidationResult.rejected(
                        "Your face is not clear enough to verify. Try better lighting and keep the camera steady.");
            }

            if (isLikely(face.getBlurredLikelihood())) {
                return ValidationResult.rejected(
                        "The photo appears blurry. Use a sharper photo with your face in focus.");
            }

            if (isLikely(face.getUnderExposedLikelihood())) {
                return ValidationResult.rejected(
                        "The photo is too dark. Retake it in even, front-facing light.");
            }

            if (Math.abs(face.getPanAngle()) > MAX_ABS_POSE_DEGREES
                    || Math.abs(face.getTiltAngle()) > MAX_ABS_POSE_DEGREES
                    || Math.abs(face.getRollAngle()) > MAX_ABS_POSE_DEGREES) {
                return ValidationResult.rejected(
                        "Face the camera directly and keep your head upright.");
            }

            double faceAreaRatio = boundingAreaRatio(face, width, height);
            if (faceAreaRatio < MIN_FACE_AREA_RATIO) {
                return ValidationResult.rejected(
                        "Move closer to the camera so your face is clearly visible.");
            }
            if (faceAreaRatio > MAX_FACE_AREA_RATIO) {
                return ValidationResult.rejected(
                        "Move slightly farther from the camera so your full face and head are visible.");
            }

            return ValidationResult.accepted(
                    face.getDetectionConfidence(),
                    faceAreaRatio,
                    metadata.mediaType()
            );
        } catch (Exception e) {
            return ValidationResult.unavailable(
                    "Photo validation is temporarily unavailable. Please try again.");
        }
    }

    private ImageMetadata inspectImage(byte[] bytes) throws Exception {
        try (ImageInputStream stream =
                     ImageIO.createImageInputStream(
                             new ByteArrayInputStream(bytes))) {
            if (stream == null) return null;

            Iterator<ImageReader> readers =
                    ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) return null;

            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                String format =
                        reader.getFormatName()
                                .toLowerCase(Locale.ROOT);
                String mediaType =
                        switch (format) {
                            case "jpeg", "jpg" ->
                                    "image/jpeg";
                            case "png" ->
                                    "image/png";
                            default ->
                                    null;
                        };
                if (mediaType == null) return null;

                return new ImageMetadata(
                        reader.getWidth(0),
                        reader.getHeight(0),
                        mediaType
                );
            } finally {
                reader.dispose();
            }
        }
    }

    private ImageAnnotatorClient visionClient() throws Exception {
        ImageAnnotatorClient current = visionClient;
        if (current != null) return current;

        synchronized (this) {
            current = visionClient;
            if (current == null) {
                current = ImageAnnotatorClient.create();
                visionClient = current;
            }
            return current;
        }
    }

    @PreDestroy
    public void closeVisionClient() {
        ImageAnnotatorClient current = visionClient;
        if (current != null) {
            current.close();
            visionClient = null;
        }
    }

    private record ImageMetadata(
            int width,
            int height,
            String mediaType
    ) { }

    private boolean isLikely(Likelihood likelihood) {
        return likelihood == Likelihood.LIKELY
                || likelihood == Likelihood.VERY_LIKELY;
    }

    private double boundingAreaRatio(
            FaceAnnotation face,
            int imageWidth,
            int imageHeight) {
        if (!face.hasBoundingPoly()
                || face.getBoundingPoly().getVerticesCount() < 2) {
            return 0.0;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (var vertex : face.getBoundingPoly().getVerticesList()) {
            minX = Math.min(minX, vertex.getX());
            minY = Math.min(minY, vertex.getY());
            maxX = Math.max(maxX, vertex.getX());
            maxY = Math.max(maxY, vertex.getY());
        }

        long faceWidth = Math.max(0, maxX - minX);
        long faceHeight = Math.max(0, maxY - minY);
        long faceArea = faceWidth * faceHeight;
        long imageArea = (long) imageWidth * imageHeight;
        if (imageArea <= 0) return 0.0;

        return Math.min(1.0, Math.max(0.0, faceArea / (double) imageArea));
    }

    public record ValidationResult(
            boolean accepted,
            boolean validatorUnavailable,
            String message,
            float detectionConfidence,
            double faceAreaRatio,
            String mediaType) {

        public static ValidationResult accepted(
                float detectionConfidence,
                double faceAreaRatio,
                String mediaType) {
            return new ValidationResult(
                    true,
                    false,
                    "Verification photo accepted",
                    detectionConfidence,
                    faceAreaRatio,
                    mediaType
            );
        }

        public static ValidationResult rejected(String message) {
            return new ValidationResult(
                    false,
                    false,
                    message,
                    0f,
                    0.0,
                    null
            );
        }

        public static ValidationResult unavailable(String message) {
            return new ValidationResult(
                    false,
                    true,
                    message,
                    0f,
                    0.0,
                    null
            );
        }
    }
}
