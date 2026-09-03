package com.company.itam.department.service;

import com.company.itam.asset.repository.AssetRepository;
import com.company.itam.common.exception.CatalogInUseException;
import com.company.itam.common.exception.DuplicateResourceException;
import com.company.itam.department.dto.DepartmentRequest;
import com.company.itam.department.dto.DepartmentResponse;
import com.company.itam.department.entity.DepartmentEntity;
import com.company.itam.department.repository.DepartmentRepository;
import com.company.itam.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AssetRepository assetRepository;

    @InjectMocks
    private DepartmentService departmentService;

    private DepartmentEntity sampleDepartment;

    @BeforeEach
    void setUp() {
        sampleDepartment = new DepartmentEntity();
        sampleDepartment.setDepartmentId(1L);
        sampleDepartment.setCode("IT_DEPT");
        sampleDepartment.setName("Information Technology");
        sampleDepartment.setIsActive(true);
    }

    @Test
    void createDepartment_success() {
        DepartmentRequest request = new DepartmentRequest("HR", "Human Resources", true);
        when(departmentRepository.existsByCode("HR")).thenReturn(false);
        when(departmentRepository.save(any(DepartmentEntity.class))).thenAnswer(inv -> {
            DepartmentEntity e = inv.getArgument(0);
            e.setDepartmentId(2L);
            return e;
        });

        DepartmentResponse response = departmentService.createDepartment(request);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("HR");
        assertThat(response.getName()).isEqualTo("Human Resources");
        verify(departmentRepository).save(any(DepartmentEntity.class));
    }

    @Test
    void createDepartment_duplicateCode_throwsException() {
        DepartmentRequest request = new DepartmentRequest("IT_DEPT", "IT Dept", true);
        when(departmentRepository.existsByCode("IT_DEPT")).thenReturn(true);

        assertThatThrownBy(() -> departmentService.createDepartment(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Mã phòng ban đã tồn tại");

        verify(departmentRepository, never()).save(any());
    }

    @Test
    void deleteDepartment_success() {
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(sampleDepartment));
        when(userRepository.existsByDepartmentDepartmentId(1L)).thenReturn(false);
        when(assetRepository.existsByDepartmentDepartmentId(1L)).thenReturn(false);

        departmentService.deleteDepartment(1L);

        verify(departmentRepository).delete(sampleDepartment);
    }

    @Test
    void deleteDepartment_inUseByUser_throwsCatalogInUseException() {
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(sampleDepartment));
        when(userRepository.existsByDepartmentDepartmentId(1L)).thenReturn(true);

        assertThatThrownBy(() -> departmentService.deleteDepartment(1L))
                .isInstanceOf(CatalogInUseException.class)
                .hasMessageContaining("Phòng ban đang được gán cho người dùng");

        verify(departmentRepository, never()).delete(any());
    }

    @Test
    void deleteDepartment_inUseByAsset_throwsCatalogInUseException() {
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(sampleDepartment));
        when(userRepository.existsByDepartmentDepartmentId(1L)).thenReturn(false);
        when(assetRepository.existsByDepartmentDepartmentId(1L)).thenReturn(true);

        assertThatThrownBy(() -> departmentService.deleteDepartment(1L))
                .isInstanceOf(CatalogInUseException.class)
                .hasMessageContaining("Phòng ban đang được gán cho tài sản");

        verify(departmentRepository, never()).delete(any());
    }
}
