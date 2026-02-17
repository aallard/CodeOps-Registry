package com.codeops.registry.service;

import com.codeops.registry.dto.request.CreateInfraResourceRequest;
import com.codeops.registry.dto.request.UpdateInfraResourceRequest;
import com.codeops.registry.dto.response.InfraResourceResponse;
import com.codeops.registry.dto.response.PageResponse;
import com.codeops.registry.entity.InfraResource;
import com.codeops.registry.entity.ServiceRegistration;
import com.codeops.registry.entity.enums.InfraResourceType;
import com.codeops.registry.entity.enums.ServiceType;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.exception.ValidationException;
import com.codeops.registry.repository.InfraResourceRepository;
import com.codeops.registry.repository.ServiceRegistrationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InfraResourceService}.
 *
 * <p>Tests cover resource creation (with/without service owner, duplicate detection),
 * partial updates, deletion, paginated team queries with filters, service-scoped queries,
 * orphan finding, reassignment, and orphaning.</p>
 */
@ExtendWith(MockitoExtension.class)
class InfraResourceServiceTest {

    @Mock
    private InfraResourceRepository infraRepository;

    @Mock
    private ServiceRegistrationRepository serviceRepository;

    @InjectMocks
    private InfraResourceService infraResourceService;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID OTHER_TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SERVICE_ID = UUID.randomUUID();
    private static final UUID RESOURCE_ID = UUID.randomUUID();
    private static final String ENVIRONMENT = "production";
    private static final String RESOURCE_NAME = "my-app-bucket";

    // ──────────────────────────────────────────────────────────────
    // Helper methods
    // ──────────────────────────────────────────────────────────────

    private ServiceRegistration buildService(UUID serviceId, UUID teamId, String name, String slug) {
        ServiceRegistration service = ServiceRegistration.builder()
                .teamId(teamId)
                .name(name)
                .slug(slug)
                .serviceType(ServiceType.SPRING_BOOT_API)
                .createdByUserId(USER_ID)
                .build();
        service.setId(serviceId);
        service.setCreatedAt(Instant.now());
        service.setUpdatedAt(Instant.now());
        return service;
    }

    private InfraResource buildInfraResource(UUID resourceId, UUID teamId, ServiceRegistration service,
                                              InfraResourceType type, String name, String environment) {
        InfraResource resource = InfraResource.builder()
                .teamId(teamId)
                .service(service)
                .resourceType(type)
                .resourceName(name)
                .environment(environment)
                .region("us-east-1")
                .arnOrUrl("arn:aws:s3:::" + name)
                .description("Test resource")
                .createdByUserId(USER_ID)
                .build();
        resource.setId(resourceId);
        resource.setCreatedAt(Instant.now());
        resource.setUpdatedAt(Instant.now());
        return resource;
    }

    // ──────────────────────────────────────────────────────────────
    // createResource tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void createResource_success() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "my-app", "my-app");

        CreateInfraResourceRequest request = new CreateInfraResourceRequest(
                TEAM_ID, SERVICE_ID, InfraResourceType.S3_BUCKET, RESOURCE_NAME,
                ENVIRONMENT, "us-east-1", "arn:aws:s3:::my-app-bucket", null, "App bucket");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        when(infraRepository.findByTeamIdAndResourceTypeAndResourceNameAndEnvironment(
                TEAM_ID, InfraResourceType.S3_BUCKET, RESOURCE_NAME, ENVIRONMENT))
                .thenReturn(Optional.empty());
        when(infraRepository.save(any(InfraResource.class))).thenAnswer(invocation -> {
            InfraResource saved = invocation.getArgument(0);
            saved.setId(RESOURCE_ID);
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        InfraResourceResponse response = infraResourceService.createResource(request, USER_ID);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(RESOURCE_ID);
        assertThat(response.teamId()).isEqualTo(TEAM_ID);
        assertThat(response.serviceId()).isEqualTo(SERVICE_ID);
        assertThat(response.serviceName()).isEqualTo("my-app");
        assertThat(response.serviceSlug()).isEqualTo("my-app");
        assertThat(response.resourceType()).isEqualTo(InfraResourceType.S3_BUCKET);
        assertThat(response.resourceName()).isEqualTo(RESOURCE_NAME);
        assertThat(response.environment()).isEqualTo(ENVIRONMENT);
        assertThat(response.region()).isEqualTo("us-east-1");
        assertThat(response.description()).isEqualTo("App bucket");

        verify(serviceRepository).findById(SERVICE_ID);
        verify(infraRepository).save(any(InfraResource.class));
    }

    @Test
    void createResource_withService_validatesTeam() {
        ServiceRegistration service = buildService(SERVICE_ID, OTHER_TEAM_ID, "other-app", "other-app");

        CreateInfraResourceRequest request = new CreateInfraResourceRequest(
                TEAM_ID, SERVICE_ID, InfraResourceType.S3_BUCKET, RESOURCE_NAME,
                ENVIRONMENT, "us-east-1", null, null, null);

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));

        assertThatThrownBy(() -> infraResourceService.createResource(request, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("does not belong to the specified team");

        verify(infraRepository, never()).save(any(InfraResource.class));
    }

    @Test
    void createResource_duplicate_throwsValidation() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "my-app", "my-app");
        InfraResource existing = buildInfraResource(UUID.randomUUID(), TEAM_ID, service,
                InfraResourceType.S3_BUCKET, RESOURCE_NAME, ENVIRONMENT);

        CreateInfraResourceRequest request = new CreateInfraResourceRequest(
                TEAM_ID, SERVICE_ID, InfraResourceType.S3_BUCKET, RESOURCE_NAME,
                ENVIRONMENT, "us-east-1", null, null, null);

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        when(infraRepository.findByTeamIdAndResourceTypeAndResourceNameAndEnvironment(
                TEAM_ID, InfraResourceType.S3_BUCKET, RESOURCE_NAME, ENVIRONMENT))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> infraResourceService.createResource(request, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already exists in environment");

        verify(infraRepository, never()).save(any(InfraResource.class));
    }

    @Test
    void createResource_shared_noServiceId() {
        CreateInfraResourceRequest request = new CreateInfraResourceRequest(
                TEAM_ID, null, InfraResourceType.DOCKER_NETWORK, "shared-network",
                "local", null, null, null, "Shared docker network");

        when(infraRepository.findByTeamIdAndResourceTypeAndResourceNameAndEnvironment(
                TEAM_ID, InfraResourceType.DOCKER_NETWORK, "shared-network", "local"))
                .thenReturn(Optional.empty());
        when(infraRepository.save(any(InfraResource.class))).thenAnswer(invocation -> {
            InfraResource saved = invocation.getArgument(0);
            saved.setId(RESOURCE_ID);
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        InfraResourceResponse response = infraResourceService.createResource(request, USER_ID);

        assertThat(response).isNotNull();
        assertThat(response.serviceId()).isNull();
        assertThat(response.serviceName()).isNull();
        assertThat(response.serviceSlug()).isNull();
        assertThat(response.resourceType()).isEqualTo(InfraResourceType.DOCKER_NETWORK);
        assertThat(response.resourceName()).isEqualTo("shared-network");

        verify(serviceRepository, never()).findById(any(UUID.class));
        verify(infraRepository).save(any(InfraResource.class));
    }

    @Test
    void createResource_serviceNotFound_throwsNotFound() {
        UUID missingServiceId = UUID.randomUUID();

        CreateInfraResourceRequest request = new CreateInfraResourceRequest(
                TEAM_ID, missingServiceId, InfraResourceType.SQS_QUEUE, "event-queue",
                ENVIRONMENT, null, null, null, null);

        when(serviceRepository.findById(missingServiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> infraResourceService.createResource(request, USER_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("ServiceRegistration")
                .hasMessageContaining(missingServiceId.toString());

        verify(infraRepository, never()).save(any(InfraResource.class));
    }

    // ──────────────────────────────────────────────────────────────
    // updateResource tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void updateResource_partialUpdate() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "my-app", "my-app");
        InfraResource existing = buildInfraResource(RESOURCE_ID, TEAM_ID, service,
                InfraResourceType.S3_BUCKET, RESOURCE_NAME, ENVIRONMENT);

        UpdateInfraResourceRequest request = new UpdateInfraResourceRequest(
                null, "updated-name", "eu-west-1", null, null, "Updated description");

        when(infraRepository.findById(RESOURCE_ID)).thenReturn(Optional.of(existing));
        when(infraRepository.save(any(InfraResource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InfraResourceResponse response = infraResourceService.updateResource(RESOURCE_ID, request);

        assertThat(response).isNotNull();
        assertThat(response.resourceName()).isEqualTo("updated-name");
        assertThat(response.region()).isEqualTo("eu-west-1");
        assertThat(response.description()).isEqualTo("Updated description");
        // Service remains unchanged because serviceId was null in the request
        assertThat(response.serviceId()).isEqualTo(SERVICE_ID);

        verify(serviceRepository, never()).findById(any(UUID.class));
        verify(infraRepository).save(any(InfraResource.class));
    }

    @Test
    void updateResource_changeOwner() {
        ServiceRegistration originalService = buildService(SERVICE_ID, TEAM_ID, "my-app", "my-app");
        UUID newServiceId = UUID.randomUUID();
        ServiceRegistration newService = buildService(newServiceId, TEAM_ID, "new-app", "new-app");
        InfraResource existing = buildInfraResource(RESOURCE_ID, TEAM_ID, originalService,
                InfraResourceType.S3_BUCKET, RESOURCE_NAME, ENVIRONMENT);

        UpdateInfraResourceRequest request = new UpdateInfraResourceRequest(
                newServiceId, null, null, null, null, null);

        when(infraRepository.findById(RESOURCE_ID)).thenReturn(Optional.of(existing));
        when(serviceRepository.findById(newServiceId)).thenReturn(Optional.of(newService));
        when(infraRepository.save(any(InfraResource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InfraResourceResponse response = infraResourceService.updateResource(RESOURCE_ID, request);

        assertThat(response).isNotNull();
        assertThat(response.serviceId()).isEqualTo(newServiceId);
        assertThat(response.serviceName()).isEqualTo("new-app");
        assertThat(response.serviceSlug()).isEqualTo("new-app");

        verify(serviceRepository).findById(newServiceId);
        verify(infraRepository).save(any(InfraResource.class));
    }

    @Test
    void updateResource_notFound_throwsNotFound() {
        UUID missingId = UUID.randomUUID();

        UpdateInfraResourceRequest request = new UpdateInfraResourceRequest(
                null, "new-name", null, null, null, null);

        when(infraRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> infraResourceService.updateResource(missingId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("InfraResource")
                .hasMessageContaining(missingId.toString());

        verify(infraRepository, never()).save(any(InfraResource.class));
    }

    // ──────────────────────────────────────────────────────────────
    // deleteResource tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void deleteResource_success() {
        InfraResource existing = buildInfraResource(RESOURCE_ID, TEAM_ID, null,
                InfraResourceType.S3_BUCKET, RESOURCE_NAME, ENVIRONMENT);

        when(infraRepository.findById(RESOURCE_ID)).thenReturn(Optional.of(existing));

        infraResourceService.deleteResource(RESOURCE_ID);

        verify(infraRepository).delete(existing);
    }

    @Test
    void deleteResource_notFound_throwsNotFound() {
        UUID missingId = UUID.randomUUID();

        when(infraRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> infraResourceService.deleteResource(missingId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("InfraResource")
                .hasMessageContaining(missingId.toString());

        verify(infraRepository, never()).delete(any(InfraResource.class));
    }

    // ──────────────────────────────────────────────────────────────
    // getResourcesForTeam tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getResourcesForTeam_noFilters() {
        Pageable pageable = PageRequest.of(0, 20);
        InfraResource resource = buildInfraResource(RESOURCE_ID, TEAM_ID, null,
                InfraResourceType.S3_BUCKET, RESOURCE_NAME, ENVIRONMENT);

        when(infraRepository.findByTeamId(TEAM_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(resource), pageable, 1));

        PageResponse<InfraResourceResponse> result =
                infraResourceService.getResourcesForTeam(TEAM_ID, null, null, pageable);

        assertThat(result).isNotNull();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(RESOURCE_ID);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.isLast()).isTrue();

        verify(infraRepository).findByTeamId(TEAM_ID, pageable);
    }

    @Test
    void getResourcesForTeam_typeFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        InfraResource resource = buildInfraResource(RESOURCE_ID, TEAM_ID, null,
                InfraResourceType.SQS_QUEUE, "event-queue", ENVIRONMENT);

        when(infraRepository.findByTeamIdAndResourceType(TEAM_ID, InfraResourceType.SQS_QUEUE, pageable))
                .thenReturn(new PageImpl<>(List.of(resource), pageable, 1));

        PageResponse<InfraResourceResponse> result =
                infraResourceService.getResourcesForTeam(TEAM_ID, InfraResourceType.SQS_QUEUE, null, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).resourceType()).isEqualTo(InfraResourceType.SQS_QUEUE);

        verify(infraRepository).findByTeamIdAndResourceType(TEAM_ID, InfraResourceType.SQS_QUEUE, pageable);
        verify(infraRepository, never()).findByTeamId(eq(TEAM_ID), any(Pageable.class));
    }

    @Test
    void getResourcesForTeam_environmentFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        InfraResource resource = buildInfraResource(RESOURCE_ID, TEAM_ID, null,
                InfraResourceType.RDS_INSTANCE, "app-db", "staging");

        when(infraRepository.findByTeamIdAndEnvironment(TEAM_ID, "staging", pageable))
                .thenReturn(new PageImpl<>(List.of(resource), pageable, 1));

        PageResponse<InfraResourceResponse> result =
                infraResourceService.getResourcesForTeam(TEAM_ID, null, "staging", pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).environment()).isEqualTo("staging");

        verify(infraRepository).findByTeamIdAndEnvironment(TEAM_ID, "staging", pageable);
        verify(infraRepository, never()).findByTeamId(eq(TEAM_ID), any(Pageable.class));
    }

    @Test
    void getResourcesForTeam_bothFilters() {
        Pageable pageable = PageRequest.of(0, 10);
        InfraResource resource = buildInfraResource(RESOURCE_ID, TEAM_ID, null,
                InfraResourceType.S3_BUCKET, "prod-bucket", "production");

        when(infraRepository.findByTeamIdAndResourceTypeAndEnvironment(
                TEAM_ID, InfraResourceType.S3_BUCKET, "production", pageable))
                .thenReturn(new PageImpl<>(List.of(resource), pageable, 1));

        PageResponse<InfraResourceResponse> result =
                infraResourceService.getResourcesForTeam(TEAM_ID, InfraResourceType.S3_BUCKET, "production", pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).resourceType()).isEqualTo(InfraResourceType.S3_BUCKET);
        assertThat(result.content().get(0).environment()).isEqualTo("production");

        verify(infraRepository).findByTeamIdAndResourceTypeAndEnvironment(
                TEAM_ID, InfraResourceType.S3_BUCKET, "production", pageable);
        verify(infraRepository, never()).findByTeamId(eq(TEAM_ID), any(Pageable.class));
    }

    // ──────────────────────────────────────────────────────────────
    // getResourcesForService tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getResourcesForService_returnsList() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "my-app", "my-app");
        InfraResource resource1 = buildInfraResource(UUID.randomUUID(), TEAM_ID, service,
                InfraResourceType.S3_BUCKET, "app-bucket", ENVIRONMENT);
        InfraResource resource2 = buildInfraResource(UUID.randomUUID(), TEAM_ID, service,
                InfraResourceType.SQS_QUEUE, "app-queue", ENVIRONMENT);

        when(infraRepository.findByServiceId(SERVICE_ID)).thenReturn(List.of(resource1, resource2));

        List<InfraResourceResponse> result = infraResourceService.getResourcesForService(SERVICE_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(InfraResourceResponse::resourceType)
                .containsExactly(InfraResourceType.S3_BUCKET, InfraResourceType.SQS_QUEUE);
        assertThat(result).extracting(InfraResourceResponse::serviceId)
                .containsOnly(SERVICE_ID);

        verify(infraRepository).findByServiceId(SERVICE_ID);
    }

    // ──────────────────────────────────────────────────────────────
    // findOrphanedResources tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void findOrphanedResources_returnsOrphansOnly() {
        InfraResource orphan1 = buildInfraResource(UUID.randomUUID(), TEAM_ID, null,
                InfraResourceType.S3_BUCKET, "orphan-bucket", ENVIRONMENT);
        InfraResource orphan2 = buildInfraResource(UUID.randomUUID(), TEAM_ID, null,
                InfraResourceType.SNS_TOPIC, "orphan-topic", ENVIRONMENT);

        when(infraRepository.findOrphansByTeamId(TEAM_ID)).thenReturn(List.of(orphan1, orphan2));

        List<InfraResourceResponse> result = infraResourceService.findOrphanedResources(TEAM_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(InfraResourceResponse::serviceId).containsOnlyNulls();
        assertThat(result).extracting(InfraResourceResponse::serviceName).containsOnlyNulls();

        verify(infraRepository).findOrphansByTeamId(TEAM_ID);
    }

    @Test
    void findOrphanedResources_noOrphans_returnsEmpty() {
        when(infraRepository.findOrphansByTeamId(TEAM_ID)).thenReturn(Collections.emptyList());

        List<InfraResourceResponse> result = infraResourceService.findOrphanedResources(TEAM_ID);

        assertThat(result).isEmpty();

        verify(infraRepository).findOrphansByTeamId(TEAM_ID);
    }

    // ──────────────────────────────────────────────────────────────
    // reassignResource tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void reassignResource_success() {
        ServiceRegistration originalService = buildService(SERVICE_ID, TEAM_ID, "old-app", "old-app");
        UUID newServiceId = UUID.randomUUID();
        ServiceRegistration newService = buildService(newServiceId, TEAM_ID, "new-app", "new-app");
        InfraResource resource = buildInfraResource(RESOURCE_ID, TEAM_ID, originalService,
                InfraResourceType.S3_BUCKET, RESOURCE_NAME, ENVIRONMENT);

        when(infraRepository.findById(RESOURCE_ID)).thenReturn(Optional.of(resource));
        when(serviceRepository.findById(newServiceId)).thenReturn(Optional.of(newService));
        when(infraRepository.save(any(InfraResource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InfraResourceResponse response = infraResourceService.reassignResource(RESOURCE_ID, newServiceId);

        assertThat(response).isNotNull();
        assertThat(response.serviceId()).isEqualTo(newServiceId);
        assertThat(response.serviceName()).isEqualTo("new-app");
        assertThat(response.serviceSlug()).isEqualTo("new-app");
        assertThat(response.teamId()).isEqualTo(TEAM_ID);

        verify(infraRepository).save(any(InfraResource.class));
    }

    @Test
    void reassignResource_differentTeam_throwsValidation() {
        InfraResource resource = buildInfraResource(RESOURCE_ID, TEAM_ID, null,
                InfraResourceType.S3_BUCKET, RESOURCE_NAME, ENVIRONMENT);
        UUID newServiceId = UUID.randomUUID();
        ServiceRegistration newService = buildService(newServiceId, OTHER_TEAM_ID, "other-app", "other-app");

        when(infraRepository.findById(RESOURCE_ID)).thenReturn(Optional.of(resource));
        when(serviceRepository.findById(newServiceId)).thenReturn(Optional.of(newService));

        assertThatThrownBy(() -> infraResourceService.reassignResource(RESOURCE_ID, newServiceId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Cannot reassign resource to a service in a different team");

        verify(infraRepository, never()).save(any(InfraResource.class));
    }

    // ──────────────────────────────────────────────────────────────
    // orphanResource tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void orphanResource_success() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "my-app", "my-app");
        InfraResource resource = buildInfraResource(RESOURCE_ID, TEAM_ID, service,
                InfraResourceType.S3_BUCKET, RESOURCE_NAME, ENVIRONMENT);

        when(infraRepository.findById(RESOURCE_ID)).thenReturn(Optional.of(resource));
        when(infraRepository.save(any(InfraResource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InfraResourceResponse response = infraResourceService.orphanResource(RESOURCE_ID);

        assertThat(response).isNotNull();
        assertThat(response.serviceId()).isNull();
        assertThat(response.serviceName()).isNull();
        assertThat(response.serviceSlug()).isNull();
        assertThat(response.id()).isEqualTo(RESOURCE_ID);
        assertThat(response.resourceName()).isEqualTo(RESOURCE_NAME);

        verify(infraRepository).save(any(InfraResource.class));
    }
}
