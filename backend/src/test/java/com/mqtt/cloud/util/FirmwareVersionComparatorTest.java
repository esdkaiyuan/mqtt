package com.mqtt.cloud.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FirmwareVersionComparator} 单元测试（T-54）。
 */
class FirmwareVersionComparatorTest {

    @Test
    void numericSegments_should_compareNumerically() {
        assertThat(FirmwareVersionComparator.compare("1.2.10", "1.2.9")).isPositive();
        assertThat(FirmwareVersionComparator.compare("1.2.9", "1.2.10")).isNegative();
        assertThat(FirmwareVersionComparator.compare("2.0.0", "1.9.9")).isPositive();
    }

    @Test
    void prefixAndMetadata_should_beIgnored() {
        assertThat(FirmwareVersionComparator.compare("v2.0.0", "2.0.0")).isZero();
        assertThat(FirmwareVersionComparator.compare("2.0.0+build7", "2.0.0")).isZero();
    }

    @Test
    void missingNumericSegments_should_beTreatedAsZero() {
        assertThat(FirmwareVersionComparator.compare("1.0", "1.0.0")).isZero();
        assertThat(FirmwareVersionComparator.compare("1.0.1", "1.0")).isPositive();
    }

    @Test
    void preReleaseSuffix_should_beLowerThanRelease() {
        assertThat(FirmwareVersionComparator.compare("1.0.0-beta", "1.0.0")).isNegative();
        assertThat(FirmwareVersionComparator.compare("1.0.0", "1.0.0-beta")).isPositive();
        assertThat(FirmwareVersionComparator.compare("1.0.0-rc1", "1.0.0-rc2")).isNegative();
        assertThat(FirmwareVersionComparator.compare("1.0.0-beta.2", "1.0.0-beta.10")).isNegative();
    }

    @Test
    void isDowngrade_should_returnFalseWhenEitherSideUnknown() {
        assertThat(FirmwareVersionComparator.isDowngrade("1.0.0", null)).isFalse();
        assertThat(FirmwareVersionComparator.isDowngrade(null, "1.0.0")).isFalse();
        assertThat(FirmwareVersionComparator.isDowngrade(" ", "1.0.0")).isFalse();
        assertThat(FirmwareVersionComparator.isDowngrade("0.9.0", "1.0.0")).isTrue();
    }

    @Test
    void equalVersions_should_notBeDowngrade() {
        assertThat(FirmwareVersionComparator.isDowngrade("1.0.0", "1.0")).isFalse();
        assertThat(FirmwareVersionComparator.isDowngrade("v1.0.0", "1.0.0")).isFalse();
    }
}