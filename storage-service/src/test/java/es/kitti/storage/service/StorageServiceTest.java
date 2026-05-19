package es.kitti.storage.service;

import es.kitti.mon.error.BadRequestError;
import es.kitti.mon.error.DomainError;
import es.kitti.storage.provider.StorageProvider;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    private static final byte[] JPEG_DATA = jpegData(100);
    private static final byte[] PNG_DATA  = pngData(100);

    @Mock StorageProvider storageProvider;
    @InjectMocks StorageService storageService;

    @Test
    void upload_validJpeg_returnsRight() {
        when(storageProvider.upload(anyString(), eq(JPEG_DATA), eq("image/jpeg")))
                .thenReturn(Uni.createFrom().item("some-key.jpg"));

        var result = storageService.upload(JPEG_DATA, "image/jpeg", "photo.jpg")
                .await().indefinitely();

        assertTrue(result.isRight());
        assertTrue(result.getOrElse(null).endsWith(".jpg"));
        verify(storageProvider).upload(anyString(), eq(JPEG_DATA), eq("image/jpeg"));
    }

    @Test
    void upload_validPng_returnsRight() {
        when(storageProvider.upload(anyString(), eq(PNG_DATA), eq("image/png")))
                .thenReturn(Uni.createFrom().item("some-key.png"));

        var result = storageService.upload(PNG_DATA, "image/png", "photo.png")
                .await().indefinitely();

        assertTrue(result.isRight());
        assertTrue(result.getOrElse(null).endsWith(".png"));
    }

    @Test
    void upload_invalidContentType_returnsLeft400() {
        var result = storageService.upload(new byte[100], "application/pdf", "document.pdf")
                .await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(BadRequestError.class, result.fold(e -> e, __ -> null));
        assertEquals(400, result.fold(DomainError::httpStatus, __ -> 0));
        verify(storageProvider, never()).upload(any(), any(), any());
    }

    @Test
    void upload_fileTooLarge_returnsLeft400() {
        var result = storageService.upload(new byte[6 * 1024 * 1024], "image/jpeg", "photo.jpg")
                .await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(400, result.fold(DomainError::httpStatus, __ -> 0));
        verify(storageProvider, never()).upload(any(), any(), any());
    }

    @Test
    void upload_exactMaxSize_returnsRight() {
        byte[] data = jpegData(5 * 1024 * 1024);
        when(storageProvider.upload(anyString(), eq(data), eq("image/jpeg")))
                .thenReturn(Uni.createFrom().item("some-key.jpg"));

        var result = storageService.upload(data, "image/jpeg", "photo.jpg")
                .await().indefinitely();

        assertTrue(result.isRight());
    }

    @Test
    void upload_wrongMagicBytesForDeclaredType_returnsLeft400() {
        var result = storageService.upload(PNG_DATA, "image/jpeg", "photo.jpg")
                .await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(400, result.fold(DomainError::httpStatus, __ -> 0));
        verify(storageProvider, never()).upload(any(), any(), any());
    }

    @Test
    void upload_allZeroes_returnsLeft400() {
        var result = storageService.upload(new byte[100], "image/jpeg", "malware.jpg")
                .await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(400, result.fold(DomainError::httpStatus, __ -> 0));
        verify(storageProvider, never()).upload(any(), any(), any());
    }

    @Test
    void upload_tooShortForMagicBytes_returnsLeft400() {
        byte[] tooShort = {(byte) 0xFF, (byte) 0xD8};

        var result = storageService.upload(tooShort, "image/jpeg", "photo.jpg")
                .await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(400, result.fold(DomainError::httpStatus, __ -> 0));
        verify(storageProvider, never()).upload(any(), any(), any());
    }

    @Test
    void upload_emptyFile_returnsLeft400() {
        var result = storageService.upload(new byte[0], "image/jpeg", "photo.jpg")
                .await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(400, result.fold(DomainError::httpStatus, __ -> 0));
        verify(storageProvider, never()).upload(any(), any(), any());
    }

    @Test
    void upload_jpegMagicDeclaredAsPng_returnsLeft400() {
        var result = storageService.upload(JPEG_DATA, "image/png", "photo.png")
                .await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(400, result.fold(DomainError::httpStatus, __ -> 0));
        verify(storageProvider, never()).upload(any(), any(), any());
    }

    @Test
    void upload_oneByteBelowMaxSize_returnsRight() {
        byte[] data = jpegData(5 * 1024 * 1024 - 1);
        when(storageProvider.upload(anyString(), eq(data), eq("image/jpeg")))
                .thenReturn(Uni.createFrom().item("some-key.jpg"));

        var result = storageService.upload(data, "image/jpeg", "photo.jpg")
                .await().indefinitely();

        assertTrue(result.isRight());
        verify(storageProvider).upload(anyString(), eq(data), eq("image/jpeg"));
    }

    @Test
    void upload_providerFailure_propagatesError() {
        byte[] data = jpegData(100);
        when(storageProvider.upload(anyString(), eq(data), eq("image/jpeg")))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("S3 unavailable")));

        assertThrows(RuntimeException.class,
                () -> storageService.upload(data, "image/jpeg", "photo.jpg")
                        .await().indefinitely());
    }

    @Test
    void delete_delegatesToProvider() {
        when(storageProvider.delete("some-key.jpg"))
                .thenReturn(Uni.createFrom().voidItem());

        storageService.delete("some-key.jpg").await().indefinitely();

        verify(storageProvider).delete("some-key.jpg");
    }

    // --- helpers ---

    private static byte[] jpegData(int size) {
        byte[] data = new byte[size];
        data[0] = (byte) 0xFF;
        data[1] = (byte) 0xD8;
        data[2] = (byte) 0xFF;
        return data;
    }

    private static byte[] pngData(int size) {
        byte[] magic = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] data = new byte[size];
        System.arraycopy(magic, 0, data, 0, magic.length);
        return data;
    }
}
