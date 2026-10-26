package com.saas.quote;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Stores item photos. STORAGE=s3 uses the AWS default credential chain (IAM role on ECS/EC2); local is for dev. */
@Service
public class StorageService {
    @Value("${app.storage}") private String mode;
    @Value("${app.local-dir}") private String dir;
    @Value("${app.s3.bucket}") private String bucket;
    private S3Client s3;

    @PostConstruct
    void init() throws IOException {
        if (isS3()) s3 = S3Client.create();
        else Files.createDirectories(Path.of(dir));
    }

    private boolean isS3() { return "s3".equals(mode); }

    /** Validates it is a real image, shrinks to max 800px wide and re-encodes as JPEG (keeps the PDF small with 60+ photos). */
    public String save(Long uid, MultipartFile f) throws IOException {
        BufferedImage src = ImageIO.read(f.getInputStream());
        if (src == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is not a valid image");
        int w = Math.min(800, src.getWidth());
        int h = Math.max(1, src.getHeight() * w / src.getWidth());
        BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ImageIO.write(rgb, "jpg", bo);

        String key = uid + "/" + UUID.randomUUID() + ".jpg";
        if (isS3()) {
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType("image/jpeg").build(),
                    RequestBody.fromBytes(bo.toByteArray()));
        } else {
            Path p = path(key);
            Files.createDirectories(p.getParent());
            Files.write(p, bo.toByteArray());
        }
        return key;
    }

    public byte[] load(String key) throws IOException {
        if (isS3()) return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
        return Files.readAllBytes(path(key));
    }

    /** Returns the key only if it belongs to this tenant, otherwise null. */
    public String own(Long uid, String key) {
        return (key == null || key.isBlank() || !key.startsWith(uid + "/")) ? null : key;
    }

    private Path path(String key) {
        Path base = Path.of(dir).toAbsolutePath().normalize();
        Path p = base.resolve(key).normalize();
        if (!p.startsWith(base)) throw new IllegalArgumentException("Bad key");   // blocks ../ tricks
        return p;
    }
}
