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

    /** Photos are used by quotations, templates and template data, so any create/edit right on those is enough. */
    @PostMapping
    public Key upload(@AuthenticationPrincipal AuthUser me, @RequestParam("file") MultipartFile file) throws IOException {
        boolean ok = me.canAny(AppModule.QUOTATIONS, Right.CREATE, Right.EDIT) || me.canAny(AppModule.TEMPLATES, Right.CREATE, Right.EDIT)
                || me.canAny(AppModule.TEMPLATE_DATA, Right.CREATE, Right.EDIT);
        if (!ok) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to upload images");
        return new Key(storage.save(me.tenantId(), file));
    }

    @GetMapping
    public ResponseEntity<byte[]> get(@AuthenticationPrincipal AuthUser me, @RequestParam("key") String key) throws IOException {
        if (!key.startsWith(me.tenantId() + "/")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);   // tenants only see their own photos
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
                .body(storage.load(key));
    }
}
