/*
 * Copyright 2020 Google LLC
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LienInfoTest {

  private static final String LIEN_NAME = "liens/1234abcd";
  private static final String LIEN_PARENT = " projects/1234";
  private static final List<String> LIEN_RESTRICTIONS =
      Arrays.asList("resourcemanager.projects.get", "resourcemanager.projects.delete");
  private static final String LIEN_REASON = "Holds production API key";
  private static final String LIEN_ORIGIN = "compute.googleapis.com";
  private static final String LIEN_CREATE_TIME = "2014-10-02T15:01:23.045123456Z";
  private static final LienInfo FULL_LIEN_INFO =
      LienInfo.newBuilder(LIEN_PARENT)
          .setName(LIEN_NAME)
          .setCreateTime(LIEN_CREATE_TIME)
          .setOrigin(LIEN_ORIGIN)
          .setReason(LIEN_REASON)
          .setRestrictions(LIEN_RESTRICTIONS)
          .build();
  private static final LienInfo PARTIAL_LIEN_INFO = LienInfo.newBuilder(LIEN_PARENT).build();

  @Test
  public void testBuilder() {
    assertEquals(LIEN_NAME, FULL_LIEN_INFO.getName());
    assertEquals(LIEN_PARENT, FULL_LIEN_INFO.getParent());
    assertEquals(LIEN_RESTRICTIONS, FULL_LIEN_INFO.getRestrictions());
    assertEquals(LIEN_REASON, FULL_LIEN_INFO.getReason());
    assertEquals(LIEN_ORIGIN, FULL_LIEN_INFO.getOrigin());
    assertEquals(LIEN_CREATE_TIME, FULL_LIEN_INFO.getCreateTime());
  }

  @Test
  public void testToBuilder() {
    String name = "liens/12345";
    String parent = "projects/12345";
    String createTime = "2015-11-02T15:02:23.045123456Z";
    LienInfo lienInfo =
        FULL_LIEN_INFO
            .toBuilder()
            .setName("liens/12345")
            .setParent("projects/12345")
            .setCreateTime(createTime)
            .build();
    assertEquals(name, lienInfo.getName());
    assertEquals(parent, lienInfo.getParent());
    assertEquals(createTime, lienInfo.getCreateTime());
  }

  @Test
  public void testToAndFromProtobuf() {
    assertTrue(FULL_LIEN_INFO.toProtobuf().getCreateTime().endsWith("Z"));
    compareLiens(FULL_LIEN_INFO, LienInfo.fromProtobuf(FULL_LIEN_INFO.toProtobuf()));
  }

  @Test
  public void testEquals() {
    compareLiens(
        FULL_LIEN_INFO,
        LienInfo.newBuilder(LIEN_PARENT)
            .setName(LIEN_NAME)
            .setCreateTime(LIEN_CREATE_TIME)
            .setOrigin(LIEN_ORIGIN)
            .setReason(LIEN_REASON)
            .setRestrictions(LIEN_RESTRICTIONS)
            .build());
    compareLiens(FULL_LIEN_INFO, new LienInfo.Builder(FULL_LIEN_INFO).build());
    assertNotEquals(FULL_LIEN_INFO, PARTIAL_LIEN_INFO);
    assertNotNull(FULL_LIEN_INFO);
  }

  private void compareLiens(LienInfo expected, LienInfo actual) {
    assertEquals(expected, actual);
    assertEquals(expected.getName(), actual.getName());
    assertEquals(expected.getParent(), actual.getParent());
    assertEquals(expected.getRestrictions(), actual.getRestrictions());
    assertEquals(expected.getReason(), actual.getReason());
    assertEquals(expected.getOrigin(), actual.getOrigin());
    assertEquals(expected.getCreateTime(), actual.getCreateTime());
  }
}
