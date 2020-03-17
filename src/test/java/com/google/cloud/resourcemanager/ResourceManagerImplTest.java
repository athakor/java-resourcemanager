/*
 * Copyright 2015 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.resourcemanager;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.gax.paging.Page;
import com.google.cloud.Identity;
import com.google.cloud.Policy;
import com.google.cloud.Role;
import com.google.cloud.Tuple;
import com.google.cloud.resourcemanager.ProjectInfo.ResourceId;
import com.google.cloud.resourcemanager.ResourceManager.ProjectField;
import com.google.cloud.resourcemanager.ResourceManager.ProjectGetOption;
import com.google.cloud.resourcemanager.ResourceManager.ProjectListOption;
import com.google.cloud.resourcemanager.spi.ResourceManagerRpcFactory;
import com.google.cloud.resourcemanager.spi.v1beta1.ResourceManagerRpc;
import com.google.cloud.resourcemanager.testing.LocalResourceManagerHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.easymock.EasyMock;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ResourceManagerImplTest {

  private static final LocalResourceManagerHelper RESOURCE_MANAGER_HELPER =
      LocalResourceManagerHelper.create();
  private static final ResourceManager RESOURCE_MANAGER =
      RESOURCE_MANAGER_HELPER.getOptions().getService();
  private static final ProjectGetOption GET_FIELDS =
      ProjectGetOption.fields(ProjectField.NAME, ProjectField.CREATE_TIME);
  private static final ProjectListOption LIST_FIELDS =
      ProjectListOption.fields(ProjectField.NAME, ProjectField.LABELS);
  private static final ProjectListOption LIST_FILTER =
      ProjectListOption.filter("id:* name:myProject labels.color:blue LABELS.SIZE:*");
  private static final ProjectInfo PARTIAL_PROJECT =
      ProjectInfo.newBuilder("partial-project").build();
  private static final ResourceId PARENT = new ResourceId("id", "type");
  private static final ProjectInfo COMPLETE_PROJECT =
      ProjectInfo.newBuilder("complete-project")
          .setName("name")
          .setLabels(ImmutableMap.of("k1", "v1"))
          .setParent(PARENT)
          .build();
  private static final Map<ResourceManagerRpc.Option, ?> EMPTY_RPC_OPTIONS = ImmutableMap.of();
  private static final Policy POLICY =
      Policy.newBuilder()
          .addIdentity(Role.owner(), Identity.user("me@gmail.com"))
          .addIdentity(Role.editor(), Identity.serviceAccount("serviceaccount@gmail.com"))
          .build();
  // Lien
  private static final String LIEN_NAME = "liens/1234abcd";
  private static final String LIEN_PARENT = "projects/1234";
  private static final List<String> LIEN_RESTRICTIONS =
      Arrays.asList("resourcemanager.projects.get", "resourcemanager.projects.delete");
  private static final String LIEN_REASON = "Holds production API key";
  private static final String LIEN_ORIGIN = "compute.googleapis.com";
  private static final String LIEN_CREATE_TIME = "2014-10-02T15:01:23.045123456Z";
  private static final String CURSOR = "cursor";
  private static final LienInfo COMPLETE_LIEN_INFO =
      LienInfo.newBuilder(LIEN_PARENT)
          .setName(LIEN_NAME)
          .setCreateTime(LIEN_CREATE_TIME)
          .setOrigin(LIEN_ORIGIN)
          .setReason(LIEN_REASON)
          .setRestrictions(LIEN_RESTRICTIONS)
          .build();
  private static final LienInfo PARTIAL_LIEN_INFO = LienInfo.newBuilder(LIEN_PARENT).build();

  private ResourceManagerRpcFactory rpcFactoryMock = Mockito.mock(ResourceManagerRpcFactory.class);
  private ResourceManagerRpc resourceManagerRpcMock = Mockito.mock(ResourceManagerRpc.class);

  @BeforeClass
  public static void beforeClass() {
    RESOURCE_MANAGER_HELPER.start();
  }

  @Before
  public void setUp() {
    clearProjects();
  }

  private void clearProjects() {
    for (Project project : RESOURCE_MANAGER.list().getValues()) {
      RESOURCE_MANAGER_HELPER.removeProject(project.getProjectId());
    }
  }

  @AfterClass
  public static void afterClass() {
    RESOURCE_MANAGER_HELPER.stop();
  }

  private void compareReadWriteFields(ProjectInfo expected, ProjectInfo actual) {
    assertEquals(expected.getProjectId(), actual.getProjectId());
    assertEquals(expected.getName(), actual.getName());
    assertEquals(expected.getLabels(), actual.getLabels());
    assertEquals(expected.getParent(), actual.getParent());
  }

  @Test
  public void testCreate() {
    Project returnedProject = RESOURCE_MANAGER.create(PARTIAL_PROJECT);
    compareReadWriteFields(PARTIAL_PROJECT, returnedProject);
    assertEquals(ProjectInfo.State.ACTIVE, returnedProject.getState());
    assertNull(returnedProject.getName());
    assertNull(returnedProject.getParent());
    assertNotNull(returnedProject.getProjectNumber());
    assertNotNull(returnedProject.getCreateTimeMillis());
    assertSame(RESOURCE_MANAGER, returnedProject.getResourceManager());
    try {
      RESOURCE_MANAGER.create(PARTIAL_PROJECT);
      fail("Should fail, project already exists.");
    } catch (ResourceManagerException e) {
      assertEquals(409, e.getCode());
      assertTrue(
          e.getMessage().startsWith("A project with the same project ID")
              && e.getMessage().endsWith("already exists."));
    }
    returnedProject = RESOURCE_MANAGER.create(COMPLETE_PROJECT);
    compareReadWriteFields(COMPLETE_PROJECT, returnedProject);
    assertEquals(ProjectInfo.State.ACTIVE, returnedProject.getState());
    assertNotNull(returnedProject.getProjectNumber());
    assertNotNull(returnedProject.getCreateTimeMillis());
    assertSame(RESOURCE_MANAGER, returnedProject.getResourceManager());
  }

  @Test
  public void testDelete() {
    RESOURCE_MANAGER.create(COMPLETE_PROJECT);
    RESOURCE_MANAGER.delete(COMPLETE_PROJECT.getProjectId());
    assertEquals(
        ProjectInfo.State.DELETE_REQUESTED,
        RESOURCE_MANAGER.get(COMPLETE_PROJECT.getProjectId()).getState());
    try {
      RESOURCE_MANAGER.delete("some-nonexistant-project-id");
      fail("Should fail because the project doesn't exist.");
    } catch (ResourceManagerException e) {
      assertEquals(403, e.getCode());
      assertTrue(e.getMessage().contains("not found."));
    }
  }

  @Test
  public void testGet() {
    RESOURCE_MANAGER.create(COMPLETE_PROJECT);
    Project returnedProject = RESOURCE_MANAGER.get(COMPLETE_PROJECT.getProjectId());
    compareReadWriteFields(COMPLETE_PROJECT, returnedProject);
    assertEquals(RESOURCE_MANAGER, returnedProject.getResourceManager());
    RESOURCE_MANAGER_HELPER.removeProject(COMPLETE_PROJECT.getProjectId());
    assertNull(RESOURCE_MANAGER.get(COMPLETE_PROJECT.getProjectId()));
  }

  @Test
  public void testGetWithOptions() {
    Project originalProject = RESOURCE_MANAGER.create(COMPLETE_PROJECT);
    Project returnedProject = RESOURCE_MANAGER.get(COMPLETE_PROJECT.getProjectId(), GET_FIELDS);
    assertFalse(COMPLETE_PROJECT.equals(returnedProject));
    assertEquals(COMPLETE_PROJECT.getProjectId(), returnedProject.getProjectId());
    assertEquals(COMPLETE_PROJECT.getName(), returnedProject.getName());
    assertEquals(originalProject.getCreateTimeMillis(), returnedProject.getCreateTimeMillis());
    assertNull(returnedProject.getParent());
    assertNull(returnedProject.getProjectNumber());
    assertNull(returnedProject.getState());
    assertTrue(returnedProject.getLabels().isEmpty());
    assertEquals(RESOURCE_MANAGER, originalProject.getResourceManager());
    assertEquals(RESOURCE_MANAGER, returnedProject.getResourceManager());
  }

  @Test
  public void testList() {
    Page<Project> projects = RESOURCE_MANAGER.list();
    assertFalse(projects.getValues().iterator().hasNext());
    RESOURCE_MANAGER.create(PARTIAL_PROJECT);
    RESOURCE_MANAGER.create(COMPLETE_PROJECT);
    for (Project p : RESOURCE_MANAGER.list().getValues()) {
      if (p.getProjectId().equals(PARTIAL_PROJECT.getProjectId())) {
        compareReadWriteFields(PARTIAL_PROJECT, p);
      } else if (p.getProjectId().equals(COMPLETE_PROJECT.getProjectId())) {
        compareReadWriteFields(COMPLETE_PROJECT, p);
      } else {
        fail("Some unexpected project returned by list.");
      }
      assertSame(RESOURCE_MANAGER, p.getResourceManager());
    }
  }

  @Test
  public void testListPaging() {
    RESOURCE_MANAGER.create(PARTIAL_PROJECT);
    RESOURCE_MANAGER.create(COMPLETE_PROJECT);
    Page<Project> page = RESOURCE_MANAGER.list(ProjectListOption.pageSize(1));
    assertNotNull(page.getNextPageToken());
    Iterator<Project> iterator = page.getValues().iterator();
    compareReadWriteFields(COMPLETE_PROJECT, iterator.next());
    assertFalse(iterator.hasNext());
    page = page.getNextPage();
    iterator = page.getValues().iterator();
    compareReadWriteFields(PARTIAL_PROJECT, iterator.next());
    assertFalse(iterator.hasNext());
    assertNull(page.getNextPageToken());
  }

  @Test
  public void testListFieldOptions() {
    RESOURCE_MANAGER.create(COMPLETE_PROJECT);
    Page<Project> projects = RESOURCE_MANAGER.list(LIST_FIELDS);
    Project returnedProject = projects.iterateAll().iterator().next();
    assertEquals(COMPLETE_PROJECT.getProjectId(), returnedProject.getProjectId());
    assertEquals(COMPLETE_PROJECT.getName(), returnedProject.getName());
    assertEquals(COMPLETE_PROJECT.getLabels(), returnedProject.getLabels());
    assertNull(returnedProject.getParent());
    assertNull(returnedProject.getProjectNumber());
    assertNull(returnedProject.getState());
    assertNull(returnedProject.getCreateTimeMillis());
    assertSame(RESOURCE_MANAGER, returnedProject.getResourceManager());
  }

  @Test
  public void testListPagingWithFieldOptions() {
    RESOURCE_MANAGER.create(PARTIAL_PROJECT);
    RESOURCE_MANAGER.create(COMPLETE_PROJECT);
    Page<Project> projects = RESOURCE_MANAGER.list(LIST_FIELDS, ProjectListOption.pageSize(1));
    assertNotNull(projects.getNextPageToken());
    Iterator<Project> iterator = projects.getValues().iterator();
    Project returnedProject = iterator.next();
    assertEquals(COMPLETE_PROJECT.getProjectId(), returnedProject.getProjectId());
    assertEquals(COMPLETE_PROJECT.getName(), returnedProject.getName());
    assertEquals(COMPLETE_PROJECT.getLabels(), returnedProject.getLabels());
    assertNull(returnedProject.getParent());
    assertNull(returnedProject.getProjectNumber());
    assertNull(returnedProject.getState());
    assertNull(returnedProject.getCreateTimeMillis());
    assertSame(RESOURCE_MANAGER, returnedProject.getResourceManager());
    assertFalse(iterator.hasNext());
    projects = projects.getNextPage();
    iterator = projects.getValues().iterator();
    returnedProject = iterator.next();
    assertEquals(PARTIAL_PROJECT.getProjectId(), returnedProject.getProjectId());
    assertEquals(PARTIAL_PROJECT.getName(), returnedProject.getName());
    assertEquals(PARTIAL_PROJECT.getLabels(), returnedProject.getLabels());
    assertNull(returnedProject.getParent());
    assertNull(returnedProject.getProjectNumber());
    assertNull(returnedProject.getState());
    assertNull(returnedProject.getCreateTimeMillis());
    assertSame(RESOURCE_MANAGER, returnedProject.getResourceManager());
    assertFalse(iterator.hasNext());
    assertNull(projects.getNextPageToken());
  }

  @Test
  public void testListFilterOptions() {
    ProjectInfo matchingProject =
        ProjectInfo.newBuilder("matching-project")
            .setName("MyProject")
            .setLabels(ImmutableMap.of("color", "blue", "size", "big"))
            .build();
    ProjectInfo nonMatchingProject1 =
        ProjectInfo.newBuilder("non-matching-project1")
            .setName("myProject")
            .setLabels(ImmutableMap.of("color", "blue"))
            .build();
    ProjectInfo nonMatchingProject2 =
        ProjectInfo.newBuilder("non-matching-project2")
            .setName("myProj")
            .setLabels(ImmutableMap.of("color", "blue", "size", "big"))
            .build();
    ProjectInfo nonMatchingProject3 = ProjectInfo.newBuilder("non-matching-project3").build();
    RESOURCE_MANAGER.create(matchingProject);
    RESOURCE_MANAGER.create(nonMatchingProject1);
    RESOURCE_MANAGER.create(nonMatchingProject2);
    RESOURCE_MANAGER.create(nonMatchingProject3);
    for (Project p : RESOURCE_MANAGER.list(LIST_FILTER).getValues()) {
      assertFalse(p.equals(nonMatchingProject1));
      assertFalse(p.equals(nonMatchingProject2));
      compareReadWriteFields(matchingProject, p);
      assertSame(RESOURCE_MANAGER, p.getResourceManager());
    }
  }

  @Test
  public void testReplace() {
    ProjectInfo createdProject = RESOURCE_MANAGER.create(COMPLETE_PROJECT);
    Map<String, String> newLabels = ImmutableMap.of("new k1", "new v1");
    ProjectInfo anotherCompleteProject =
        ProjectInfo.newBuilder(COMPLETE_PROJECT.getProjectId())
            .setLabels(newLabels)
            .setProjectNumber(987654321L)
            .setCreateTimeMillis(230682061315L)
            .setState(ProjectInfo.State.DELETE_REQUESTED)
            .setParent(createdProject.getParent())
            .build();
    Project returnedProject = RESOURCE_MANAGER.replace(anotherCompleteProject);
    compareReadWriteFields(anotherCompleteProject, returnedProject);
    assertEquals(createdProject.getProjectNumber(), returnedProject.getProjectNumber());
    assertEquals(createdProject.getCreateTimeMillis(), returnedProject.getCreateTimeMillis());
    assertEquals(createdProject.getState(), returnedProject.getState());
    assertEquals(RESOURCE_MANAGER, returnedProject.getResourceManager());
    ProjectInfo nonexistantProject =
        ProjectInfo.newBuilder("some-project-id-that-does-not-exist").build();
    try {
      RESOURCE_MANAGER.replace(nonexistantProject);
      fail("Should fail because the project doesn't exist.");
    } catch (ResourceManagerException e) {
      assertEquals(403, e.getCode());
      assertTrue(e.getMessage().contains("the project was not found"));
    }
  }

  @Test
  public void testUndelete() {
    RESOURCE_MANAGER.create(COMPLETE_PROJECT);
    RESOURCE_MANAGER.delete(COMPLETE_PROJECT.getProjectId());
    assertEquals(
        ProjectInfo.State.DELETE_REQUESTED,
        RESOURCE_MANAGER.get(COMPLETE_PROJECT.getProjectId()).getState());
    RESOURCE_MANAGER.undelete(COMPLETE_PROJECT.getProjectId());
    ProjectInfo revivedProject = RESOURCE_MANAGER.get(COMPLETE_PROJECT.getProjectId());
    compareReadWriteFields(COMPLETE_PROJECT, revivedProject);
    assertEquals(ProjectInfo.State.ACTIVE, revivedProject.getState());
    try {
      RESOURCE_MANAGER.undelete("invalid-project-id");
      fail("Should fail because the project doesn't exist.");
    } catch (ResourceManagerException e) {
      assertEquals(403, e.getCode());
      assertTrue(e.getMessage().contains("the project was not found"));
    }
  }

  @Test
  public void testGetPolicy() {
    assertNull(RESOURCE_MANAGER.getPolicy(COMPLETE_PROJECT.getProjectId()));
    RESOURCE_MANAGER.create(COMPLETE_PROJECT);
    RESOURCE_MANAGER.replacePolicy(COMPLETE_PROJECT.getProjectId(), POLICY);
    Policy retrieved = RESOURCE_MANAGER.getPolicy(COMPLETE_PROJECT.getProjectId());
    assertEquals(POLICY.getBindings(), retrieved.getBindings());
    assertNotNull(retrieved.getEtag());
    assertEquals(0, retrieved.getVersion());
  }

  @Test
  public void testReplacePolicy() {
    try {
      RESOURCE_MANAGER.replacePolicy("nonexistent-project", POLICY);
      fail("Project doesn't exist.");
    } catch (ResourceManagerException e) {
      assertEquals(403, e.getCode());
      assertTrue(e.getMessage().endsWith("project was not found."));
    }
    RESOURCE_MANAGER.create(PARTIAL_PROJECT);
    Policy oldPolicy = RESOURCE_MANAGER.getPolicy(PARTIAL_PROJECT.getProjectId());
    RESOURCE_MANAGER.replacePolicy(PARTIAL_PROJECT.getProjectId(), POLICY);
    try {
      RESOURCE_MANAGER.replacePolicy(PARTIAL_PROJECT.getProjectId(), oldPolicy);
      fail("Policy with an invalid etag didn't cause error.");
    } catch (ResourceManagerException e) {
      assertEquals(409, e.getCode());
      assertTrue(e.getMessage().contains("Policy etag mismatch"));
    }
    String originalEtag = RESOURCE_MANAGER.getPolicy(PARTIAL_PROJECT.getProjectId()).getEtag();
    Policy newPolicy = RESOURCE_MANAGER.replacePolicy(PARTIAL_PROJECT.getProjectId(), POLICY);
    assertEquals(POLICY.getBindings(), newPolicy.getBindings());
    assertNotNull(newPolicy.getEtag());
    assertNotEquals(originalEtag, newPolicy.getEtag());
  }

  @Test
  public void testTestPermissions() {
    List<String> permissions = ImmutableList.of("resourcemanager.projects.get");
    try {
      RESOURCE_MANAGER.testPermissions("nonexistent-project", permissions);
      fail("Nonexistent project");
    } catch (ResourceManagerException e) {
      assertEquals(403, e.getCode());
      assertEquals("Project nonexistent-project not found.", e.getMessage());
    }
    RESOURCE_MANAGER.create(PARTIAL_PROJECT);
    assertEquals(
        ImmutableList.of(true),
        RESOURCE_MANAGER.testPermissions(PARTIAL_PROJECT.getProjectId(), permissions));
  }

  @Test
  public void testRetryableException() {
    ResourceManagerRpcFactory rpcFactoryMock = EasyMock.createMock(ResourceManagerRpcFactory.class);
    ResourceManagerRpc resourceManagerRpcMock = EasyMock.createMock(ResourceManagerRpc.class);
    EasyMock.expect(rpcFactoryMock.create(EasyMock.anyObject(ResourceManagerOptions.class)))
        .andReturn(resourceManagerRpcMock);
    EasyMock.replay(rpcFactoryMock);
    ResourceManager resourceManagerMock =
        ResourceManagerOptions.newBuilder()
            .setServiceRpcFactory(rpcFactoryMock)
            .build()
            .getService();
    EasyMock.expect(resourceManagerRpcMock.get(PARTIAL_PROJECT.getProjectId(), EMPTY_RPC_OPTIONS))
        .andThrow(new ResourceManagerException(500, "Internal Error"))
        .andReturn(PARTIAL_PROJECT.toPb());
    EasyMock.replay(resourceManagerRpcMock);
    Project returnedProject = resourceManagerMock.get(PARTIAL_PROJECT.getProjectId());
    assertEquals(
        new Project(resourceManagerMock, new ProjectInfo.BuilderImpl(PARTIAL_PROJECT)),
        returnedProject);
  }

  @Test
  public void testNonRetryableException() {
    ResourceManagerRpcFactory rpcFactoryMock = EasyMock.createMock(ResourceManagerRpcFactory.class);
    ResourceManagerRpc resourceManagerRpcMock = EasyMock.createMock(ResourceManagerRpc.class);
    EasyMock.expect(rpcFactoryMock.create(EasyMock.anyObject(ResourceManagerOptions.class)))
        .andReturn(resourceManagerRpcMock);
    EasyMock.replay(rpcFactoryMock);
    ResourceManager resourceManagerMock =
        ResourceManagerOptions.newBuilder()
            .setServiceRpcFactory(rpcFactoryMock)
            .build()
            .getService();
    EasyMock.expect(resourceManagerRpcMock.get(PARTIAL_PROJECT.getProjectId(), EMPTY_RPC_OPTIONS))
        .andThrow(
            new ResourceManagerException(
                403, "Project " + PARTIAL_PROJECT.getProjectId() + " not found."))
        .once();
    EasyMock.replay(resourceManagerRpcMock);
    try {
      resourceManagerMock.get(PARTIAL_PROJECT.getProjectId());
      fail();
    } catch (ResourceManagerException e) {
      assertTrue(e.getMessage().contains("Project partial-project not found"));
    }
  }

  @Test
  public void testRuntimeException() {
    ResourceManagerRpcFactory rpcFactoryMock = EasyMock.createMock(ResourceManagerRpcFactory.class);
    ResourceManagerRpc resourceManagerRpcMock = EasyMock.createMock(ResourceManagerRpc.class);
    EasyMock.expect(rpcFactoryMock.create(EasyMock.anyObject(ResourceManagerOptions.class)))
        .andReturn(resourceManagerRpcMock);
    EasyMock.replay(rpcFactoryMock);
    ResourceManager resourceManagerMock =
        ResourceManagerOptions.newBuilder()
            .setServiceRpcFactory(rpcFactoryMock)
            .build()
            .getService();
    String exceptionMessage = "Artificial runtime exception";
    EasyMock.expect(resourceManagerRpcMock.get(PARTIAL_PROJECT.getProjectId(), EMPTY_RPC_OPTIONS))
        .andThrow(new RuntimeException(exceptionMessage));
    EasyMock.replay(resourceManagerRpcMock);
    try {
      resourceManagerMock.get(PARTIAL_PROJECT.getProjectId());
      fail();
    } catch (ResourceManagerException exception) {
      assertEquals(exceptionMessage, exception.getCause().getMessage());
    }
  }

  @Test
  public void testTestOrgPermissions() throws IOException {
    String organization = "organization/12345";
    List<String> permissions =
        ImmutableList.of(
            "resourcemanager.organizations.get", "resourcemanager.organizations.getIamPolicy");
    Map<String, Boolean> expected =
        ImmutableMap.of(
            "resourcemanager.organizations.get",
            true,
            "resourcemanager.organizations.getIamPolicy",
            false);
    when(rpcFactoryMock.create(Mockito.any(ResourceManagerOptions.class)))
        .thenReturn(resourceManagerRpcMock);
    ResourceManager resourceManager =
        ResourceManagerOptions.newBuilder()
            .setServiceRpcFactory(rpcFactoryMock)
            .build()
            .getService();
    when(resourceManagerRpcMock.testOrgPermissions(organization, permissions)).thenReturn(expected);
    Map<String, Boolean> actual = resourceManager.testOrgPermissions(organization, permissions);
    assertEquals(expected, actual);
    verify(resourceManagerRpcMock).testOrgPermissions(organization, permissions);
  }

  @Test
  public void testTestOrgPermissionsWithResourceManagerException() throws IOException {
    String organization = "organizations/12345";
    String exceptionMessage = "Not Found";
    List<String> permissions =
        ImmutableList.of(
            "resourcemanager.organizations.get", "resourcemanager.organizations.getIamPolicy");
    when(rpcFactoryMock.create(Mockito.any(ResourceManagerOptions.class)))
        .thenReturn(resourceManagerRpcMock);
    ResourceManager resourceManager =
        ResourceManagerOptions.newBuilder()
            .setServiceRpcFactory(rpcFactoryMock)
            .build()
            .getService();
    doThrow(new ResourceManagerException(404, exceptionMessage))
        .when(resourceManagerRpcMock)
        .testOrgPermissions(organization, permissions);
    try {
      resourceManager.testOrgPermissions(organization, permissions);
    } catch (ResourceManagerException expected) {
      assertEquals(404, expected.getCode());
      assertEquals(exceptionMessage, expected.getMessage());
    }
  }

  @Test
  public void testCreateLien() {
    ResourceManagerRpcFactory rpcFactoryMock = EasyMock.createMock(ResourceManagerRpcFactory.class);
    ResourceManagerRpc resourceManagerRpcMock = EasyMock.createMock(ResourceManagerRpc.class);
    EasyMock.expect(rpcFactoryMock.create(EasyMock.anyObject(ResourceManagerOptions.class)))
        .andReturn(resourceManagerRpcMock);
    EasyMock.replay(rpcFactoryMock);
    ResourceManager resourceManagerMock =
        ResourceManagerOptions.newBuilder()
            .setServiceRpcFactory(rpcFactoryMock)
            .build()
            .getService();
    EasyMock.expect(resourceManagerRpcMock.createLien(COMPLETE_LIEN_INFO.toPb()))
        .andReturn(COMPLETE_LIEN_INFO.toPb());
    EasyMock.replay(resourceManagerRpcMock);
    Lien lien = resourceManagerMock.createLien(COMPLETE_LIEN_INFO);
    assertEquals(LIEN_NAME, lien.getName());
    assertEquals(LIEN_CREATE_TIME, lien.getCreateTime());
    assertEquals(LIEN_ORIGIN, lien.getOrigin());
    assertEquals(LIEN_PARENT, lien.getParent());
    assertEquals(LIEN_REASON, lien.getReason());
    assertEquals(LIEN_RESTRICTIONS, lien.getRestrictions());
  }

  @Test
  public void testDeleteLien() {
    try {
      RESOURCE_MANAGER.deleteLien(LIEN_NAME);
      fail("Should fail because the lien doesn't exist.");
    } catch (ResourceManagerException e) {
      assertEquals(404, e.getCode());
    }
  }

  @Test
  public void testGetLien() {
    ResourceManagerRpcFactory rpcFactoryMock = EasyMock.createMock(ResourceManagerRpcFactory.class);
    ResourceManagerRpc resourceManagerRpcMock = EasyMock.createMock(ResourceManagerRpc.class);
    EasyMock.expect(rpcFactoryMock.create(EasyMock.anyObject(ResourceManagerOptions.class)))
        .andReturn(resourceManagerRpcMock);
    EasyMock.replay(rpcFactoryMock);
    ResourceManager resourceManagerMock =
        ResourceManagerOptions.newBuilder()
            .setServiceRpcFactory(rpcFactoryMock)
            .build()
            .getService();
    EasyMock.expect(resourceManagerRpcMock.getLien(LIEN_NAME)).andReturn(COMPLETE_LIEN_INFO.toPb());
    EasyMock.replay(resourceManagerRpcMock);
    Lien lien = resourceManagerMock.getLien(LIEN_NAME);
    assertEquals(LIEN_NAME, lien.getName());
    assertEquals(LIEN_CREATE_TIME, lien.getCreateTime());
    assertEquals(LIEN_ORIGIN, lien.getOrigin());
    assertEquals(LIEN_PARENT, lien.getParent());
    assertEquals(LIEN_REASON, lien.getReason());
    assertEquals(LIEN_RESTRICTIONS, lien.getRestrictions());
  }

  @Test
  public void testGetLienWithException() {
    try {
      Lien lien = RESOURCE_MANAGER.getLien(LIEN_NAME);
      assertEquals(LIEN_NAME, lien.getName());
      assertEquals(LIEN_CREATE_TIME, lien.getCreateTime());
      assertEquals(LIEN_ORIGIN, lien.getOrigin());
      assertEquals(LIEN_PARENT, lien.getParent());
      assertEquals(LIEN_REASON, lien.getReason());
      assertEquals(LIEN_RESTRICTIONS, lien.getRestrictions());
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void testListLien() {
    ResourceManagerRpcFactory rpcFactoryMock = EasyMock.createMock(ResourceManagerRpcFactory.class);
    ResourceManagerRpc resourceManagerRpcMock = EasyMock.createMock(ResourceManagerRpc.class);
    EasyMock.expect(rpcFactoryMock.create(EasyMock.anyObject(ResourceManagerOptions.class)))
        .andReturn(resourceManagerRpcMock);
    EasyMock.replay(rpcFactoryMock);
    ResourceManager resourceManagerMock =
        ResourceManagerOptions.newBuilder()
            .setServiceRpcFactory(rpcFactoryMock)
            .build()
            .getService();
    ImmutableList<Lien> lienList =
        ImmutableList.of(new Lien(resourceManagerMock, new LienInfo.BuilderImpl(LIEN_PARENT)));
    Tuple<String, Iterable<com.google.api.services.cloudresourcemanager.model.Lien>> result =
        Tuple.of(CURSOR, Iterables.transform(lienList, LienInfo.TO_PB_FUNCTION));
    EasyMock.expect(resourceManagerRpcMock.listLiens(LIEN_PARENT, EMPTY_RPC_OPTIONS))
        .andReturn(result);
    EasyMock.replay(resourceManagerRpcMock);
    Page<Lien> page = resourceManagerMock.listLiens(LIEN_PARENT);
    assertEquals(CURSOR, page.getNextPageToken());
    assertArrayEquals(lienList.toArray(), Iterables.toArray(page.getValues(), Lien.class));
  }

  @Test
  public void testListLienWithResourceManagerException() {
    try {
      Page<Lien> page = RESOURCE_MANAGER.listLiens(LIEN_PARENT);
      assertEquals(CURSOR, page.getNextPageToken());
      fail();
    } catch (ResourceManagerException expected) {
      assertTrue(expected.getMessage().contains("404 Not Found"));
    }
  }

  @Test
  public void testListLienPaging() {
    ResourceManagerRpcFactory rpcFactoryMock = EasyMock.createMock(ResourceManagerRpcFactory.class);
    ResourceManagerRpc resourceManagerRpcMock = EasyMock.createMock(ResourceManagerRpc.class);
    EasyMock.expect(rpcFactoryMock.create(EasyMock.anyObject(ResourceManagerOptions.class)))
        .andReturn(resourceManagerRpcMock);
    EasyMock.replay(rpcFactoryMock);
    ResourceManager resourceManagerMock =
        ResourceManagerOptions.newBuilder()
            .setServiceRpcFactory(rpcFactoryMock)
            .build()
            .getService();
    ImmutableList<Lien> lienList =
        ImmutableList.of(new Lien(resourceManagerMock, new LienInfo.BuilderImpl(LIEN_PARENT)));
    Tuple<String, Iterable<com.google.api.services.cloudresourcemanager.model.Lien>> result =
        Tuple.of(CURSOR, Iterables.transform(lienList, LienInfo.TO_PB_FUNCTION));
    Map<ResourceManagerRpc.Option, ?> RPC_OPTIONS =
        ImmutableMap.of(ResourceManagerRpc.Option.PAGE_SIZE, 1);
    EasyMock.expect(resourceManagerRpcMock.listLiens(LIEN_PARENT, RPC_OPTIONS)).andReturn(result);
    EasyMock.replay(resourceManagerRpcMock);
    Page<Lien> page =
        resourceManagerMock.listLiens(LIEN_PARENT, ResourceManager.LienListOption.pageSize(1));
    assertEquals(CURSOR, page.getNextPageToken());
    assertArrayEquals(lienList.toArray(), Iterables.toArray(page.getValues(), Lien.class));
  }
}
