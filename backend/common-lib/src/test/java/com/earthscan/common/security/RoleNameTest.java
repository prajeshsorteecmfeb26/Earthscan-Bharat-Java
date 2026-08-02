package com.earthscan.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("RoleName")
class RoleNameTest {

    @ParameterizedTest(name = "\"{0}\" resolves to {1}")
    @CsvSource({
            "Farmer,FARMER",
            "Land Buyer,LAND_BUYER",
            "Agriculture Expert,AGRICULTURE_EXPERT",
            "Admin,ADMIN"
    })
    @DisplayName("accepts the display names the React client sends")
    void resolvesDisplayNames(String input, RoleName expected) {
        assertThat(RoleName.from(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\" resolves to LAND_BUYER")
    @ValueSource(strings = {
            "LAND_BUYER", "land_buyer", "Land Buyer", "land buyer",
            "LAND-BUYER", "  Land Buyer  ", "lAnD bUyEr"
    })
    @DisplayName("is tolerant of case, padding, hyphens and underscores")
    void isTolerantOfFormatting(String input) {
        assertThat(RoleName.from(input)).isEqualTo(RoleName.LAND_BUYER);
    }

    @ParameterizedTest
    @EnumSource(RoleName.class)
    @DisplayName("round-trips every constant through its own display name")
    void roundTripsEveryConstant(RoleName role) {
        assertThat(RoleName.from(role.getDisplayName())).isEqualTo(role);
        assertThat(RoleName.from(role.name())).isEqualTo(role);
    }

    @ParameterizedTest
    @EnumSource(RoleName.class)
    @DisplayName("prefixes authorities with ROLE_ so hasRole() matches")
    void buildsSpringAuthority(RoleName role) {
        // hasRole('ADMIN') in @PreAuthorize looks for the authority ROLE_ADMIN; getting this prefix
        // wrong is a silent authorisation bypass, so it is asserted explicitly.
        assertThat(role.getAuthority()).isEqualTo("ROLE_" + role.name());
    }

    @Test
    @DisplayName("rejects an unknown role")
    void rejectsUnknownRole() {
        assertThatThrownBy(() -> RoleName.from("Supreme Overlord"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown role");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("rejects blank input")
    void rejectsBlank(String input) {
        assertThatThrownBy(() -> RoleName.from(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    @DisplayName("rejects null input")
    void rejectsNull() {
        assertThatThrownBy(() -> RoleName.from(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
