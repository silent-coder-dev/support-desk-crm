package com.support.crm.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);

    private final Cloudinary cloudinary;

    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/webp", "image/jpg"
    );

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB

    public String uploadAvatar(MultipartFile file , Long userId) throws IOException {

        if(file == null || file.isEmpty()){
            throw new IllegalArgumentException("Selected file is empty.");
        }
        if(file.getSize() > MAX_FILE_SIZE){
            throw new IllegalArgumentException("File Exceeds 5MB limit.");
        }
        String contentType = file.getContentType();
        if(contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())){
            throw new IllegalArgumentException("Only JPEG, PNG, and WebP images are allowed.");
        }

        Map<String , Object> params = ObjectUtils.asMap(
                "folder" , "support_crm/avatars",
                "public_id" , "user_avatar_" + userId ,
                "overwrite" , true ,
                "resource_type", "image"
        );

        log.info("Uploading avatar for user ID: {} to Cloudinary...", userId);

        Map<? , ?> uploadResult = cloudinary.uploader().upload(file.getBytes(),params);

        Object secureUrl = uploadResult.get("secure_url");
        if(secureUrl == null){
            throw new IOException("Failed to obtain secure URL from Cloudinary upload response");
        }

        return secureUrl.toString();
    }
}
