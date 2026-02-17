package com.codeops.registry.entity.enums;

/**
 * Classifies the type of configuration template stored for a service.
 */
public enum ConfigTemplateType {
    DOCKER_COMPOSE,
    APPLICATION_YML,
    APPLICATION_PROPERTIES,
    ENV_FILE,
    TERRAFORM_MODULE,
    CLAUDE_CODE_HEADER,
    CONVENTIONS_MD,
    NGINX_CONF,
    GITHUB_ACTIONS,
    DOCKERFILE,
    MAKEFILE,
    README_SECTION
}
