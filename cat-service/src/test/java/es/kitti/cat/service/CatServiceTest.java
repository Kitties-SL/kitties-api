package es.kitti.cat.service;

import es.kitti.mon.either.Either;
import es.kitti.mon.error.ConflictError;
import es.kitti.mon.error.DomainError;
import es.kitti.mon.error.ForbiddenError;
import es.kitti.mon.error.NotFoundError;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import es.kitti.cat.client.AdoptionClient;
import es.kitti.cat.client.StorageClient;
import es.kitti.cat.client.dto.StorageResponse;
import es.kitti.cat.dto.*;
import es.kitti.cat.entity.Cat;
import es.kitti.cat.entity.CatImage;
import es.kitti.cat.entity.CatStatus;
import es.kitti.cat.mapper.CatMapper;
import es.kitti.cat.repository.CatImageRepository;
import es.kitti.cat.repository.CatRepository;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatServiceTest {

    @Mock CatRepository catRepository;
    @Mock CatImageRepository catImageRepository;
    @Mock CatMapper catMapper;
    @Mock StorageClient storageClient;
    @Mock AdoptionClient adoptionClient;
    @Mock CatWriteService catWriteService;

    @InjectMocks
    CatService catService;

    private Cat testCat;
    private CatResponse testCatResponse;
    private CatSummaryResponse testCatSummaryResponse;
    private int size;
    private int page;

    @BeforeEach
    void setUp() {
        testCat = new Cat();
        testCat.id = 1L;
        testCat.organizationId = 10L;
        testCat.name = "Peluso";
        testCat.age = 2;
        testCat.city = "La Orotava";
        testCat.status = CatStatus.Available;

        testCatResponse = new CatResponse(
                1L, "Peluso", 2, "Male", null, true,
                "Available", "La Orotava", "Tenerife", "España",
                null, null, 10L, List.of(),
                LocalDateTime.now(), LocalDateTime.now()
        );

        testCatSummaryResponse = new CatSummaryResponse(
                1L, "Peluso", null, 2, "Male", true,
                "Available", "La Orotava", "Tenerife", "España",
                LocalDateTime.now()
        );
        size = 10;
        page = 1;
    }

    // --- createCat ---

    @Test
    void createCat_success() {
        var request = new CatCreateRequest(
                "Peluso", 2, "Male", null, true,
                "La Orotava", "Tenerife", "España", null, null
        );
        when(catMapper.toEntity(request)).thenReturn(testCat);
        when(catRepository.persist(any(Cat.class))).thenReturn(Uni.createFrom().item(testCat));
        when(catMapper.toResponse(testCat, List.of())).thenReturn(testCatResponse);

        var result = catService.createCat(request, 10L).await().indefinitely();

        assertNotNull(result);
        assertEquals("Peluso", result.name());
    }

    // --- findById ---

    @Test
    void findById_catExists_returnsRight() {
        when(catRepository.findById(1L)).thenReturn(Uni.createFrom().item(testCat));
        when(catImageRepository.findByCatId(1L)).thenReturn(Multi.createFrom().empty());
        when(catMapper.toResponse(eq(testCat), any())).thenReturn(testCatResponse);

        var result = catService.findById(1L).await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(1L, result.getOrElse(null).id());
    }

    @Test
    void findById_catNotFound_returnsLeft404() {
        when(catRepository.findById(999L)).thenReturn(Uni.createFrom().nullItem());

        var result = catService.findById(999L).await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
        assertInstanceOf(NotFoundError.class, ((Either.Left<?, ?>) result).value());
    }

    @Test
    void findById_deletedCat_returnsLeft404() {
        testCat.status = CatStatus.Deleted;
        when(catRepository.findById(1L)).thenReturn(Uni.createFrom().item(testCat));

        var result = catService.findById(1L).await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
    }

    // --- findMine ---

    @Test
    void findMine_returnsOwnedCats() {
        when(catRepository.findByOrganizationId(10L)).thenReturn(Uni.createFrom().item(List.of(testCat)));
        when(catMapper.toSummaryResponse(testCat)).thenReturn(testCatSummaryResponse);

        var result = catService.findMine(10L).await().indefinitely();

        assertEquals(1, result.size());
    }

    @Test
    void findMine_noOwnedCats_returnsEmptyList() {
        when(catRepository.findByOrganizationId(10L)).thenReturn(Uni.createFrom().item(List.of()));

        var result = catService.findMine(10L).await().indefinitely();

        assertTrue(result.isEmpty());
    }

    // --- search ---

    @Test
    void search_byCity_returnsSummaries() {
        when(catRepository.findByCity("La Orotava", page, size))
                .thenReturn(Uni.createFrom().item(List.of(testCat)));
        when(catRepository.countByCity("La Orotava"))
                .thenReturn(Uni.createFrom().item(1L));
        when(catMapper.toSummaryResponse(testCat)).thenReturn(testCatSummaryResponse);

        var result = catService.search("La Orotava", null, page, size).await().indefinitely();

        assertEquals(1, result.content().size());
        assertEquals("La Orotava", result.content().getFirst().city());
    }

    @Test
    void search_noFilters_returnsAllAvailable() {
        when(catRepository.findAvailable(page, size))
                .thenReturn(Uni.createFrom().item(List.of(testCat)));
        when(catRepository.countAvailable())
                .thenReturn(Uni.createFrom().item(1L));
        when(catMapper.toSummaryResponse(testCat)).thenReturn(testCatSummaryResponse);

        var result = catService.search(null, null, page, size).await().indefinitely();

        assertEquals(1, result.content().size());
        assertTrue(result.content().size() <= size);
    }

    @Test
    void search_byName_returnsSummaries() {
        when(catRepository.findByName("Peluso", page, size))
                .thenReturn(Uni.createFrom().item(List.of(testCat)));
        when(catRepository.countByName("Peluso"))
                .thenReturn(Uni.createFrom().item(1L));
        when(catMapper.toSummaryResponse(testCat)).thenReturn(testCatSummaryResponse);

        var result = catService.search(null, "Peluso", page, size).await().indefinitely();

        assertEquals(1, result.content().size());
        assertEquals("Peluso", result.content().getFirst().name());
    }

    @Test
    void search_byCityAndName_returnsSummaries() {
        when(catRepository.findByCityAndName("La Orotava", "Peluso", page, size))
                .thenReturn(Uni.createFrom().item(List.of(testCat)));
        when(catRepository.countByCityAndName("La Orotava", "Peluso"))
                .thenReturn(Uni.createFrom().item(1L));
        when(catMapper.toSummaryResponse(testCat)).thenReturn(testCatSummaryResponse);

        var result = catService.search("La Orotava", "Peluso", page, size).await().indefinitely();

        assertEquals(1, result.content().size());
    }

    @Test
    void search_sizeExceedsMax_capsAt100() {
        when(catRepository.findAvailable(page, 100))
                .thenReturn(Uni.createFrom().item(List.of()));
        when(catRepository.countAvailable())
                .thenReturn(Uni.createFrom().item(0L));

        catService.search(null, null, page, 200).await().indefinitely();

        verify(catRepository).findAvailable(page, 100);
    }

    @Test
    void search_emptyResults_returnsEmptyPage() {
        when(catRepository.findByCity("Desconocida", page, size))
                .thenReturn(Uni.createFrom().item(List.of()));
        when(catRepository.countByCity("Desconocida"))
                .thenReturn(Uni.createFrom().item(0L));

        var result = catService.search("Desconocida", null, page, size).await().indefinitely();

        assertTrue(result.content().isEmpty());
        assertEquals(0L, result.total());
    }

    // --- uploadImage ---

    @Test
    void uploadImage_success_callsStorageClientAndReturnsRight() {
        FileUpload file = mock(FileUpload.class);
        StorageResponse storageResponse = new StorageResponse("cats/img.jpg", "http://storage/cats/img.jpg");
        CatImage savedImage = new CatImage();
        savedImage.id = 5L;
        savedImage.catId = 1L;
        savedImage.key = "cats/img.jpg";

        when(catRepository.findById(1L)).thenReturn(Uni.createFrom().item(testCat));
        when(storageClient.upload(file)).thenReturn(Uni.createFrom().item(storageResponse));
        when(catRepository.persist(any(Cat.class))).thenReturn(Uni.createFrom().item(testCat));
        when(catImageRepository.persist(any(CatImage.class))).thenReturn(Uni.createFrom().item(savedImage));
        when(catImageRepository.findByCatId(1L)).thenReturn(Multi.createFrom().items(savedImage));
        when(catMapper.toResponse(eq(testCat), any())).thenReturn(testCatResponse);

        var result = catService.uploadImage(1L, file, 10L).await().indefinitely();

        assertTrue(result.isRight());
        verify(storageClient).upload(file);
    }

    @Test
    void uploadImage_catNotFound_returnsLeft404() {
        FileUpload file = mock(FileUpload.class);
        when(catRepository.findById(999L)).thenReturn(Uni.createFrom().nullItem());

        var result = catService.uploadImage(999L, file, 10L).await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
        verify(storageClient, never()).upload(any());
    }

    @Test
    void uploadImage_doesNotOverwriteExistingProfileImageUrl() {
        testCat.profileImageUrl = "http://existing/profile.jpg";
        FileUpload file = mock(FileUpload.class);
        StorageResponse storageResponse = new StorageResponse("cats/img2.jpg", "http://storage/cats/img2.jpg");
        CatImage savedImage = new CatImage();
        savedImage.catId = 1L;
        savedImage.key = "cats/img2.jpg";

        when(catRepository.findById(1L)).thenReturn(Uni.createFrom().item(testCat));
        when(storageClient.upload(file)).thenReturn(Uni.createFrom().item(storageResponse));
        when(catRepository.persist(any(Cat.class))).thenReturn(Uni.createFrom().item(testCat));
        when(catImageRepository.persist(any(CatImage.class))).thenReturn(Uni.createFrom().item(savedImage));
        when(catImageRepository.findByCatId(1L)).thenReturn(Multi.createFrom().items(savedImage));
        when(catMapper.toResponse(eq(testCat), any())).thenReturn(testCatResponse);

        catService.uploadImage(1L, file, 10L).await().indefinitely();

        assertEquals("http://existing/profile.jpg", testCat.profileImageUrl);
    }

    @Test
    void uploadImage_notOwner_returnsLeft403() {
        FileUpload file = mock(FileUpload.class);
        when(catRepository.findById(1L)).thenReturn(Uni.createFrom().item(testCat));

        var result = catService.uploadImage(1L, file, 99L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ForbiddenError.class, ((Either.Left<?, ?>) result).value());
        verify(storageClient, never()).upload(any());
    }

    // --- deleteImage ---

    @Test
    void deleteImage_success_callsStorageDelete() {
        CatImage catImage = new CatImage();
        catImage.id = 5L;
        catImage.catId = 1L;
        catImage.key = "cats/img.jpg";

        when(catRepository.findById(1L)).thenReturn(Uni.createFrom().item(testCat));
        when(catImageRepository.findById(5L)).thenReturn(Uni.createFrom().item(catImage));
        when(storageClient.delete("cats/img.jpg")).thenReturn(Uni.createFrom().voidItem());
        when(catImageRepository.delete(any(CatImage.class))).thenReturn(Uni.createFrom().voidItem());

        var result = catService.deleteImage(1L, 5L, 10L).await().indefinitely();

        assertTrue(result.isRight());
        verify(storageClient).delete("cats/img.jpg");
    }

    @Test
    void deleteImage_catNotFound_returnsLeft404() {
        when(catRepository.findById(999L)).thenReturn(Uni.createFrom().nullItem());

        var result = catService.deleteImage(999L, 5L, 10L).await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
        verify(storageClient, never()).delete(any());
    }

    @Test
    void deleteImage_notOwner_returnsLeft403() {
        when(catRepository.findById(1L)).thenReturn(Uni.createFrom().item(testCat));

        var result = catService.deleteImage(1L, 5L, 99L).await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(403, result.fold(DomainError::httpStatus, __ -> 0));
        verify(storageClient, never()).delete(any());
    }

    @Test
    void deleteImage_imageNotFound_returnsLeft404() {
        when(catRepository.findById(1L)).thenReturn(Uni.createFrom().item(testCat));
        when(catImageRepository.findById(999L)).thenReturn(Uni.createFrom().nullItem());

        var result = catService.deleteImage(1L, 999L, 10L).await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
        verify(storageClient, never()).delete(any());
    }

    // --- getInventoryStats ---

    @Test
    void getInventoryStats_countsCorrectly() {
        Cat available = new Cat();   available.status   = CatStatus.Available;
        Cat unavailable = new Cat(); unavailable.status = CatStatus.Unavailable;
        Cat deleted = new Cat();     deleted.status     = CatStatus.Deleted;
        when(catRepository.findAllByOrganizationId(10L))
                .thenReturn(Uni.createFrom().item(List.of(available, unavailable, deleted)));

        var result = catService.getInventoryStats(10L).await().indefinitely();

        assertEquals(1L, result.available());
        assertEquals(1L, result.unavailable());
        assertEquals(1L, result.deleted());
        assertEquals(3L, result.total());
    }

    @Test
    void getInventoryStats_emptyOrg_returnsAllZeros() {
        when(catRepository.findAllByOrganizationId(10L))
                .thenReturn(Uni.createFrom().item(List.of()));

        var result = catService.getInventoryStats(10L).await().indefinitely();

        assertEquals(0L, result.available());
        assertEquals(0L, result.total());
    }

    // --- updateCat ---

    @Test
    void updateCat_success_returnsRight() {
        var request = new CatUpdateRequest("Peluso Updated", 3, null, true,
                "Santa Cruz", "Tenerife", "España", null, null);
        when(catRepository.findById(1L)).thenReturn(Uni.createFrom().item(testCat));
        when(catRepository.persist(any(Cat.class))).thenReturn(Uni.createFrom().item(testCat));
        when(catImageRepository.findByCatId(1L)).thenReturn(Multi.createFrom().empty());
        when(catMapper.toResponse(eq(testCat), any())).thenReturn(testCatResponse);

        var result = catService.updateCat(1L, request, 10L).await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(1L, result.getOrElse(null).id());
    }

    @Test
    void updateCat_catNotFound_returnsLeft404() {
        var request = new CatUpdateRequest(null, null, null, null, null, null, null, null, null);
        when(catRepository.findById(999L)).thenReturn(Uni.createFrom().nullItem());

        var result = catService.updateCat(999L, request, 10L).await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
        assertInstanceOf(NotFoundError.class, result.fold(e -> e, __ -> null));
    }

    @Test
    void updateCat_notOwner_returnsLeft403() {
        when(catRepository.findById(1L)).thenReturn(Uni.createFrom().item(testCat));

        var request = new CatUpdateRequest("Peluso Updated", 3, null, true,
                "Santa Cruz", "Tenerife", "España", null, null);

        var result = catService.updateCat(1L, request, 99L).await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(403, result.fold(DomainError::httpStatus, __ -> 0));
    }

    // --- deleteCat ---

    @Test
    void deleteCat_catNotFound_returnsLeft404() {
        when(catRepository.findById(999L)).thenReturn(Uni.createFrom().nullItem());

        var result = catService.deleteCat(999L, 10L).await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
        assertInstanceOf(NotFoundError.class, result.fold(e -> e, __ -> null));
        verify(adoptionClient, never()).hasActiveRequestsForCat(any(), any());
    }

    @Test
    void deleteCat_notOwner_returnsLeft403() {
        when(catRepository.findById(1L)).thenReturn(Uni.createFrom().item(testCat));

        var result = catService.deleteCat(1L, 99L).await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(403, result.fold(DomainError::httpStatus, __ -> 0));
        assertInstanceOf(ForbiddenError.class, result.fold(e -> e, __ -> null));
        verify(adoptionClient, never()).hasActiveRequestsForCat(any(), any());
    }

    @Test
    void deleteCat_activeAdoptions_returnsLeft409() {
        when(catRepository.findById(1L)).thenReturn(Uni.createFrom().item(testCat));
        when(adoptionClient.hasActiveRequestsForCat(eq(1L), any()))
                .thenReturn(Uni.createFrom().item(true));

        var result = catService.deleteCat(1L, 10L).await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(409, result.fold(DomainError::httpStatus, __ -> 0));
        assertInstanceOf(ConflictError.class, result.fold(e -> e, __ -> null));
        verify(catWriteService, never()).markDeleted(any());
    }

    @Test
    void deleteCat_success_returnsRight() {
        when(catRepository.findById(1L)).thenReturn(Uni.createFrom().item(testCat));
        when(adoptionClient.hasActiveRequestsForCat(eq(1L), any()))
                .thenReturn(Uni.createFrom().item(false));
        when(catWriteService.markDeleted(1L)).thenReturn(Uni.createFrom().voidItem());

        var result = catService.deleteCat(1L, 10L).await().indefinitely();

        assertTrue(result.isRight());
        verify(catWriteService).markDeleted(1L);
    }
}
