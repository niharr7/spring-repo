package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
public class EchoController {

    @Value("${openai.api.key}")
    private String apiKey;
    @PostMapping("/echo")
    public String echo(@RequestBody String input) {
        return "Received: " + input;
    }

    @GetMapping("/echo")
    public String echoGet(@RequestParam(defaultValue = "World") String input) {
        return "Received: " + input;
    }
    
    @PostMapping("/openai")
    public String callOpenAI(@RequestBody String prompt) throws Exception {
        // Replace with your actual OpenAI API key or use an environment variable
        
        if (apiKey == null) {
            return "OpenAI API key not set in environment variable OPENAI_API_KEY";
        }
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        String requestBody = "{"
            + "\"model\": \"gpt-3.5-turbo\","
            + "\"messages\": [{\"role\": \"user\", \"content\": " 
            + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(prompt)
            + "}]"
            + "}";
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create("https://api.openai.com/v1/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}
