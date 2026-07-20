package com.pickuppass.service;

import com.google.cloud.firestore.Firestore;
import com.pickuppass.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Handles school logo uploads for the multi-tenant branding feature.
 *
 * IMPORTANT — this does NOT use Firebase/Cloud Storage. As of Feb 3, 2026,
 * Cloud Storage for Firebase requires the pay-as-you-go Blaze plan (a linked
 * billing account) even for entirely free-tier usage — a hard blocker for a
 * project that wants to stay on the free Spark plan. Since logos and avatars
 * are deliberately kept tiny (see resizeAndEncode below), we instead resize/
 * compress server-side and store the result as a base64 data URI directly
 * on the school's Firestore document. Firestore documents can hold up to
 * 1MiB, and MAX_ENCODED_BYTES below keeps real usage far under that with
 * margin to spare for the rest of the document's fields.
 *
 * The tradeoff: base64 inflates size by ~33% versus raw bytes, and every
 * read of the school doc now also pulls the logo bytes along with it
 * (rather than a separate, independently-cacheable Storage URL). For a
 * once-per-school logo shown in a handful of screens, this is a fine trade
 * for avoiding a billing requirement. If you outgrow this (e.g. want a CDN,
 * or images much larger than a small logo), swap back to real Storage once
 * you're on Blaze anyway, or use an external host — see README.
 */
@Service
public class SchoolLogoService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final int MAX_DIMENSION = 512;
    private static final long MAX_UPLOAD_BYTES = 2L * 1024 * 1024; // raw upload cap, before resizing
    private static final int MAX_ENCODED_BYTES = 700 * 1024;       // post-resize raw-byte cap, before base64 inflation

    private final Firestore firestore;

    public SchoolLogoService(Firestore firestore) {
        this.firestore = firestore;
    }

    public String uploadLogo(String schoolId, MultipartFile file) throws IOException, ExecutionException, InterruptedException {
        if (!firestore.collection("schools").document(schoolId).get().get().exists()) {
            throw new NotFoundException("School not found");
        }

        if (file.isEmpty()) {
            throw new IllegalArgumentException("No file uploaded");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("Logo file is too large (max 2MB before processing)");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Logo must be a PNG, JPEG, or WebP image");
        }

        ProcessedImage processed = resizeAndEncode(file.getBytes(), contentType);
        String dataUri = toDataUri(processed);

        firestore.collection("schools").document(schoolId)
                .update("logoUrl", dataUri, "logoUpdatedAt", com.google.cloud.firestore.FieldValue.serverTimestamp())
                .get();

        return dataUri;
    }

    /**
     * Resizes to fit within MAX_DIMENSION x MAX_DIMENSION (preserving aspect
     * ratio, never upscaling). PNGs are kept as PNG to preserve transparency
     * as long as the result stays under MAX_ENCODED_BYTES; if it doesn't (a
     * large/detailed PNG can still be big even at 512px), it's flattened
     * onto white and re-encoded as JPEG with quality stepped down until it
     * fits — the same iterative-quality pattern used for parent avatars,
     * just implemented server-side here instead of client-side.
     */
    private ProcessedImage resizeAndEncode(byte[] originalBytes, String originalContentType) throws IOException {
        BufferedImage original = ImageIO.read(new java.io.ByteArrayInputStream(originalBytes));
        if (original == null) {
            throw new IllegalArgumentException("Could not read image file — it may be corrupted or an unsupported format");
        }

        int width = original.getWidth();
        int height = original.getHeight();
        double scale = Math.min(1.0, (double) MAX_DIMENSION / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        boolean tryPng = originalContentType.equals("image/png");

        if (tryPng) {
            byte[] pngBytes = renderAndEncode(original, targetWidth, targetHeight, true, 1.0f);
            if (pngBytes.length <= MAX_ENCODED_BYTES) {
                return new ProcessedImage(pngBytes, "image/png");
            }
            // PNG came out too big even at 512px — fall through to JPEG.
        }

        float quality = 0.9f;
        byte[] jpegBytes;
        do {
            jpegBytes = renderAndEncode(original, targetWidth, targetHeight, false, quality);
            quality -= 0.1f;
        } while (jpegBytes.length > MAX_ENCODED_BYTES && quality >= 0.3f);

        if (jpegBytes.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "This image is too complex to compress under our size limit — try a simpler/smaller logo image");
        }

        return new ProcessedImage(jpegBytes, "image/jpeg");
    }

    private byte[] renderAndEncode(BufferedImage original, int targetWidth, int targetHeight, boolean asPng, float jpegQuality) throws IOException {
        int imageType = asPng ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, imageType);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        if (!asPng) {
            // Flatten onto white first — JPEG has no alpha channel, and an
            // unflattened transparent source would otherwise render black.
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, targetWidth, targetHeight);
        }
        g.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (asPng) {
            ImageIO.write(resized, "png", out);
        } else {
            writeJpegWithQuality(resized, out, jpegQuality);
        }
        return out.toByteArray();
    }

    private void writeJpegWithQuality(BufferedImage image, ByteArrayOutputStream out, float quality) throws IOException {
        var writers = ImageIO.getImageWritersByFormatName("jpg");
        var writer = writers.next();
        var params = writer.getDefaultWriteParam();
        params.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(quality);

        try (var ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
    }

    private String toDataUri(ProcessedImage img) {
        String base64 = Base64.getEncoder().encodeToString(img.bytes());
        return "data:" + img.contentType() + ";base64," + base64;
    }

    private record ProcessedImage(byte[] bytes, String contentType) {}
}
