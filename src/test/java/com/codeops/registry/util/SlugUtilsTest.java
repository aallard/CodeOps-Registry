package com.codeops.registry.util;

import com.codeops.registry.config.AppConstants;
import com.codeops.registry.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SlugUtils}.
 */
class SlugUtilsTest {

    @Test
    void generateSlug_simpleCase() {
        assertThat(SlugUtils.generateSlug("CodeOps Server")).isEqualTo("codeops-server");
    }

    @Test
    void generateSlug_specialCharacters() {
        assertThat(SlugUtils.generateSlug("My App (v2.0)")).isEqualTo("my-app-v20");
    }

    @Test
    void generateSlug_underscores() {
        assertThat(SlugUtils.generateSlug("my_cool_app")).isEqualTo("my-cool-app");
    }

    @Test
    void generateSlug_multipleSpaces() {
        assertThat(SlugUtils.generateSlug("too   many   spaces")).isEqualTo("too-many-spaces");
    }

    @Test
    void generateSlug_leadingTrailingHyphens() {
        assertThat(SlugUtils.generateSlug("---test---")).isEqualTo("test");
    }

    @Test
    void generateSlug_unicodeStripped() {
        assertThat(SlugUtils.generateSlug("café app")).isEqualTo("caf-app");
    }

    @Test
    void generateSlug_emptyResult_throwsValidation() {
        assertThatThrownBy(() -> SlugUtils.generateSlug("!!!"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void generateSlug_tooShort_throwsValidation() {
        assertThatThrownBy(() -> SlugUtils.generateSlug("!a"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void generateSlug_nullInput_throwsValidation() {
        assertThatThrownBy(() -> SlugUtils.generateSlug(null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void generateSlug_maxLength_truncates() {
        String longName = "a".repeat(100);
        String slug = SlugUtils.generateSlug(longName);
        assertThat(slug.length()).isLessThanOrEqualTo(AppConstants.SLUG_MAX_LENGTH);
    }

    @Test
    void makeUnique_noConflict_returnsBase() {
        String result = SlugUtils.makeUnique("my-app", s -> false);
        assertThat(result).isEqualTo("my-app");
    }

    @Test
    void makeUnique_oneConflict_appendsSuffix() {
        String result = SlugUtils.makeUnique("my-app", s -> s.equals("my-app"));
        assertThat(result).isEqualTo("my-app-2");
    }

    @Test
    void makeUnique_multipleConflicts_incrementsSuffix() {
        String result = SlugUtils.makeUnique("my-app", s -> s.equals("my-app") || s.equals("my-app-2"));
        assertThat(result).isEqualTo("my-app-3");
    }

    @Test
    void validateSlug_valid_noException() {
        SlugUtils.validateSlug("my-valid-slug");
    }

    @Test
    void validateSlug_invalid_throwsValidation() {
        assertThatThrownBy(() -> SlugUtils.validateSlug("INVALID SLUG!"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateSlug_tooShort_throwsValidation() {
        assertThatThrownBy(() -> SlugUtils.validateSlug("a"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("at least");
    }

    @Test
    void validateSlug_blank_throwsValidation() {
        assertThatThrownBy(() -> SlugUtils.validateSlug(""))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("blank");
    }
}
