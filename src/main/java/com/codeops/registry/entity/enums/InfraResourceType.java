package com.codeops.registry.entity.enums;

/**
 * Classifies the type of cloud or infrastructure resource tracked by the Registry.
 */
public enum InfraResourceType {
    S3_BUCKET,
    SQS_QUEUE,
    SNS_TOPIC,
    CLOUDWATCH_LOG_GROUP,
    IAM_ROLE,
    SECRETS_MANAGER_PATH,
    SSM_PARAMETER,
    RDS_INSTANCE,
    ELASTICACHE_CLUSTER,
    ECR_REPOSITORY,
    CLOUD_MAP_NAMESPACE,
    ROUTE53_RECORD,
    ACM_CERTIFICATE,
    ALB_TARGET_GROUP,
    ECS_SERVICE,
    LAMBDA_FUNCTION,
    DYNAMODB_TABLE,
    DOCKER_NETWORK,
    DOCKER_VOLUME,
    OTHER
}
