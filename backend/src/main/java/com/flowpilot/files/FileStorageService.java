package com.flowpilot.files;

import com.flowpilot.common.ApiException;
import com.flowpilot.files.FileValidator.Validated;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Metadata in PostgreSQL, bytes in Cloudinary. Reads always re-check ownership here, never trust a client-supplied key. */
@Service
public class FileStorageService {
    public record Stored(UUID id, String mime, String originalName, long size) {}
    public record Content(byte[] bytes, String mime, String originalName) {}

    private final JdbcTemplate jdbc;
    private final CloudinaryClient cloud;

    public FileStorageService(JdbcTemplate jdbc, CloudinaryClient cloud) { this.jdbc = jdbc; this.cloud = cloud; }

    /** Private document (receipt etc.): authenticated raw asset, random server-generated key. */
    @Transactional
    public Stored storePrivate(UUID ownerId, String purpose, Validated v) {
        String key = "flowpilot/" + purpose.toLowerCase() + "/" + UUID.randomUUID() + "." + v.kind().ext;
        cloud.upload(v.bytes(), v.safeName(), key, "raw", "authenticated");
        UUID id = jdbc.queryForObject("insert into stored_files (owner_user_id, purpose, object_key, original_name, mime_type, size_bytes, sha256) values (?, ?, ?, ?, ?, ?, ?) returning id",
                UUID.class, ownerId, purpose, key, v.safeName(), v.kind().mime, (long) v.bytes().length, v.sha256());
        return new Stored(id, v.kind().mime, v.safeName(), v.bytes().length);
    }

    /** Avatar: the only public-type asset. Returns its delivery URL. */
    public String storeAvatar(UUID ownerId, Validated v, String previousPublicId) {
        String key = "flowpilot/avatars/" + UUID.randomUUID();
        String url = cloud.upload(v.bytes(), v.safeName(), key, "image", "upload");
        jdbc.update("update user_profiles set avatar_url = ?, avatar_public_id = ?, updated_at = now() where user_id = ?", url, key, ownerId);
        if (previousPublicId != null) cloud.delete(previousPublicId, "image", "upload");
        return url;
    }

    public void removeAvatar(UUID ownerId) {
        List<String> old = jdbc.query("select avatar_public_id from user_profiles where user_id = ?", (rs, i) -> rs.getString(1), ownerId);
        jdbc.update("update user_profiles set avatar_url = null, avatar_public_id = null where user_id = ?", ownerId);
        if (!old.isEmpty() && old.get(0) != null) cloud.delete(old.get(0), "image", "upload");
    }

    public String currentAvatarKey(UUID ownerId) {
        List<String> keys = jdbc.query("select avatar_public_id from user_profiles where user_id = ?", (rs, i) -> rs.getString(1), ownerId);
        return keys.isEmpty() ? null : keys.get(0);   // NOT stream().findFirst(): that throws NPE when the column is NULL (no avatar yet)
    }

    public Content read(UUID fileId, UUID ownerId) {
        var rows = jdbc.query("select object_key, mime_type, original_name from stored_files where id = ? and owner_user_id = ? and deleted_at is null",
                (rs, i) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)}, fileId, ownerId);
        if (rows.isEmpty()) throw ApiException.notFound("File not found.");
        return new Content(cloud.downloadPrivate(rows.get(0)[0]), rows.get(0)[1], rows.get(0)[2]);
    }

    /** For callers that have ALREADY authorised access (e.g. a support ticket's owner or an admin). Never expose this directly. */
    public Content readAuthorised(UUID fileId) {
        var rows = jdbc.query("select object_key, mime_type, original_name from stored_files where id = ? and deleted_at is null", (rs, i) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)}, fileId);
        if (rows.isEmpty()) throw ApiException.notFound("File not found.");
        return new Content(cloud.downloadPrivate(rows.get(0)[0]), rows.get(0)[1], rows.get(0)[2]);
    }

    @Transactional
    public void delete(UUID fileId, UUID ownerId) {
        var rows = jdbc.query("select object_key from stored_files where id = ? and owner_user_id = ? and deleted_at is null", (rs, i) -> rs.getString(1), fileId, ownerId);
        if (rows.isEmpty()) return;
        jdbc.update("update stored_files set deleted_at = now() where id = ?", fileId);
        cloud.delete(rows.get(0), "raw", "authenticated");
    }
}