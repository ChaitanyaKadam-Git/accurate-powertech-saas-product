package com.saas.quote;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@RestController @RequestMapping("/api/files") @RequiredArgsConstructor
public class FileController {
    private final StorageService storage;

    record Key(String key) {}

    @PostMapping
    public Key upload(@AuthenticationPrincipal Long uid, @RequestParam("file") MultipartFile file) throws IOException {
        return new Key(storage.save(uid, file));
    }

    @GetMapping
    public ResponseEntity<byte[]> get(@AuthenticationPrincipal Long uid, @RequestParam("key") String key) throws IOException {
        if (!key.startsWith(uid + "/")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);   // tenants only see their own photos
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
                .body(storage.load(key));
    }
}
