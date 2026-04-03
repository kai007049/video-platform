package com.bilibili.video.utils;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * MinIO 文件上传工具
 */
@Component
public class MinioUtils {

    private final MinioClient minioClient;

    @Value("${minio.bucket-video}")
    private String bucketVideo;

    @Value("${minio.bucket-cover}")
    private String bucketCover;

    @Value("${minio.bucket-avatar}")
    private String bucketAvatar;

    @Value("${minio.bucket-message}")
    private String bucketMessage;

    @Value("${minio.endpoint}")
    private String endpoint;

    public MinioUtils(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    public String uploadVideo(MultipartFile file) throws Exception {
        return uploadFile(file, bucketVideo, "video");
    }

    public String uploadCover(MultipartFile file) throws Exception {
        return uploadCoverObject(file);
    }

    public String uploadAvatar(MultipartFile file) throws Exception {
        return uploadAvatarObject(file);
    }

    public String uploadDefaultAvatar(MultipartFile file) throws Exception {
        ensureBucketExists(bucketAvatar);
        String originalFilename = file.getOriginalFilename();
        String ext = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".png";
        String baseName = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(0, originalFilename.lastIndexOf("."))
                : UUID.randomUUID().toString();
        String objectName = "default/" + baseName + ext;

        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketAvatar)
                    .object(objectName)
                    .stream(is, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        }

        return objectName;
    }

    public String uploadMessageImage(MultipartFile file) throws Exception {
        ensureBucketExists(bucketMessage);
        String originalFilename = file.getOriginalFilename();
        String ext = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".png";
        String objectName = "message/" + UUID.randomUUID() + ext;

        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketMessage)
                    .object(objectName)
                    .stream(is, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        }

        return objectName;
    }

    /**
     * 上传封面文件，返回对象名（objectName），不返回可访问URL
     */
    public String uploadCoverFile(java.io.File file, String contentType) throws Exception {
        ensureBucketExists(bucketCover);
        String name = file.getName();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : ".jpg";
        String objectName = "cover/" + java.util.UUID.randomUUID() + ext;
        try (InputStream is = new java.io.FileInputStream(file)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketCover)
                    .object(objectName)
                    .stream(is, file.length(), -1)
                    .contentType(contentType)
                    .build());
        }
        return objectName;
    }

    private String uploadFile(MultipartFile file, String bucket, String prefix) throws Exception {
        ensureBucketExists(bucket);
        String originalFilename = file.getOriginalFilename();
        String ext = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
        String objectName = prefix + "/" + UUID.randomUUID() + ext;

        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(is, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        }

        return endpoint + "/" + bucket + "/" + objectName;
    }

    /**
     * 上传封面 MultipartFile，返回对象名（objectName）
     */
    private String uploadCoverObject(MultipartFile file) throws Exception {
        ensureBucketExists(bucketCover);
        String originalFilename = file.getOriginalFilename();
        String ext = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
        String objectName = "user/" + UUID.randomUUID() + ext;

        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketCover)
                    .object(objectName)
                    .stream(is, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        }

        return objectName;
    }

    /**
     * 上传头像 MultipartFile，返回对象名（objectName）
     */
    private String uploadAvatarObject(MultipartFile file) throws Exception {
        ensureBucketExists(bucketAvatar);
        String originalFilename = file.getOriginalFilename();
        String ext = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
        String objectName = "user/" + UUID.randomUUID() + ext;

        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketAvatar)
                    .object(objectName)
                    .stream(is, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        }

        return objectName;
    }

    /**
     * 从 MinIO 获取视频文件流，用于后端转发播放（解决私有桶浏览器无法直接访问的问题）
     */
    public InputStream getVideoStream(String videoUrl) throws Exception {
        String[] bucketAndObject = parseBucketAndObject(videoUrl);
        if (bucketAndObject == null) {
            throw new IllegalArgumentException("无效的视频URL: " + videoUrl);
        }
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketAndObject[0])
                .object(bucketAndObject[1])
                .build());
    }

    /** 从 MinIO 获取对象流（视频/封面通用） */
    public InputStream getObjectStream(String objectUrl) throws Exception {
        String[] bucketAndObject = parseBucketAndObject(objectUrl);
        if (bucketAndObject == null) {
            throw new IllegalArgumentException("无效的对象URL: " + objectUrl);
        }
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketAndObject[0])
                .object(bucketAndObject[1])
                .build());
    }

    /** 仅允许 cover 桶对象名，防止任意文件读取 */
    public InputStream getCoverStreamByObjectName(String objectName) throws Exception {
        String object = normalizeCoverObject(objectName);
        if (object == null || !isSafeCoverObject(object)) {
            throw new IllegalArgumentException("无效的对象名: " + objectName);
        }
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketCover)
                .object(object)
                .build());
    }

    /** 仅允许 avatar 桶对象名，防止任意文件读取 */
    public InputStream getAvatarStreamByObjectName(String objectName) throws Exception {
        String object = normalizeAvatarObject(objectName);
        if (object == null || !isSafeAvatarObject(object)) {
            throw new IllegalArgumentException("无效的对象名: " + objectName);
        }
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketAvatar)
                .object(object)
                .build());
    }

    /** 仅允许 message 桶对象名，防止任意文件读取 */
    public InputStream getMessageStreamByObjectName(String objectName) throws Exception {
        String object = normalizeMessageObject(objectName);
        if (object == null || !isSafeMessageObject(object)) {
            throw new IllegalArgumentException("无效的对象名: " + objectName);
        }
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketMessage)
                .object(object)
                .build());
    }

    /** 从对象URL解析 bucket 和 object，格式: http(s)://host:9000/bucket/object/path */
    private String[] parseBucketAndObject(String objectUrl) {
        if (objectUrl == null || objectUrl.isBlank()) {
            return null;
        }
        // 只解析完整 URL，避免把 "default/xxx" 这种对象名误判为 URL
        if (!(objectUrl.startsWith("http://") || objectUrl.startsWith("https://"))) {
            return null;
        }
        try {
            java.net.URI uri = java.net.URI.create(objectUrl);
            String path = uri.getPath();
            if (path == null) return null;
            path = path.replaceAll("^/+", "");
            int firstSlash = path.indexOf('/');
            if (firstSlash <= 0) return null;
            String bucket = path.substring(0, firstSlash);
            String object = path.substring(firstSlash + 1);
            return new String[]{bucket, object};
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 删除视频
     * @param videoUrl
     * @throws Exception
     */
    //TODO MQ异步删除
    public void deleteVideoByUrl(String videoUrl) throws Exception {
        String[] bucketAndObject = parseBucketAndObject(videoUrl);
        if (bucketAndObject == null) return;
        minioClient.removeObject(io.minio.RemoveObjectArgs.builder()
                .bucket(bucketAndObject[0])
                .object(bucketAndObject[1])
                .build());
    }

    /**
     * 删除封面
     * @param objectName
     * @throws Exception
     */
    //TODO MQ异步删除
    public void deleteCoverByObjectName(String objectName) throws Exception {
        String object = normalizeCoverObject(objectName);
        if (object == null) return;
        minioClient.removeObject(io.minio.RemoveObjectArgs.builder()
                .bucket(bucketCover)
                .object(object)
                .build());
    }

    /**
     * 兼容 coverUrl（完整URL）和 objectName 两种格式
     */
    private String normalizeCoverObject(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.startsWith("http://") || value.startsWith("https://")) {
            String[] bucketAndObject = parseBucketAndObject(value);
            if (bucketAndObject == null) return null;
            if (!bucketCover.equals(bucketAndObject[0])) return null;
            return bucketAndObject[1];
        }
        return value;
    }

    private boolean isSafeCoverObject(String objectName) {
        if (objectName == null || objectName.isBlank()) return false;
        if (objectName.contains("..") || objectName.contains("\\") || objectName.startsWith("/")) return false;
        // Support both prefixed object keys and legacy flat keys like "uuid.jpg".
        if (objectName.startsWith("default/") || objectName.startsWith("user/") || objectName.startsWith("cover/")) {
            if (objectName.substring(objectName.indexOf('/') + 1).isBlank()) return false;
            return true;
        }
        // Flat key must not contain slash and should include an extension.
        if (objectName.contains("/")) return false;
        int dot = objectName.lastIndexOf('.');
        if (dot <= 0 || dot == objectName.length() - 1) return false;
        return true;
    }

    private boolean isSafeAvatarObject(String objectName) {
        if (objectName == null || objectName.isBlank()) return false;
        if (objectName.contains("..") || objectName.contains("\\") || objectName.startsWith("/")) return false;
        if (objectName.startsWith("default/") || objectName.startsWith("user/")) {
            if (objectName.substring(objectName.indexOf('/') + 1).isBlank()) return false;
            return true;
        }
        if (objectName.contains("/")) return false;
        int dot = objectName.lastIndexOf('.');
        if (dot <= 0 || dot == objectName.length() - 1) return false;
        return true;
    }

    private boolean isSafeMessageObject(String objectName) {
        if (objectName == null || objectName.isBlank()) return false;
        if (!objectName.startsWith("message/")) return false;
        if (objectName.contains("..") || objectName.contains("\\") || objectName.startsWith("/")) return false;
        if (objectName.substring(objectName.indexOf('/') + 1).isBlank()) return false;
        return true;
    }

    /** 兼容 avatarUrl（完整URL）和 objectName 两种格式 */
    private String normalizeAvatarObject(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.startsWith("http://") || value.startsWith("https://")) {
            String[] bucketAndObject = parseBucketAndObject(value);
            if (bucketAndObject == null) return null;
            if (!bucketAvatar.equals(bucketAndObject[0])) return null;
            return bucketAndObject[1];
        }
        return value;
    }

    private String normalizeMessageObject(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.startsWith("http://") || value.startsWith("https://")) {
            String[] bucketAndObject = parseBucketAndObject(value);
            if (bucketAndObject == null) return null;
            if (!bucketMessage.equals(bucketAndObject[0])) return null;
            return bucketAndObject[1];
        }
        return value;
    }

    public InputStream getSafeCoverStreamLegacy(String objectName) throws Exception {
        String object = normalizeCoverObject(objectName);
        if (!isSafeCoverObject(object)) {
            throw new IllegalArgumentException("鏃犳晥鐨勫璞″悕: " + objectName);
        }
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketCover)
                .object(object)
                .build());
    }

    private boolean isSafeCoverObjectLegacy(String objectName) {
        if (objectName == null || objectName.isBlank()) return false;
        if (!(objectName.startsWith("user/") || objectName.startsWith("cover/"))) return false;
        if (objectName.contains("..") || objectName.contains("\\") || objectName.startsWith("/")) return false;
        if (objectName.substring(objectName.indexOf('/') + 1).isBlank()) return false;
        return true;
    }

    private void ensureBucketExists(String bucket) {
        try {
            if (!minioClient.bucketExists(io.minio.BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(io.minio.MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new RuntimeException("MinIO bucket 检查失败: " + e.getMessage());
        }
    }
}
