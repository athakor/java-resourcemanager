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

import com.google.api.services.cloudresourcemanager.model.BooleanPolicy;
import com.google.api.services.cloudresourcemanager.model.ListPolicy;
import com.google.api.services.cloudresourcemanager.model.OrgPolicy;
import com.google.api.services.cloudresourcemanager.model.RestoreDefault;
import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** A Google Cloud Resource Manager organization policy metadata object. */
public class OrgPolicyInfo implements Serializable {

  private static final long serialVersionUID = 9148970963697734236L;

  static final Function<OrgPolicy, OrgPolicyInfo> FROM_PB_FUNCTION =
      new Function<OrgPolicy, OrgPolicyInfo>() {
        @Override
        public OrgPolicyInfo apply(OrgPolicy pb) {
          return OrgPolicyInfo.fromPb(pb);
        }
      };
  static final Function<OrgPolicyInfo, OrgPolicy> TO_PB_FUNCTION =
      new Function<OrgPolicyInfo, OrgPolicy>() {
        @Override
        public OrgPolicy apply(OrgPolicyInfo orgPolicyInfo) {
          return orgPolicyInfo.toPb();
        }
      };

  private BoolPolicy boolPolicy;
  private String constraint;
  private String etag;
  private Policies policies;
  private RestoreDefault restoreDefault;
  private String updateTime;
  private Integer version;

  /** Used For boolean Constraints, whether to enforce the Constraint or not. */
  static class BoolPolicy implements Serializable {

    private static final long serialVersionUID = -2133042982786959351L;
    private final Boolean enforce;

    BoolPolicy(Boolean enforce) {
      this.enforce = enforce;
    }

    public boolean getEnforce() {
      return enforce;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("enforce", getEnforce()).toString();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      BoolPolicy that = (BoolPolicy) o;
      return Objects.equals(enforce, that.enforce);
    }

    @Override
    public int hashCode() {
      return Objects.hash(enforce);
    }

    BooleanPolicy toPb() {
      return new BooleanPolicy().setEnforced(enforce);
    }

    static BoolPolicy fromPb(BooleanPolicy booleanPolicy) {
      return new BoolPolicy(booleanPolicy.getEnforced());
    }
  }

  /**
   * The organization ListPolicy object.
   *
   * <p>ListPolicy can define specific values and subtrees of Cloud Resource Manager resource
   * hierarchy (Organizations, Folders, Projects) that are allowed or denied by setting the
   * allowedValues and deniedValues fields. This is achieved by using the under: and optional is:
   * prefixes. The under: prefix denotes resource subtree values. The is: prefix is used to denote
   * specific values, and is required only if the value contains a ":". Values prefixed with "is:"
   * are treated the same as values with no prefix. Ancestry subtrees must be in one of the
   * following formats: - "projects/", e.g. "projects/tokyo-rain-123" - "folders/", e.g.
   * "folders/1234" - "organizations/", e.g. "organizations/1234" The supportsUnder field of the
   * associated Constraint defines whether ancestry prefixes can be used. You can set allowedValues
   * and deniedValues in the same Policy if allValues is ALL_VALUES_UNSPECIFIED. ALLOW or DENY are
   * used to allow or deny all values. If allValues is set to either ALLOW or DENY, allowedValues
   * and deniedValues must be unset.
   */
  static class Policies implements Serializable {

    private static final long serialVersionUID = -2133042982786959352L;

    private final String allValues;
    private final List<String> allowedValues;
    private final List<java.lang.String> deniedValues;
    private final Boolean inheritFromParent;
    private final String suggestedValue;

    public Policies(
        String allValues,
        List<String> allowedValues,
        List<String> deniedValues,
        Boolean inheritFromParent,
        String suggestedValue) {
      this.allValues = allValues;
      this.allowedValues = allowedValues;
      this.deniedValues = deniedValues;
      this.inheritFromParent = inheritFromParent;
      this.suggestedValue = suggestedValue;
    }

    /** Returns all the Values state of this policy. */
    public String getAllValues() {
      return allValues;
    }

    /** Returns the list of allowed values of this resource */
    public List<String> getAllowedValues() {
      return allowedValues;
    }

    /** Returns the list of denied values of this resource. */
    public List<String> getDeniedValues() {
      return deniedValues;
    }

    /** Returns the inheritance behavior for this Policy */
    public Boolean getInheritFromParent() {
      return inheritFromParent;
    }

    /** Returns the suggested value of this policy. */
    public String getSuggestedValue() {
      return suggestedValue;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("allValues", getAllValues())
          .add("allowedValues", getAllowedValues())
          .add("deniedValues", getDeniedValues())
          .add("inheritFromParent", getInheritFromParent())
          .add("suggestedValue", getSuggestedValue())
          .toString();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Policies policies = (Policies) o;
      return Objects.equals(allValues, policies.allValues)
          && Objects.equals(allowedValues, policies.allowedValues)
          && Objects.equals(deniedValues, policies.deniedValues)
          && Objects.equals(inheritFromParent, policies.inheritFromParent)
          && Objects.equals(suggestedValue, policies.suggestedValue);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          allValues, allowedValues, deniedValues, inheritFromParent, suggestedValue);
    }

    ListPolicy toPb() {
      return new ListPolicy()
          .setAllValues(allValues)
          .setAllowedValues(allowedValues)
          .setDeniedValues(deniedValues)
          .setInheritFromParent(inheritFromParent)
          .setSuggestedValue(suggestedValue);
    }

    static Policies fromPb(ListPolicy listPolicy) {
      return new Policies(
          listPolicy.getAllValues(),
          listPolicy.getAllowedValues(),
          listPolicy.getDeniedValues(),
          listPolicy.getInheritFromParent(),
          listPolicy.getSuggestedValue());
    }
  }

  /** Builder for {@code OrganizationPolicyInfo}. */
  static class Builder {
    private BoolPolicy boolPolicy;
    private String constraint;
    private String etag;
    private Policies policies;
    private RestoreDefault restoreDefault;
    private String updateTime;
    private Integer version;

    Builder() {}

    Builder(OrgPolicyInfo info) {
      this.boolPolicy = info.boolPolicy;
      this.constraint = info.constraint;
      this.etag = info.etag;
      this.policies = info.policies;
      this.restoreDefault = info.restoreDefault;
      this.updateTime = info.updateTime;
      this.version = info.version;
    }

    public Builder setBoolPolicy(BoolPolicy boolPolicy) {
      this.boolPolicy = boolPolicy;
      return this;
    }

    public Builder setConstraint(String constraint) {
      this.constraint = constraint;
      return this;
    }

    public Builder setEtag(String etag) {
      this.etag = etag;
      return this;
    }

    public Builder setListPolicy(Policies policies) {
      this.policies = policies;
      return this;
    }

    public Builder setRestoreDefault(RestoreDefault restoreDefault) {
      this.restoreDefault = restoreDefault;
      return this;
    }

    public Builder setUpdateTime(String updateTime) {
      this.updateTime = updateTime;
      return this;
    }

    public Builder setVersion(Integer version) {
      this.version = version;
      return this;
    }

    public OrgPolicyInfo build() {
      return new OrgPolicyInfo(this);
    }
  }

  OrgPolicyInfo(Builder builder) {
    this.boolPolicy = builder.boolPolicy;
    this.constraint = builder.constraint;
    this.etag = builder.etag;
    this.policies = builder.policies;
    this.restoreDefault = builder.restoreDefault;
    this.updateTime = builder.updateTime;
    this.version = builder.version;
  }

  /** Returns the boolean constraint to check whether the constraint is enforced or not. */
  public BoolPolicy getBoolPolicy() {
    return boolPolicy;
  }
  /** Returns the name of the Constraint. */
  public String getConstraint() {
    return constraint;
  }
  /** */
  public String getEtag() {
    return etag;
  }
  /** Return the policies. */
  public Policies getPolicies() {
    return policies;
  }
  /** Restores the default behavior of the constraint. */
  public RestoreDefault getRestoreDefault() {
    return restoreDefault;
  }
  /** Returns the updated timestamp of policy. */
  public String getUpdateTime() {
    return updateTime;
  }
  /** Returns the version of the Policy, Default version is 0. */
  public Integer getVersion() {
    return version;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    OrgPolicyInfo that = (OrgPolicyInfo) o;
    return Objects.equals(boolPolicy, that.boolPolicy)
        && Objects.equals(constraint, that.constraint)
        && Objects.equals(etag, that.etag)
        && Objects.equals(policies, that.policies)
        && Objects.equals(restoreDefault, that.restoreDefault)
        && Objects.equals(updateTime, that.updateTime)
        && Objects.equals(version, that.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        boolPolicy, constraint, etag, policies, restoreDefault, updateTime, version);
  }

  /** Returns a builder for the {@link OrgPolicyInfo} object. */
  public static Builder newBuilder() {
    return new Builder();
  }

  /** Returns a builder for the {@link OrgPolicyInfo} object. */
  public Builder toBuilder() {
    return new Builder(this);
  }

  OrgPolicy toPb() {
    OrgPolicy orgPolicyPb = new OrgPolicy();
    if (boolPolicy != null) {
      orgPolicyPb.setBooleanPolicy(boolPolicy.toPb());
    }
    orgPolicyPb.setConstraint(constraint);
    if (policies != null) {
      orgPolicyPb.setListPolicy(policies.toPb());
    }
    orgPolicyPb.setRestoreDefault(restoreDefault);
    orgPolicyPb.setEtag(etag);
    orgPolicyPb.setUpdateTime(updateTime);
    orgPolicyPb.setVersion(version);
    return orgPolicyPb;
  }

  static OrgPolicyInfo fromPb(OrgPolicy orgPolicyPb) {
    Builder builder = newBuilder();
    if (orgPolicyPb.getBooleanPolicy() != null) {
      builder.setBoolPolicy(BoolPolicy.fromPb(orgPolicyPb.getBooleanPolicy()));
    }
    if (orgPolicyPb.getConstraint() != null) {
      builder.setConstraint(orgPolicyPb.getConstraint());
    }
    if (orgPolicyPb.getListPolicy() != null) {
      builder.setListPolicy(Policies.fromPb(orgPolicyPb.getListPolicy()));
    }
    if (orgPolicyPb.getRestoreDefault() != null) {
      builder.setRestoreDefault(orgPolicyPb.getRestoreDefault());
    }
    if (orgPolicyPb.getEtag() != null) {
      builder.setEtag(orgPolicyPb.getEtag());
    }
    if (orgPolicyPb.getUpdateTime() != null) {
      builder.setUpdateTime(orgPolicyPb.getUpdateTime());
    }
    if (orgPolicyPb.getVersion() != null) {
      builder.setVersion(orgPolicyPb.getVersion());
    }
    return builder.build();
  }
}
