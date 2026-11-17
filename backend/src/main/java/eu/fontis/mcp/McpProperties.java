package eu.fontis.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the MCP server (the server itself is configured under {@code spring.ai.mcp.server}).
 *
 * @param charsPerResult text returned for each search result, from several chunks of the unit
 * @param rateLimitPerMinute MCP requests per client per minute (0 = no limit)
 */
@ConfigurationProperties("fontis.mcp")
public record McpProperties(int charsPerResult, int rateLimitPerMinute) {
}
