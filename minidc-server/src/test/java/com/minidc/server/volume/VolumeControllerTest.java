package com.minidc.server.volume;

import com.minidc.common.enums.Role;
import com.minidc.common.enums.VolumeStatus;
import com.minidc.common.model.VolumeInfo;
import com.minidc.server.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

class VolumeControllerTest {

    private VolumeService volumeService;
    private VolumeController controller;
    private CurrentUser currentUser;

    @BeforeEach
    void setUp() {
        volumeService = Mockito.mock(VolumeService.class);
        controller = new VolumeController(volumeService);
        currentUser = new CurrentUser(UUID.randomUUID(), "borrower@minidc.local", Role.MEMBER);
    }

    @Test
    void shouldReturnMyVolumes() {
        VolumeInfo vol = new VolumeInfo(
                UUID.randomUUID(), currentUser.id(), "HomeDir", 30, VolumeStatus.AVAILABLE,
                "/home/borrower/workspace", null, null, Instant.now(), Instant.now()
        );
        when(volumeService.getUserVolumes(currentUser.id())).thenReturn(List.of(vol));

        ResponseEntity<List<VolumeInfo>> response = controller.getMyVolumes(currentUser);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("HomeDir", response.getBody().get(0).name());
    }

    @Test
    void shouldCreateVolumeViaRest() {
        VolumeInfo vol = new VolumeInfo(
                UUID.randomUUID(), currentUser.id(), "Code", 10, VolumeStatus.AVAILABLE,
                "/mnt/code", null, null, Instant.now(), Instant.now()
        );
        when(volumeService.createVolume(currentUser.id(), "Code", 10, "/mnt/code")).thenReturn(vol);

        ResponseEntity<VolumeInfo> response = controller.createVolume(
                new VolumeController.CreateVolumeRequest("Code", 10, "/mnt/code"),
                currentUser
        );

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Code", response.getBody().name());
    }
}
