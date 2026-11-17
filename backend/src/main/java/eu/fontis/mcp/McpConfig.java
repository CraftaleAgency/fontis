package eu.fontis.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Exposes {@link EuLawTools} on the MCP endpoint ({@code /mcp}, Streamable HTTP; see application.yaml). */
@Configuration(proxyBeanMethods = false)
class McpConfig {

	@Bean
	ToolCallbackProvider euLawToolCallbacks(EuLawTools tools) {
		return MethodToolCallbackProvider.builder().toolObjects(tools).build();
	}

}
