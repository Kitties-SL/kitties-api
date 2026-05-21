package es.kitti.cat.service;

import es.kitti.cat.client.AdoptionClient;
import es.kitti.cat.client.StorageClient;
import es.kitti.cat.dto.*;
import es.kitti.cat.entity.Cat;
import es.kitti.cat.entity.CatImage;
import es.kitti.cat.entity.CatStatus;
import es.kitti.cat.mapper.CatMapper;
import es.kitti.cat.repository.CatImageRepository;
import es.kitti.cat.repository.CatRepository;
import es.kitti.mon.either.Either;
import es.kitti.mon.either.Unit;
import es.kitti.mon.error.ConflictError;
import es.kitti.mon.error.DomainError;
import es.kitti.mon.error.ForbiddenError;
import es.kitti.mon.error.NotFoundError;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class CatService {

    @Inject
    CatRepository catRepository;

    @Inject
    CatImageRepository catImageRepository;

    @Inject
    CatMapper catMapper;

    @Inject
    CatWriteService catWriteService;

    @RestClient
    StorageClient storageClient;

    @RestClient
    AdoptionClient adoptionClient;

    @ConfigProperty(name = "kitties.internal.secret")
    String internalSecret;

    @WithTransaction
    public Uni<CatResponse> createCat(CatCreateRequest request, Long callerId) {
        Cat cat = catMapper.toEntity(request);
        cat.organizationId = callerId;
        return catRepository.persist(cat)
                .onItem().transform(saved -> catMapper.toResponse(saved, List.of()));
    }

    @WithTransaction
    public Uni<CatResponse> createForOrganization(CatCreateInternalRequest request) {
        Cat cat = catMapper.toEntity(request);
        return catRepository.persist(cat)
                .onItem().transform(saved -> catMapper.toResponse(saved, List.of()));
    }

    @WithSession
    public Uni<List<OrgCatCountResponse>> countActiveByOrgIds(List<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty())
            return Uni.createFrom().item(List.of());
        return catRepository.findAvailableByOrgIds(orgIds)
                .onItem().transform(cats -> {
                    Map<Long, Long> counts = cats.stream()
                            .collect(Collectors.groupingBy(c -> c.organizationId, Collectors.counting()));
                    return orgIds.stream()
                            .map(id -> new OrgCatCountResponse(id, counts.getOrDefault(id, 0L)))
                            .toList();
                });
    }

    @WithTransaction
    public Uni<Either<DomainError, CatResponse>> updateCat(Long id, CatUpdateRequest request, Long organizationId) {
        return catRepository.findById(id)
                .onItem().transformToUni(cat -> {
                    if (cat == null)
                        return Uni.createFrom().item(Either.left(new NotFoundError("CAT_NOT_FOUND")));
                    if (!cat.organizationId.equals(organizationId))
                        return Uni.createFrom().item(Either.left(new ForbiddenError("CAT_ACCESS_DENIED")));
                    catMapper.updateEntity(cat, request);
                    return catRepository.persist(cat)
                            .onItem().transformToUni(saved ->
                                    catImageRepository.findByCatId(saved.id)
                                            .collect().asList()
                                            .onItem().transform(images ->
                                                    Either.<DomainError, CatResponse>right(catMapper.toResponse(saved, images))
                                            )
                            );
                });
    }

    @WithSession
    public Uni<Either<DomainError, CatResponse>> findById(Long id) {
        return catRepository.findById(id)
                .onItem().transformToUni(cat -> {
                    if (cat == null || cat.status == CatStatus.Deleted)
                        return Uni.createFrom().item(Either.left(new NotFoundError("CAT_NOT_FOUND")));
                    return catImageRepository.findByCatId(cat.id)
                            .collect().asList()
                            .onItem().transform(images ->
                                    Either.<DomainError, CatResponse>right(catMapper.toResponse(cat, images))
                            );
                });
    }

    public Uni<PageResponse<CatSummaryResponse>> search(String city, String name, int page, int size) {
        if (size > 100) size = 100;
        final int effectiveSize = size;

        if (city != null && name != null) {
            return catRepository.findByCityAndName(city, name, page, effectiveSize)
                    .onItem().transformToUni(cats ->
                            catRepository.countByCityAndName(city, name)
                                    .onItem().transform(count -> pageOf(cats, page, effectiveSize, count)));
        } else if (city != null) {
            return catRepository.findByCity(city, page, effectiveSize)
                    .onItem().transformToUni(cats ->
                            catRepository.countByCity(city)
                                    .onItem().transform(count -> pageOf(cats, page, effectiveSize, count)));
        } else if (name != null) {
            return catRepository.findByName(name, page, effectiveSize)
                    .onItem().transformToUni(cats ->
                            catRepository.countByName(name)
                                    .onItem().transform(count -> pageOf(cats, page, effectiveSize, count)));
        } else {
            return catRepository.findAvailable(page, effectiveSize)
                    .onItem().transformToUni(cats ->
                            catRepository.countAvailable()
                                    .onItem().transform(count -> pageOf(cats, page, effectiveSize, count)));
        }
    }

    private PageResponse<CatSummaryResponse> pageOf(List<Cat> cats, int page, int size, long count) {
        List<CatSummaryResponse> content = cats.stream().map(catMapper::toSummaryResponse).toList();
        return PageResponse.of(content, page, size, count);
    }

    @WithSession
    public Uni<List<CatSummaryResponse>> findMine(Long organizationId) {
        return catRepository.findByOrganizationId(organizationId)
                .onItem().transform(cats -> cats.stream()
                        .map(catMapper::toSummaryResponse)
                        .toList());
    }

    @WithSession
    public Uni<CatInventoryStatsResponse> getInventoryStats(Long organizationId) {
        return catRepository.findAllByOrganizationId(organizationId)
                .onItem().transform(cats -> {
                    long available   = cats.stream().filter(c -> c.status == CatStatus.Available).count();
                    long unavailable = cats.stream().filter(c -> c.status == CatStatus.Unavailable).count();
                    long deleted     = cats.stream().filter(c -> c.status == CatStatus.Deleted).count();
                    return new CatInventoryStatsResponse(available, unavailable, deleted, available + unavailable + deleted);
                });
    }

    @WithTransaction
    public Uni<Either<DomainError, CatResponse>> uploadImage(Long catId, FileUpload file, Long organizationId) {
        return catRepository.findById(catId)
                .onItem().transformToUni(cat -> {
                    if (cat == null)
                        return Uni.createFrom().item(Either.left(new NotFoundError("CAT_NOT_FOUND")));
                    if (!cat.organizationId.equals(organizationId))
                        return Uni.createFrom().item(Either.left(new ForbiddenError("CAT_ACCESS_DENIED")));
                    return storageClient.upload(file)
                            .onItem().transformToUni(storage -> {
                                CatImage image = new CatImage();
                                image.catId = catId;
                                image.key = storage.key();
                                image.url = storage.url();
                                image.imageOrder = 0;
                                if (cat.profileImageUrl == null) {
                                    cat.profileImageUrl = storage.url();
                                }
                                return catRepository.persist(cat)
                                        .onItem().transformToUni(savedCat -> catImageRepository.persist(image));
                            })
                            .onItem().transformToUni(savedImage ->
                                    catRepository.findById(catId)
                                            .onItem().transformToUni(savedCat ->
                                                    catImageRepository.findByCatId(catId)
                                                            .collect().asList()
                                                            .onItem().transform(images ->
                                                                    Either.<DomainError, CatResponse>right(catMapper.toResponse(savedCat, images))
                                                            )
                                            )
                            );
                });
    }

    @WithTransaction
    public Uni<Either<DomainError, Unit>> deleteImage(Long catId, Long imageId, Long organizationId) {
        return catRepository.findById(catId)
                .onItem().transformToUni(cat -> {
                    if (cat == null)
                        return Uni.createFrom().item(Either.left(new NotFoundError("CAT_NOT_FOUND")));
                    if (!cat.organizationId.equals(organizationId))
                        return Uni.createFrom().item(Either.left(new ForbiddenError("CAT_ACCESS_DENIED")));
                    return catImageRepository.findById(imageId)
                            .onItem().transformToUni(image -> {
                                if (image == null)
                                    return Uni.createFrom().item(Either.left(new NotFoundError("CAT_IMAGE_NOT_FOUND")));
                                return storageClient.delete(image.key)
                                        .onItem().transformToUni(v -> catImageRepository.delete(image))
                                        .onItem().transform(v -> Either.unit());
                            });
                });
    }

    @WithSession
    public Uni<Either<DomainError, Unit>> deleteCat(Long id, Long organizationId) {
        return catRepository.findById(id)
                .onItem().transformToUni(cat -> {
                    if (cat == null)
                        return Uni.createFrom().item(Either.<DomainError, Unit>left(new NotFoundError("CAT_NOT_FOUND")));
                    if (!cat.organizationId.equals(organizationId))
                        return Uni.createFrom().item(Either.<DomainError, Unit>left(new ForbiddenError("CAT_ACCESS_DENIED")));
                    return adoptionClient.hasActiveRequestsForCat(id, internalSecret)
                            .onItem().transformToUni(hasActive -> {
                                if (hasActive)
                                    return Uni.createFrom().item(Either.<DomainError, Unit>left(
                                            new ConflictError("CAT_HAS_ACTIVE_ADOPTIONS")));
                                return catWriteService.markDeleted(id)
                                        .onItem().transform(v -> Either.unit());
                            });
                });
    }
}
