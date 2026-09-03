package luckydrop.demo.draw.reward.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
public class PrivateRewardImageService {

    private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final long MAX_SIZE = 5 * 1024 * 1024;

    private final Path privateRewardDirectory;

    public PrivateRewardImageService(@Value("${file.upload-dir}") String uploadDir) {
        Path publicUploadDirectory = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.privateRewardDirectory = publicUploadDirectory.resolveSibling("luckydrop-private-rewards");
    }

    public StoredImage store(MultipartFile image) {
        validate(image);
        String originalFilename = StringUtils.cleanPath(image.getOriginalFilename());
        String extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        String filename = UUID.randomUUID() + "." + extension;

        try {
            Files.createDirectories(privateRewardDirectory);
            Files.copy(image.getInputStream(), privateRewardDirectory.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            return new StoredImage(filename, image.getContentType());
        } catch (IOException exception) {
            throw new IllegalStateException("보상 이미지를 저장하지 못했습니다.", exception);
        }
    }

    public Resource load(String filename) {
        try {
            Resource resource = new UrlResource(privateRewardDirectory.resolve(filename).normalize().toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("보상 이미지를 찾을 수 없습니다.");
            }
            return resource;
        } catch (MalformedURLException exception) {
            throw new IllegalArgumentException("보상 이미지 경로가 올바르지 않습니다.", exception);
        }
    }

    private void validate(MultipartFile image) {
        if (image == null || image.isEmpty()) throw new IllegalArgumentException("보상 이미지를 첨부해주세요.");
        if (!ALLOWED_TYPES.contains(image.getContentType())) throw new IllegalArgumentException("jpg, png, gif, webp 이미지만 업로드 가능합니다.");
        if (image.getSize() > MAX_SIZE) throw new IllegalArgumentException("이미지 크기는 5MB 이하여야 합니다.");
        String filename = image.getOriginalFilename();
        if (filename == null || filename.isBlank() || filename.lastIndexOf('.') < 1) {
            throw new IllegalArgumentException("이미지 파일명이 올바르지 않습니다.");
        }
    }

    public record StoredImage(String filename, String contentType) {
    }
}
