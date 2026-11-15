package dev.mickablondo.cartable.sheet.internal.configuration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SheetConfiguration {

	@Bean
	ChatClient sheetChatClient(ChatClient.Builder builder) {
		return builder.build();
	}

}
