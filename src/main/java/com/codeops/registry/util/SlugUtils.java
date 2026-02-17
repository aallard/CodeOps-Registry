package com.codeops.registry.util;

import com.codeops.registry.config.AppConstants;
import com.codeops.registry.exception.ValidationException;

import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Utility for generating URL-safe slug identifiers from display names.
 *
 * <p>Slugs are lowercase, hyphenated, stripped of special characters.
 * Example: "CodeOps Server" → "codeops-server"</p>
 */
public final class SlugUtils {

    private static final Pattern SLUG_REGEX = Pattern.compile(AppConstants.SLUG_PATTERN);

    private SlugUtils() {}

    /**
     * Generates a slug from a display name.
     *
     * <ul>
     *   <li>Lowercase</li>
     *   <li>Replace spaces and underscores with hyphens</li>
     *   <li>Strip all characters except a-z, 0-9, hyphen</li>
     *   <li>Collapse consecutive hyphens</li>
     *   <li>Trim leading/trailing hyphens</li>
     *   <li>Enforce min length {@value AppConstants#SLUG_MIN_LENGTH},
     *       max length {@value AppConstants#SLUG_MAX_LENGTH}</li>
     * </ul>
     *
     * @param name the display name
     * @return the generated slug
     * @throws ValidationException if the result is empty or too short
     */
    public static String generateSlug(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Name must not be blank for slug generation");
        }

        String slug = name.toLowerCase()
                .replace(' ', '-')
                .replace('_', '-')
                .replaceAll("[^a-z0-9-]", "")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");

        if (slug.length() > AppConstants.SLUG_MAX_LENGTH) {
            slug = slug.substring(0, AppConstants.SLUG_MAX_LENGTH);
            slug = slug.replaceAll("-+$", "");
        }

        if (slug.length() < AppConstants.SLUG_MIN_LENGTH) {
            throw new ValidationException(
                    "Generated slug '" + slug + "' is too short (min " + AppConstants.SLUG_MIN_LENGTH + " characters)");
        }

        return slug;
    }

    /**
     * Makes a slug unique by appending -2, -3, etc. if the base slug is taken.
     *
     * @param baseSlug    the initial slug
     * @param existsCheck predicate that returns true if the slug already exists
     * @return a unique slug
     */
    public static String makeUnique(String baseSlug, Predicate<String> existsCheck) {
        if (!existsCheck.test(baseSlug)) {
            return baseSlug;
        }
        int suffix = 2;
        while (true) {
            String candidate = baseSlug + "-" + suffix;
            if (!existsCheck.test(candidate)) {
                return candidate;
            }
            suffix++;
        }
    }

    /**
     * Validates that a slug matches the allowed pattern.
     *
     * @param slug the slug to validate
     * @throws ValidationException if invalid
     */
    public static void validateSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new ValidationException("Slug must not be blank");
        }
        if (slug.length() < AppConstants.SLUG_MIN_LENGTH) {
            throw new ValidationException("Slug must be at least " + AppConstants.SLUG_MIN_LENGTH + " characters");
        }
        if (slug.length() > AppConstants.SLUG_MAX_LENGTH) {
            throw new ValidationException("Slug must be at most " + AppConstants.SLUG_MAX_LENGTH + " characters");
        }
        if (!SLUG_REGEX.matcher(slug).matches()) {
            throw new ValidationException(
                    "Slug '" + slug + "' is invalid. Must contain only lowercase letters, numbers, and hyphens, "
                            + "and must start and end with a letter or number.");
        }
    }
}
