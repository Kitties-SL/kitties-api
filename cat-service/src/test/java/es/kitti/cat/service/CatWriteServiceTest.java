package es.kitti.cat.service;

import es.kitti.cat.entity.Cat;
import es.kitti.cat.entity.CatStatus;
import es.kitti.cat.repository.CatRepository;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatWriteServiceTest {

    @Mock CatRepository catRepository;
    @InjectMocks CatWriteService catWriteService;

    @Test
    void markDeleted_setsStatusToDeletedAndPersists() {
        Cat cat = new Cat();
        cat.id = 1L;
        cat.status = CatStatus.Available;
        when(catRepository.findById(1L)).thenReturn(Uni.createFrom().item(cat));
        when(catRepository.persist(any(Cat.class))).thenReturn(Uni.createFrom().item(cat));

        catWriteService.markDeleted(1L).await().indefinitely();

        assertEquals(CatStatus.Deleted, cat.status);
        verify(catRepository).persist(cat);
    }
}
