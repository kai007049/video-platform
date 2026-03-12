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

    /**
     * 上传封面文件，返回对象名（objectName），不返回可访问URL
     */
    public String uploadCoverFile(java.io.File file, String contentType) throws Exception {
        ensureBucketExists(bucketCover);
        String name = file.getName();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : ".jpg";
        String objectName = java.util.UUID.randomUUID() + ext;
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
        String objectName = UUID.randomUUID() + ext;

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
        if (objectName == null || !objectName.matches("^[a-zA-Z0-9._-]+$")) {
            throw new IllegalArgumentException("无效的对象名: " + objectName);
        }
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketCover)
                .object(objectName)
                .build());
    }

    /** 从对象URL解析 bucket 和 object，格式: http(s)://host:9000/bucket/object/path */
    private String[] parseBucketAndObject(String objectUrl) {
        if (objectUrl == null || objectUrl.isBlank()) {
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
        if (objectName == null || objectName.isBlank()) return;
        minioClient.removeObject(io.minio.RemoveObjectArgs.builder()
                .bucket(bucketCover)
                .object(objectName)
                .build());
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
